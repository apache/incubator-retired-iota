#!/usr/bin/env bash

set -e

##########################################################
# Set the relaase
RELEASE="xenial"

# Image configs
HOSTNAME="iota"
USERNAME="iota"
TZDATA="Europe/London"

#########################################################
# Directorys
TITLE="ubuntu"
VERSION="16.04"

BASEDIR=$(pwd)/image-build
BUILDDIR=${BASEDIR}/${TITLE}
MOUNTDIR=$BUILDDIR/mount
BASE_R=${BASEDIR}/base
DEVICE_R=${BUILDDIR}/pi2
DESKTOP_R=${BUILDDIR}/desktop
ARCH=$(uname -m)
export TZ=${TZDATA}

TARBALL="$(date +%Y-%m-%d)-iota-ubuntu-${VERSION}-armhf-rootfs.tar.bz2"
IMAGE="$(date +%Y-%m-%d)-iota-ubuntu-${VERSION}-armhf-raspberry-pi-2.img"
IMAGE_NAME="$(date +%Y-%m-%d)-iota-ubuntu-${VERSION}-armhf-raspberry-pi-2"
# Either 'ext4' or 'f2fs'
FS_TYPE="ext4"

# Either 4, 8 or 16
FS_SIZE=4

# Either 0 or 1.
# - 0 don't make generic rootfs tarball
# - 1 make a generic rootfs tarball
MAKE_TARBALL=0

########################################################
# bash colors
BASH_GREEN="\e[1;32m"
BASH_RED="\e[1;31m"
BASH_NORMAL="\e[0m"

printGreen() {
    echo -e "${BASH_GREEN}$1${BASH_NORMAL}"
}

printRed() {
    echo -e "${BASH_RED}$1${BASH_NORMAL}"
}

#########################################################
# check root
if [ ${UID} -ne 0 ]; then
    printRed "Please start the script as root."
    exit 1
fi


#########################################################
# Mount host system
function mount_system() {
    printGreen "Mount system..."
    # In case this is a re-run move the cofi preload out of the way
    if [ -e $R/etc/ld.so.preload ]; then
        mv -v $R/etc/ld.so.preload $R/etc/ld.so.preload.disable
    fi
    mount -t proc none $R/proc
    mount -t sysfs none $R/sys
    mount -o bind /dev $R/dev
    mount -o bind /dev/pts $R/dev/pts
    echo "nameserver 8.8.8.8" > $R/etc/resolv.conf
}

# Unmount host system
function umount_system() {
    printGreen "Umount system..."
    umount -l $R/sys
    umount -l $R/proc
    umount -l $R/dev/pts
    umount -l $R/dev
    echo "" > $R/etc/resolv.conf
}

function sync_to() {
    printGreen "Sync ${1}..."
    local TARGET="${1}"
    if [ ! -d "${TARGET}" ]; then
        mkdir -p "${TARGET}"
    fi
    rsync -a --progress --delete ${R}/ ${TARGET}/
}

# Base debootstrap
function bootstrap() {
    printGreen "Bootstrap..."
    # Required tools
    apt-get -y install binfmt-support debootstrap f2fs-tools \
    qemu-user-static rsync ubuntu-keyring wget whois

    # Use the same base system for all flavours.
    if [ ! -f "${R}/tmp/.bootstrap" ]; then
        if [ "${ARCH}" == "armv7l" ]; then
            debootstrap --verbose $RELEASE $R http://ports.ubuntu.com/
        else
            qemu-debootstrap --verbose --arch=armhf $RELEASE $R http://ports.ubuntu.com/
        fi
        touch "$R/tmp/.bootstrap"
    fi
}

function generate_locale() {
    printGreen "Generate locale..."
    for LOCALE in $(chroot $R locale | cut -d'=' -f2 | grep -v : | sed 's/"//g' | uniq); do
        if [ -n "${LOCALE}" ]; then
            chroot $R locale-gen $LOCALE
        fi
    done
}

function configure_timezone() {
    printGreen "Setup timezone ${TZDATA}..."
    # Set time zone
    echo ${TZDATA} > $R/etc/timezone
    chroot $R dpkg-reconfigure -f noninteractive tzdata
}

# Set up initial sources.list
function apt_sources() {
    printGreen "Add source lists..."
    cat <<EOM >$R/etc/apt/sources.list
deb http://ports.ubuntu.com/ ${RELEASE} main restricted universe multiverse
deb-src http://ports.ubuntu.com/ ${RELEASE} main restricted universe multiverse

deb http://ports.ubuntu.com/ ${RELEASE}-updates main restricted universe multiverse
deb-src http://ports.ubuntu.com/ ${RELEASE}-updates main restricted universe multiverse

deb http://ports.ubuntu.com/ ${RELEASE}-security main restricted universe multiverse
deb-src http://ports.ubuntu.com/ ${RELEASE}-security main restricted universe multiverse

deb http://ports.ubuntu.com/ ${RELEASE}-backports main restricted universe multiverse
deb-src http://ports.ubuntu.com/ ${RELEASE}-backports main restricted universe multiverse
EOM

    cat <<EOM >$R/etc/apt/apt.conf.d/50raspi
# Never use pdiffs, current implementation is very slow on low-powered devices
Acquire::PDiffs "0";
EOM

}

function apt_upgrade() {
    printGreen "Upgrade..."
    chroot $R apt-get -f -y install
    chroot $R dpkg --configure -a
    chroot $R apt-get update
    chroot $R apt-get -y -u dist-upgrade
}

function apt_clean() {
    printGreen "Clean packages..."
    chroot $R apt-get -y autoremove
    chroot $R apt-get clean
}

# Install Ubuntu
function install_ubuntu() {
    printGreen "Install ubuntu..."
    chroot $R apt-get -y install f2fs-tools software-properties-common
    if [ ! -f "${R}/tmp/.ubuntu" ]; then
        chroot $R apt-get -y install ubuntu-standard
        touch "${R}/tmp/.ubuntu"
    fi
}

function create_groups() {
    printGreen "Create groups..."
    chroot $R groupadd -f --system gpio
    chroot $R groupadd -f --system i2c
    chroot $R groupadd -f --system input
    chroot $R groupadd -f --system spi

    # Create adduser hook
    cat <<'EOM' >$R/usr/local/sbin/adduser.local
#!/bin/sh
# This script is executed as the final step when calling `adduser`
# USAGE:
#   adduser.local USER UID GID HOME

# Add user to the Raspberry Pi specific groups
usermod -a -G adm,gpio,i2c,input,spi,video $1
EOM
    chmod +x $R/usr/local/sbin/adduser.local
}

# Create default user
function create_user() {
    printGreen "Create user..."
    local DATE=$(date +%m%H%M%S)
    local PASSWD=$(mkpasswd -m sha-512 ${USERNAME} ${DATE})

    chroot $R adduser --gecos "iota user" --add_extra_groups --disabled-password ${USERNAME}
    chroot $R usermod -a -G sudo -p ${PASSWD} ${USERNAME}
}


function configure_ssh() {
    printGreen "Configure ssh..."
    chroot $R apt-get -y install openssh-server
    cat > $R/etc/systemd/system/sshdgenkeys.service << EOF
[Unit]
Description=SSH key generation on first startup
Before=ssh.service
ConditionPathExists=|!/etc/ssh/ssh_host_key
ConditionPathExists=|!/etc/ssh/ssh_host_key.pub
ConditionPathExists=|!/etc/ssh/ssh_host_rsa_key
ConditionPathExists=|!/etc/ssh/ssh_host_rsa_key.pub
ConditionPathExists=|!/etc/ssh/ssh_host_dsa_key
ConditionPathExists=|!/etc/ssh/ssh_host_dsa_key.pub
ConditionPathExists=|!/etc/ssh/ssh_host_ecdsa_key
ConditionPathExists=|!/etc/ssh/ssh_host_ecdsa_key.pub
ConditionPathExists=|!/etc/ssh/ssh_host_ed25519_key
ConditionPathExists=|!/etc/ssh/ssh_host_ed25519_key.pub

[Service]
ExecStart=/usr/bin/ssh-keygen -A
Type=oneshot
RemainAfterExit=yes

[Install]
WantedBy=ssh.service
EOF

    mkdir -p $R/etc/systemd/system/ssh.service.wants
    chroot $R ln -s /etc/systemd/system/sshdgenkeys.service /etc/systemd/system/ssh.service.wants
}

function configure_network() {
    printGreen "Set hostename ${HOSTNAME}..."

    # Set up hosts
    echo ${HOSTNAME} >$R/etc/hostname
    cat <<EOM >$R/etc/hosts
127.0.0.1       localhost
::1             localhost ip6-localhost ip6-loopback
ff02::1         ip6-allnodes
ff02::2         ip6-allrouters

127.0.1.1       ${HOSTNAME}
EOM

    # Set up interfaces
    printGreen "Configure network..."
    cat <<EOM >$R/etc/network/interfaces
# interfaces(5) file used by ifup(8) and ifdown(8)
# Include files from /etc/network/interfaces.d:
source-directory /etc/network/interfaces.d

# The loopback network interface
auto lo
iface lo inet loopback

auto eth0
iface eth0 inet dhcp
EOM

}

function configure_hardware() {
    # Ported
    # http://archive.raspberrypi.org/debian/pool/main/r/raspberrypi-firmware/raspberrypi-firmware_1.20151118-1.dsc # Foundation's Kernel
    # https://launchpad.net/~fo0bar/+archive/ubuntu/rpi2-nightly/+files/xserver-xorg-video-fbturbo_0%7Egit.20151007.f9a6ed7-0%7Enightly.dsc

    # Kernel and Firmware - Pending
    # https://twolife.be/raspbian/pool/main/bcm-videocore-pkgconfig/bcm-videocore-pkgconfig_1.dsc
    # https://twolife.be/raspbian/pool/main/linux/linux_4.1.8-1+rpi1.dsc
    # http://archive.raspberrypi.org/debian/pool/main/r/raspi-copies-and-fills/raspi-copies-and-fills_0.5-1.dsc # FTBFS in a PPA

    printGreen "Configure hardware..."
    local FS="${1}"
    if [ "${FS}" != "ext4" ] && [ "${FS}" != 'f2fs' ]; then
        echo "ERROR! Unsupport filesystem requested. Exitting."
        exit 1
    fi

    # gdebi-core used for installing copies-and-fills and omxplayer
    chroot $R apt-get -y install gdebi-core
    local COFI="http://archive.raspberrypi.org/debian/pool/main/r/raspi-copies-and-fills/raspi-copies-and-fills_0.5-1_armhf.deb"

    # Install the RPi PPA
    chroot $R apt-add-repository -y ppa:ubuntu-pi-flavour-makers/ppa
    chroot $R apt-get update

    # Firmware Kernel installation
    printGreen "Install kernel and firmware..."
    chroot $R apt-get -y -f install libraspberrypi-bin libraspberrypi-dev \
    libraspberrypi-doc libraspberrypi0 raspberrypi-bootloader rpi-update
    chroot $R apt-get -y -f install linux-firmware #linux-firmware-nonfree
    chroot $R rpi-update

    # Add VideoCore libs to ld.so
    echo "/opt/vc/lib" > $R/etc/ld.so.conf.d/vmcs.conf

    # Hardware - Create a fake HW clock and add rng-tools
    chroot $R apt-get -y install fake-hwclock fbset i2c-tools rng-tools

    # Load sound module on boot and enable HW random number generator
    cat <<EOM >$R/etc/modules-load.d/rpi2.conf
snd_bcm2835
bcm2708_rng
EOM

    # Blacklist platform modules not applicable to the RPi2
    cat <<EOM >$R/etc/modprobe.d/blacklist-rpi2.conf
blacklist snd_soc_pcm512x_i2c
blacklist snd_soc_pcm512x
blacklist snd_soc_tas5713
blacklist snd_soc_wm8804
EOM

    # Disable TLP
    if [ -f $R/etc/default/tlp ]; then
        sed -i s'/TLP_ENABLE=1/TLP_ENABLE=0/' $R/etc/default/tlp
    fi

    # udev rules
    printf 'SUBSYSTEM=="vchiq", GROUP="video", MODE="0660"\n' > $R/etc/udev/rules.d/10-local-rpi.rules
    printf "SUBSYSTEM==\"gpio*\", PROGRAM=\"/bin/sh -c 'chown -R root:gpio /sys/class/gpio && chmod -R 770 /sys/class/gpio; chown -R root:gpio /sys/devices/virtual/gpio && chmod -R 770 /sys/devices/virtual/gpio'\"\n" > $R/etc/udev/rules.d/99-com.rules
    printf 'SUBSYSTEM=="input", GROUP="input", MODE="0660"\n' >> $R/etc/udev/rules.d/99-com.rules
    printf 'SUBSYSTEM=="i2c-dev", GROUP="i2c", MODE="0660"\n' >> $R/etc/udev/rules.d/99-com.rules
    printf 'SUBSYSTEM=="spidev", GROUP="spi", MODE="0660"\n' >> $R/etc/udev/rules.d/99-com.rules
    cat <<EOF > $R/etc/udev/rules.d/40-scratch.rules
ATTRS{idVendor}=="0694", ATTRS{idProduct}=="0003", SUBSYSTEMS=="usb", ACTION=="add", MODE="0666", GROUP="plugdev"
EOF

    # copies-and-fills
    printGreen "Copies-and-fills..."
    wget -c "${COFI}" -O $R/tmp/cofi.deb
    chroot $R gdebi -n /tmp/cofi.deb
    # Disabled cofi so it doesn't segfault when building via qemu-user-static
    mv -v $R/etc/ld.so.preload $R/etc/ld.so.preload.disable

    # Set up fstab
    cat <<EOM >$R/etc/fstab
proc            /proc           proc    defaults          0       0
/dev/mmcblk0p2  /               ${FS}   defaults,noatime  0       1
/dev/mmcblk0p1  /boot/          vfat    defaults          0       2
EOM

    # Set up firmware config
    printGreen "Set up firmware config..."
    wget -c https://raw.githubusercontent.com/Evilpaul/RPi-config/master/config.txt -O $R/boot/config.txt

    echo "net.ifnames=0 biosdevname=0 dwc_otg.lpm_enable=0 console=tty1 root=/dev/mmcblk0p2 rootfstype=${FS} elevator=deadline rootwait quiet splash" > $R/boot/cmdline.txt


    # Save the clock
    chroot $R fake-hwclock save
}

function install_software() {
    printGreen "Add ubuntu pi flavour PPA"
    # Install the RPi PPA
    chroot $R apt-add-repository -y ppa:ubuntu-pi-flavour-makers/ppa

    printGreen "Update..."
    chroot $R apt-get update

    printGreen "Install extra packages..."
    chroot $R apt-get -y install htop nano avahi-utils vim git ntp openjdk-8-jdk curl

    printGreen "Installing ZeroMQ..."
    chroot $R apt-get -y install libtool pkg-config build-essential autoconf automake
    wget -P $R https://download.libsodium.org/libsodium/releases/libsodium-1.0.3.tar.gz
    chroot $R tar xzf libsodium-1.0.3.tar.gz
    rm $R/libsodium-1.0.3.tar.gz
    chroot $R << EOF
cd libsodium-1.0.3/
./configure
make
make install
EOF
    rm -r $R/libsodium-1.0.3/
    wget -P $R http://download.zeromq.org/zeromq-4.1.3.tar.gz
    chroot $R << EOF
tar -zxf zeromq-4.1.3.tar.gz
cd zeromq-4.1.3/
./configure
make
make install
ldconfig
cd ..
rm -r zeromq-4.1.3/
rm zeromq-4.1.3.tar.gz
EOF

    printGreen "Installing MQTT..."
    chroot $R apt-get -y install mosquitto mosquitto-clients

    printGreen  "Installing Redis...."
    chroot $R apt-get -y install redis-server python-redis

    printGreen  "Installing Scala..."
    wget -P $R https://downloads.typesafe.com/scala/2.11.6/scala-2.11.6.tgz
    mkdir -p $R/usr/lib/scala
    chroot $R tar -xzf scala-2.11.6.tgz -C /usr/lib/scala
    rm $R/scala-2.11.6.tgz
    chroot $R ln -sf /usr/lib/scala/scala-2.11.6/bin/scala /bin/scala
    chroot $R ln -sf /usr/lib/scala/scala-2.11.6/bin/scalac /bin/scalac

    printGreen "Installing Spark..."
    wget -P $R http://apache.mirror.anlx.net/spark/spark-1.6.1/spark-1.6.1-bin-hadoop2.6.tgz
    chroot $R tar xzf spark-1.6.1-bin-hadoop2.6.tgz -C /opt
    rm $R/spark-1.6.1-bin-hadoop2.6.tgz
    cat <<EOF >> $R/etc/bash.bashrc
export PATH=\$PATH:/opt/spark-1.6.1-bin-hadoop2.6/bin
EOF

    printGreen "Installing StrongSwan..."
    chroot $R << EOF
curl -L -O https://raw.github.com/philplckthun/setup-strong-strongswan/master/setup.sh
./setup.sh
rm setup.sh
EOF



    printGreen "Install iota packages..."

}

function clean_up() {
    printGreen "Clean up..."
    rm -f $R/etc/apt/*.save || true
    rm -f $R/etc/apt/sources.list.d/*.save || true
    rm -f $R/etc/resolvconf/resolv.conf.d/original
    rm -f $R/run/*/*pid || true
    rm -f $R/run/*pid || true
    rm -f $R/run/cups/cups.sock || true
    rm -f $R/run/uuidd/request || true
    rm -f $R/etc/*-
    rm -rf $R/tmp/*
    rm -f $R/var/crash/*
    rm -f $R/var/lib/urandom/random-seed

    # Clean up old Raspberry Pi firmware and modules
    rm -f $R/boot/.firmware_revision || true
    rm -rf $R/boot.bak || true
    rm -rf $R/lib/modules/4.1.7* || true
    rm -rf $R/lib/modules.bak || true

    # Potentially sensitive.
    rm -f $R/root/.bash_history
    rm -f $R/root/.ssh/known_hosts

    # Machine-specific, so remove in case this system is going to be
    # cloned.  These will be regenerated on the first boot.
    rm -f $R/etc/udev/rules.d/70-persistent-cd.rules
    rm -f $R/etc/udev/rules.d/70-persistent-net.rules
    rm -f $R/etc/NetworkManager/system-connections/*
    [ -L $R/var/lib/dbus/machine-id ] || rm -f $R/var/lib/dbus/machine-id
    echo '' > $R/etc/machine-id

    # Enable cofi
    if [ -e $R/etc/ld.so.preload.disable ]; then
        mv -v $R/etc/ld.so.preload.disable $R/etc/ld.so.preload
    fi

    rm -rf $R/tmp/.bootstrap || true
    rm -rf $R/tmp/.minimal || true
    rm -rf $R/tmp/.standard || true
}

function make_raspi2_image() {
    printGreen "Create image..."
    # Build the image file
    local FS="${1}"
    local GB=${2}

    if [ "${FS}" != "ext4" ] && [ "${FS}" != 'f2fs' ]; then
        echo "ERROR! Unsupport filesystem requested. Exitting."
        exit 1
    fi

    if [ ${GB} -ne 4 ] && [ ${GB} -ne 8 ] && [ ${GB} -ne 16 ]; then
        echo "ERROR! Unsupport card image size requested. Exitting."
        exit 1
    fi

    if [ ${GB} -eq 4 ]; then
        SEEK=3750
        SIZE=7546880
        SIZE_LIMIT=3685
    elif [ ${GB} -eq 8 ]; then
        SEEK=7680
        SIZE=15728639
        SIZE_LIMIT=7615
    elif [ ${GB} -eq 16 ]; then
        SEEK=15360
        SIZE=31457278
        SIZE_LIMIT=15230
    fi

    # If a compress version exists, remove it.
    rm -f "${BASEDIR}/${IMAGE}.bz2" || true

    dd if=/dev/zero of="${BASEDIR}/${IMAGE}" bs=1M count=1
    dd if=/dev/zero of="${BASEDIR}/${IMAGE}" bs=1M count=0 seek=${SEEK}

    sfdisk -f "$BASEDIR/${IMAGE}" <<EOM
unit: sectors

1 : start=     2048, size=   131072, Id= c, bootable
2 : start=   133120, size=  ${SIZE}, Id=83
3 : start=        0, size=        0, Id= 0
4 : start=        0, size=        0, Id= 0
EOM

    BOOT_LOOP="$(losetup -o 1M --sizelimit 64M -f --show ${BASEDIR}/${IMAGE})"
    ROOT_LOOP="$(losetup -o 65M --sizelimit ${SIZE_LIMIT}M -f --show ${BASEDIR}/${IMAGE})"
    mkfs.vfat -n PI_BOOT -S 512 -s 16 -v "${BOOT_LOOP}"
    if [ "${FS}" == "ext4" ]; then
        mkfs.ext4 -L PI_ROOT -m 0 "${ROOT_LOOP}"
    else
        mkfs.f2fs -l PI_ROOT -o 1 "${ROOT_LOOP}"
    fi
    MOUNTDIR="${BUILDDIR}/mount"
    mkdir -p "${MOUNTDIR}"
    mount "${ROOT_LOOP}" "${MOUNTDIR}"
    mkdir -p "${MOUNTDIR}/boot"
    mount "${BOOT_LOOP}" "${MOUNTDIR}/boot"
    rsync -a --progress "$R/" "${MOUNTDIR}/"
    umount -l "${MOUNTDIR}/boot"
    umount -l "${MOUNTDIR}"
    losetup -d "${ROOT_LOOP}"
    losetup -d "${BOOT_LOOP}"
}

function make_tarball() {
    if [ ${MAKE_TARBALL} -eq 1 ]; then
        printGreen "Create tarball..."
        rm -f "${BASEDIR}/${TARBALL}" || true
        tar -cSf "${BASEDIR}/${TARBALL}" $R
    fi
}

function stage_01_base() {
    printGreen "================================================"
    printGreen "Stage 1 - Base system"
    printGreen "================================================"

    R="${BASE_R}"
    bootstrap
    mount_system
    generate_locale
    configure_timezone
    apt_sources
    apt_upgrade
    install_ubuntu
    apt_clean
    umount_system
    sync_to ${DESKTOP_R}
}

function stage_02_desktop() {
    printGreen "================================================"
    printGreen "Stage 2 - Desktop"
    printGreen "================================================"

    R="${DESKTOP_R}"
    mount_system

    create_groups
    create_user
    configure_ssh
    configure_network
    apt_upgrade
    apt_clean
    umount_system
    clean_up
    sync_to ${DEVICE_R}
    make_tarball
}

function stage_03_raspi2() {
    printGreen "================================================"
    printGreen "Stage 3 - Create image"
    printGreen "================================================"

    R="${DEVICE_R}"
    mount_system
    configure_hardware ${FS_TYPE}
    install_software
    apt_upgrade
    apt_clean
    clean_up
    umount_system
    make_raspi2_image ${FS_TYPE} ${FS_SIZE}
}

#########################################################
# Start
startTime=$(date +%s)


#########################################################
# Main
stage_01_base
stage_02_desktop
stage_03_raspi2

printGreen "Compress files ${IMAGE} ..."
cd ${BASEDIR}/
zip ${IMAGE_NAME}.zip ${IMAGE}

#########################################################
# calculate process time
endTime=$(date +%s)
dt=$((endTime - startTime))
ds=$((dt % 60))
dm=$(((dt / 60) % 60))
dh=$((dt / 3600))

echo -e "${BASH_GREEN}"
echo -e "-------------------------------------------------------"
printf '\tTotal time: %02d:%02d:%02d\n' ${dh} ${dm} ${ds}
echo -e "-------------------------------------------------------"
echo -e "${BASH_NORMAL}"
