#!/bin/bash


#helper functions

announcement(){
  echo "$(tput setaf 3)"
  echo "================================================="
  for arg in "$@"
  do
      echo "$arg"
  done
  echo "================================================="
  echo "$(tput sgr0)"
 
}

alert(){
  echo "$(tput setaf 1)"
  echo "================================================="
  for arg in "$@"
  do
      echo "$arg"
  done
  echo "================================================="
  echo "$(tput sgr0)"
 
}

echo_red() {
    echo "$(tput setaf 1) $1 $(tput sgr0)"
}

echo_green() {
    echo "$(tput setaf 2) $1 $(tput sgr0)"
}


fail () {
    echo -e "Error encountered during install"
    echo -n "Cleaning up..."
    umount sdcard/proc
    umount sdcard/sys
    umount sdcard/dev/pts
    fuser -av sdcard
    fuser -kv sdcard
    umount sdcard/boot
    fuser -k sdcard
    umount sdcard
    kpartx -dv $image_name
    rm -rf sdcard
    rm $image_name
    echo "[done]"
    exit 1
}

# Apache iota Raspberry Pi Img Builder

# BEGIN Configuration
image_name=iota_installer_rpi_v0_1a.img
image_size=1 
release=jessie
http=http://mirrordirector.raspbian.org/raspbian	
scala_url=https://downloads.typesafe.com/scala/2.11.6/scala-2.11.6.tgz
spark_url=http://apache.mirror.anlx.net/spark/spark-1.6.1/spark-1.6.1-bin-hadoop2.6.tgz
spark_redis_url=https://github.com/RedisLabs/spark-redis.git
libsodium_url=https://download.libsodium.org/libsodium/releases/libsodium-1.0.3.tar.gz
zeromq_url=http://download.zeromq.org/zeromq-4.1.3.tar.gz
mqtt_key=http://repo.mosquitto.org/debian/mosquitto-repo.gpg.key
mqtt_url=http://repo.mosquitto.org/debian/mosquitto-$(awk -F"[)(]+" '/VERSION=/ {print $2}' /etc/os-release).list
bootloader=kernel 		
root_file_system=ext4
mkfs_ext4_options="^has_journal -E stride=0,stripe-width=128 -b 4096"
keyboard_layout=us
timeserver=time.nist.gov

# stuff to have cdebootstrap to install or exclude, just use the ',' format
include=dphys-swapfile
exclude=
not_installed="$(tput setaf 1)not installed.$(tput sgr0)"
installed="$(tput setaf 2)installed.$(tput sgr0)"

# use 'misc_stuff' to add routine packages with apt-get after cdebootstrap install complete
misc_stuff="libraspberrypi-bin libraspberrypi-dev libraspberrypi-doc dbus fake-hwclock psmisc ssh ntp vim"
# END Configurization

# Hardware check
RPI1_HARDWARE="BCM2708"
RPI2_HARDWARE="BCM2709"
rpi_hardware=$(grep Hardware /proc/cpuinfo | cut -d " " -f 2)
rpi_hardware_version="UNKNOWN !!"

if [ "$rpi_hardware" = "$RPI1_HARDWARE" ] ; then
    rpi_version="rpi"
elif [ "$rpi_hardware" = "$RPI2_HARDWARE" ] ; then
    rpi_version="rpi2"
else
    alert "    Alert: No Raspberry Pi hardware detected!!" \
          " Value of rpi_hardware variable: '${rpi_hardware}'"
    exit 1
fi

echo ""
announcement "Raspberry Pi Iota Installer" \
             "Revision 0.0.1a" \
             "Built on May 1, 2016" \
             "Running on Raspberry Pi version ${rpi_hardware}" \
             "http://incubator.apache.org/projects/iota.html"

# Check to see if installer is being run as root
start_time=$(date)
echo -en "hecking for root to run the installer ... "
if [ ${EUID} -ne 0 ]; then
   echo_red "[Failed]"
   alert "     Alert: iota installer must run as root!!" \
         " Try 'sudo ./iota_rpi_installer_0.1.sh' as a user"
   exit 1
else
    echo_green "[Done]"
fi


# Check for base programs that are needed

announcement "Checking for necessary base programs to be installed..."
APS=""

echo -n "Checking for fuser ... "
if [ `which fuser` ]; then
    echo_green "installed"
else
    echo_red "not installed"
    APS+="psmisc "
fi

echo -n "Checking for ioctl ... "
if [ -f /usr/include/linux/ioctl.h ]; then
    echo_green "installed"
else
    echo_red "not installed"
    APS+="libc6-dev "
fi

echo -n "Checking for kpartx ... "
if [ `which kpartx` ]; then
    echo_green "installed"
else
    echo_red "not installed"
    APS+="kpartx "
fi

echo -n "Checking for partprobe ... "
if [ `which partprobe` ]; then
    echo_green "installed"
else
    echo_red "not installed"
    APS+="parted "
fi

echo -n "Checking for dosfstools ... "
if [ `which fsck.vfat` ]; then
    echo_green "installed"
else
    echo_red "not installed"
    APS+="dosfstools "
fi

echo -n "Checking for cdebootstrap ... "
if [ `which cdebootstrap` ]; then
    echo_green "installed"
else
    echo_red "not installed"
    APS+="cdebootstrap "
fi
echo -n "Checking for curl ... "
if [ `which curl` ]; then
    echo_green "installed"
else
    echo_red "not installed"
    APS+="curl "
fi
echo -n "Checking for git ... "
if [ `which git` ]; then
    echo_green "installed"
else
    echo_red "not installed"
    APS+="git "
fi
echo -n "Checking for vim ... "
if [ `which vim` ]; then
    echo_green "installed"
else
    echo_red "not installed"
    APS+="vim "
fi

if [ "$APS" != "" ]; then
    announcement "iota installer needs the following base applications installed: " "$APS"
    echo -n "Would you like me to get $APS for you ?? [Y/n]: "
	read resp
	if [ "$resp" = "" ] || [ "$resp" = "y" ] || [ "$resp" = "yes" ]; then
        apt-get update
        apt-get -y install $APS
        announcement "All base applications are now installed."
    else
        alert "Necessary Applications not installed" \
             "Exiting .. :(~"
        exit 1
    fi
else
    announcement "No further base applications needed .. :)~"
fi  

 # install questions
announcement "Starting the network configuration ..."

echo -n "Please enter the hostname: [iota] "
read resp
if [ "$resp" = "" ]; then
    hostname=iota
else
    hostname="$resp"
fi

echo -n "Please enter the domain name: [] "
read resp
if [ "$resp" = "" ]; then
    domain_name=
else
    domain_name="$resp"
fi

echo -n "Please enter the root password: [root]"
read resp
if [ "$resp" = "" ]; then
    root_password=root
else
    root_password="$resp"
fi

echo -n "Please enter the wifi name: [Enter if you don't want to configure wifi now]"
read resp_wifi_name
if [ "$resp_wifi_name" != "" ]; then
    echo -n "Please enter the wifi password: "
    read resp_wifi_passwd
fi

echo -n "Enabe DHCP for networking [Y/n]? : "
read resp
if [ "$resp" = "" ] || [ "$resp" = "y" ] || [ "$resp" = "yes" ]; then
    dhcp="yes"
else
    dhcp="no"
    echo -n "Please enter IP address :"
    read resp
    address="$resp"
    echo -n "Please enter IP netmask :"
    read resp
    netmask="$resp"
    echo -n "Please enter IP broadcast :"
    read resp
    broadcast="$resp"
    echo -n "Please enter IP gateway :"
    read resp
    gateway="$resp"
fi

# Other Installs (Spark, Redis, SBT, Scala, ZeroMQ, MQTT

echo "====================================================="
echo -n "Would you like me to get Redis for you ?? [Y/n]: "
read resp_redis
echo -n "Would you like me to get Scala for you ?? [Y/n]: "
read resp_scala
echo -n "Would you like me to get Spark for you ?? [Y/n]: "
read resp_spark
echo -n "Would you like me to get ZeroMQ for you ?? [Y/n]: "
read resp_zeromq
echo -n "Would you like me to get MQTT for you ?? [Y/n]: "
read resp_mqtt
echo -n "Would you like me to get Cockroachdb for you ?? [Y/n]: "
read resp_cockroach


# Create image file
announcement "Creating a zero-filled file $image_name $image_size mega blocks big"
dd if=/dev/zero of=$image_name  bs=1  count="$image_size" iflag=fullblock seek=1G
thingy=$image_name

# Create partitions
announcement "Creating partitions"
fdisk_version=$(fdisk -v | grep 2 | cut -d "." -f2)
echo -n "fdisk_version "; echo $fdisk_version
if [ "$fdisk_version" -lt 25 ]; then
    echo "old"
    fdisk $thingy <<EOF
o       # clear the in memory partition table
n       # new partition

+128M   # 128 MB boot parttion
a       # make a partition bootable
1       # partition number 1
t
6
n
w       # write the partition table
EOF
else
    echo "new"
    fdisk $thingy <<EOF
o
n



+128M
a
t
6
n




w
EOF
fi

announcement "Setting up kpartx drive mapper for $image_name" \
             "and define loopback devices for boot & root"

loop_device=$(kpartx -av $image_name | grep p1 | cut -d" " -f8 | awk '{print$1}')
echo -n "Loop device is "
echo $loop_device
echo -e "\n\nPartprobing $thingy\n"
partprobe $loop_device
echo ""
bootpart=$(echo $loop_device | grep dev | cut -d"/" -f3 | awk '{print$1}')p1
bootpart=/dev/mapper/$bootpart
rootpart=$(echo $loop_device | grep dev | cut -d"/" -f3 | awk '{print$1}')p2
rootpart=/dev/mapper/$rootpart
echo -n "Boot partition is "
echo $bootpart
echo -n "Root partition is "
echo $rootpart
announcement "Formating partitions"
mkdosfs -n BOOT $bootpart
echo ""

if [ "$root_file_system" == "ext4" ]; then
    echo "mkfs.ext4 -O $mkfs_ext4_options  -L Raspbian $rootpart"
    echo "y" | mkfs.ext4 -O $mkfs_ext4_options  -L Raspbian $rootpart && sync
    echo " "
fi
echo ""
fdisk -l $thingy

# cdebootstrap is a tool which will install a Debian base system into a subdirectory of another
announcement "Setting up for cdebootstrap"
echo -e "mkdir sdcard, mount sdcard as /, cdebootstraping $rootpart, mount /boot as $bootpart and mount /proc,/sys & /dev/pts\n\n"
mkdir -v sdcard
echo -n "Mounting "
mount -v -t $root_file_system -o sync $rootpart sdcard
echo " "
include="--include=kbd,locales,keyboard-configuration,console-setup,$include"
exclude="--exclude=$exclude"
echo -n "cdebootstrap's line  "
echo "cdebootstrap --arch armhf ${release} sdcard $http ${include} $exclude"
echo " "

cdebootstrap --arch armhf ${release} sdcard $http ${include} $exclude --allow-unauthenticated && sync || fail

echo -e "\nMount new chroot system\n"
mount -v -t vfat -o sync $bootpart sdcard/boot
mount -v proc sdcard/proc -t proc
mount -v sysfs sdcard/sys -t sysfs
mount -v --bind /dev/pts sdcard/dev/pts

# Adjust a few things
echo -e "\n\nCopy, adjust and reconfigure\n"
echo "Setting up the root password... "
echo root:$root_password | chroot sdcard chpasswd
echo ""

echo "Getting gpg.key's"; echo ""
chroot sdcard wget http://archive.raspberrypi.org/debian/raspberrypi.gpg.key
chroot sdcard apt-key add raspberrypi.gpg.key
rm -v sdcard/raspberrypi.gpg.key; echo ""
chroot sdcard wget http://mirrordirector.raspbian.org/raspbian.public.key
chroot sdcard apt-key add raspbian.public.key
rm -v sdcard/raspbian.public.key; echo ""
chroot sdcard apt-key list

echo -e "\nAdjusting /etc/apt/sources.list for Release $release  Bootloader $bootloader  rPi_version $rpi_version  debian_version $debian_version\n"

if [ "$bootloader" == "kernel" ]; then
    rasp_stuff+=" raspberrypi-bootloader"
    sed -i sdcard/etc/apt/sources.list -e "s/main/main contrib non-free firmware/"
    echo "deb http://archive.raspberrypi.org/debian/ jessie main" >> sdcard/etc/apt/sources.list
else
    rasp_stuff+=" raspberrypi-bootloader-nokernel linux-image-$rpi_version-rpfv hardlink oracle-java8-jdk"
    echo -n "Creating preference for libraspberrypi-bin... "
    echo "# This sets the preference for the kernel version lower so the no-kernel are used" > sdcard/etc/apt/preferences.d/02VideoCore.pref
    echo "Package: libraspberrypi-bin libraspberrypi0 libraspberrypi-dev libraspberrypi-doc"  >> sdcard/etc/apt/preferences.d/02VideoCore.pref
    echo "Pin: release o=Raspbian,n=$release"  >> sdcard/etc/apt/preferences.d/02VideoCore.pref
    echo "Pin-Priority: 700"  >> sdcard/etc/apt/preferences.d/02VideoCore.pref
    if [ "$debian_version" == "ftp" ] && [ "$rpi_version" == "rpi2" ]; then
        sed -i sdcard/etc/apt/sources.list -e "s/main/main contrib non-free/"
        echo "deb http://mirrordirector.raspbian.org/raspbian $release main firmware" >> sdcard/etc/apt/sources.list
        sed -i sdcard/etc/apt/preferences.d/02VideoCore.pref -e "s/700/49/"
    else
        sed -i sdcard/etc/apt/sources.list -e "s/main/main contrib non-free firmware/"
        echo "deb http://archive.raspberrypi.org/debian wheezy main" >> sdcard/etc/apt/sources.list
    fi
fi

echo "Contents of /etc/apt/sources.list"
cat sdcard/etc/apt/sources.list
echo "Contents of /etc/apt/preferences.d/"
cat sdcard/etc/apt/preferences.d/*; echo ""

# Time Management
echo "Changing timezone too..."
if [ "$timezone" == "" ]; then 
    cp -v /etc/timezone sdcard/etc/timezone
else
    echo "$timezone" > sdcard/etc/timezone
fi
cat sdcard/etc/timezone; echo ""

echo "Adjusting locales too..."
if [ "$locales" == "" ]; then 
    cp -v /etc/locale.gen sdcard/etc/locale.gen
else
    sed -i "s/^# \($locales .*\)/\1/" sdcard/etc/locale.gen
fi
grep -v '^#' sdcard/etc/locale.gen; echo ""

echo "Adjusting default local too..."
if [ "$default_locale" == "" ]; then 
    default_locale=$(fgrep "=" /etc/default/locale | cut -f 2 -d '=')
fi
echo $default_locale; echo ""


# This will record the time to get to this point
PRE_NETWORK_DURATION=$(date +%s)

date_set=false
if [ "$date_set" = "false" ] ; then
    # set time with ntpdate
    ntpdate-debian -b &>/dev/null
    if [ $? -eq 0 ] ; then
        echo -n "Time set using ntpdate to... "
        date
        date_set=true
    else
        echo "Failed to set time using ntpdate!"
    fi

    if [ "$date_set" = "false" ] ; then
        # failed to set time with ntpdate, fall back to rdate
        # time server addresses taken from http://tf.nist.gov/tf-cgi/servers.cgi
        timeservers=$timeserver
        timeservers="$timeservers time.nist.gov nist1.symmetricom.com"
        timeservers="$timeservers nist-time-server.eoni.com utcnist.colorado.edu"
        timeservers="$timeservers nist1-pa.ustiming.org nist.expertsmi.com"
        timeservers="$timeservers nist1-macon.macon.ga.us wolfnisttime.com"
        timeservers="$timeservers nist.time.nosc.us nist.netservicesgroup.com"
        timeservers="$timeservers nisttime.carsoncity.k12.mi.us nist1-lnk.binary.net"
        timeservers="$timeservers ntp-nist.ldsbc.edu utcnist2.colorado.edu"
        timeservers="$timeservers nist1-ny2.ustiming.org wwv.nist.gov"
        for ts in $timeservers
        do
            rdate $ts &>/dev/null
            if [ $? -eq 0 ]; then
                echo -n "Time set using timeserver '$ts' to... "
                date
                date_set=true
                break
            else
                echo "Failed to set time using timeserver '$ts'."
            fi
        done
    fi
fi
if [ "$date_set" = "false" ] ; then
    echo "FAILED to set the date, so things are likely to fail now ..."
    echo "Make sure that rdate port 37 is not blocked by your firewall."
fi

# Record the time now that the time is set to a correct value
STARTTIME=$(date +%s)
# And substract the PRE_NETWORK_DURATION from STARTTIME to get the 
# REAL starting time.
REAL_STARTTIME=$(($STARTTIME - $PRE_NETWORK_DURATION))
echo ""
echo "Installation started at $(date --date="@$REAL_STARTTIME" --utc)."
echo ""

echo "Setting up keyboard"
if [ "$number_of_keys" == "" ]; then 
    cp -v /etc/default/keyboard sdcard/etc/default/keyboard
else
    # setting up keyboard package
    echo "Adjusting  keyboard to $number_of_keys $keyboard_layout... "
    # adjust variables
    xkbmodel=XKBMODEL='"'$number_of_keys'"'
    xkblayout=XKBLAYOUT='"'$keyboard_layout'"'
    xkbvariant=XKBVARIANT='"'$keyboard_variant'"'
    xkboptions=XKBOPTIONS='"'$keyboard_options'"'
    backspace=BACKSPACE='"'$backspace'"'

    # make keyboard file
    cat <<EOF > sdcard/etc/default/keyboard
# KEYBOARD CONFIGURATION FILE
$xkbmodel
$xkblayout
$xkbvariant
$xkboptions
$backspace
EOF
fi
echo ""; cat sdcard/etc/default/keyboard; echo ""
# end keyboard

echo "Creating cmdline.txt";echo ""
echo "dwc_otg.lpm_enable=0 console=ttyAMA0,115200 console=tty1 root=/dev/mmcblk0p2 rootfstype=ext4 elevator=deadline rootwait" > sdcard/boot/cmdline.txt

echo "Adding Raspberry Pi tweaks to sysctl.conf"; echo ""
echo "" >> sdcard/etc/sysctl.conf
echo "# http://www.raspberrypi.org/forums/viewtopic.php?p=104096#p104096" >> sdcard/etc/sysctl.conf
echo "# rpi tweaks" >> sdcard/etc/sysctl.conf
echo "vm.swappiness = 1" >> sdcard/etc/sysctl.conf
echo "vm.min_free_kbytes = 8192" >> sdcard/etc/sysctl.conf
echo "vm.vfs_cache_pressure = 50" >> sdcard/etc/sysctl.conf
echo "vm.dirty_writeback_centisecs = 1500" >> sdcard/etc/sysctl.conf
echo "vm.dirty_ratio = 20" >> sdcard/etc/sysctl.conf
echo "vm.dirty_background_ratio = 10" >> sdcard/etc/sysctl.conf


echo "Adjust hosts and hostname"; echo ""
echo $hostname > sdcard/etc/hostname
echo "127.0.1.1 $domain_name $hostname" >> sdcard/etc/hosts

echo "CONF_SWAPSIZE=100" > sdcard/etc/dphys-swapfile
sed -i sdcard/etc/default/rcS -e "s/^#FSCKFIX=no/FSCKFIX=yes/"
sed -i sdcard/lib/udev/rules.d/75-persistent-net-generator.rules -e 's/KERNEL\!="eth\*|ath\*|wlan\*\[0-9\]/KERNEL\!="ath\*/'
chroot sdcard dpkg-divert --add --local /lib/udev/rules.d/75-persistent-net-generator.rules


# Setup fstab
echo -e "\nSetting up fstab\n"
cat <<EOF > sdcard/etc/fstab
#<file system>  <dir>          <type>   <options>       <dump>  <pass>
proc            /proc           proc    defaults          0       0
/dev/mmcblk0p1  /boot           vfat    defaults          0       2
/dev/mmcblk0p2  /               ext4    defaults,noatime  0       1
# a swapfile is not a swap partition, so no using swapon|off from here on, use  dphys-swapfile swap[on|off]  for that
EOF

cat sdcard/etc/fstab && sync; echo " "

chroot sdcard dpkg-reconfigure -f noninteractive locales
echo " "
chroot sdcard locale-gen LANG="$default_locale"
echo " "
chroot sdcard dpkg-reconfigure -f noninteractive tzdata
echo " "
chroot sdcard dpkg-reconfigure -f noninteractive keyboard-configuration
echo " "
chroot sdcard dpkg-reconfigure -f noninteractive console-setup

echo "Done Coping, adjusting and reconfiguring"

echo -e "Setting up networking\n\n"

cat <<EOF > sdcard/etc/network/interfaces
# This file describes the network interfaces available on your system
# and how to activate them. For more information, see interfaces(5).
# The loopback network interface
auto lo
iface lo inet loopback
# The primary network interface
auto eth0
EOF

if [ "$dhcp" == "yes" ]; then
    echo "iface eth0 inet dhcp" >> sdcard/etc/network/interfaces
else
    echo "iface eth0 inet static" >> sdcard/etc/network/interfaces
    echo "    address	$address" >> sdcard/etc/network/interfaces
    echo "    netmask	$netmask" >> sdcard/etc/network/interfaces
    echo "    broadcast	$broadcast" >> sdcard/etc/network/interfaces
    echo "    gateway	$gateway" >> sdcard/etc/network/interfaces
fi

cat <<EOF > sdcard/etc/modprobe.d/ipv6.conf
# Don't load ipv6 by default
alias net-pf-10 off
#alias ipv6 off
EOF

echo "hostname "
cat sdcard/etc/hostname
echo " "
echo "resolv.conf "
cat sdcard/etc/resolv.conf
echo " "
echo "hosts"
cat sdcard/etc/hosts
echo " "
echo "network/interfaces"
cat sdcard/etc/network/interfaces
echo " "
echo "modprobe.d/ipv6.conf"
cat sdcard/etc/modprobe.d/ipv6.conf
echo " "

# Setup wifi
if [ "$resp_wifi_name" != "" ]; then
    echo -e "\nSetting up wifi\n"
    cat <<EOF >> sdcard/etc/network/interfaces
allow-hotplug wlan0
iface wlan0 inet manual
    wpa-conf /etc/wpa_supplicant/wpa_supplicant.conf

allow-hotplug wlan1
iface wlan1 inet manual
    wpa-conf /etc/wpa_supplicant/wpa_supplicant.conf
EOF
    cat <<EOF >> sdcard/etc/wpa_supplicant/wpa_supplicant.conf
network={
    ssid="$resp_wifi_name"
    psk="$resp_wifi_passwd"
}
EOF
fi

# end Networking

# Update and install raspberrypi-bootloader
echo -e "\n\nUpdate install and install raspberrypi-bootloader and other stuff\n"
echo -e "apt-get update\n\n"
chroot sdcard apt-get update || fail
echo -e "\n\napt-get  -y upgrade\n\n"
chroot sdcard apt-get -y upgrade || fail
echo -e "\n\napt-get -y dist-upgrade\n\n"
chroot sdcard apt-get -y dist-upgrade || fail
chroot sdcard apt-get clean
echo -e "\n\napt-get -y install $rasp_stuff\n\n"
chroot sdcard apt-get -y install $rasp_stuff || fail
chroot sdcard apt-get clean

if [ "$misc_stuff" != "" ]; then
    echo -e "\n\nInstalling Misc_stuff apt-get -y install $misc_stuff\n\n"
    chroot sdcard apt-get -y install $misc_stuff || fail
    chroot sdcard apt-get clean
fi

chroot sdcard apt-get autoremove -y
echo -e "\n\nOk, Done with raspberrypi-bootloader and other stuff\n\n"
echo "=====================================================" 
echo ""

echo "====================================================="
if [ "$resp_redis" = "" ] || [ "$resp_redis" = "y" ] || [ "$resp_redis" = "yes" ]; then
    echo -n "Installing Redis...."
    resp_redis="y"
    chroot sdcard apt-get -y install redis-server python-redis
    echo "[done]"
else
    echo "Redis not installed"
    echo "====================================================="
    echo ""
fi
    
echo "====================================================="
if [ "$resp_scala" = "" ] || [ "$resp_scala" = "y" ] || [ "$resp_scala" = "yes" ]; then
    echo  "Installing Scala..."
    resp_scala="y"
    wget -P sdcard $scala_url
    mkdir -p sdcard/usr/lib/scala
    chroot sdcard tar -xzf scala-2.11.6.tgz -C /usr/lib/scala
    rm sdcard/scala-2.11.6.tgz*
    chroot sdcard ln -sf /usr/lib/scala/scala-2.11.6/bin/scala /bin/scala
    chroot sdcard ln -sf /usr/lib/scala/scala-2.11.6/bin/scalac /bin/scalac
    echo "[done]"
else
    echo "Scala not installed"
    echo "====================================================="
    echo ""
fi
 
echo "====================================================="
if [ "$resp_spark" = "" ] || [ "$resp_spark" = "y" ] || [ "$resp_spark" = "yes" ]; then
    echo -n "Installing Spark..."
    resp_spark="y"
    chroot sdcard apt-get -y install git &>/dev/null
    wget -P sdcard $spark_url
    chroot sdcard tar xzf spark-1.6.1-bin-hadoop2.6.tgz -C /opt
    rm sdcard/spark-1.6.1-bin-hadoop2.6.tgz*
    echo "[done]"

else
    echo "Spark not installed"
    echo "====================================================="
    echo ""
fi

echo "====================================================="
if [ "$resp_spark" = "y" ] && [ "$resp_redis" = "y" ] && [ "$resp_scala" = "y" ]; then
    echo -n "Installing maven..."
    chroot sdcard apt-get -y install maven
    echo "[done]"
    echo -n "Installing Spark-redis-connector..."
    chroot sdcard << EOF
git clone $spark_redis_url
cd spark-redis
mvn clean package -DskipTests
EOF
    echo "[done]"

else
    echo "Spark-redis-connector not installed"
    echo "====================================================="
    echo ""
fi


echo "====================================================="
if [ "$resp_zeromq" = "" ] || [ "$resp_zeromq" = "y" ] || [ "$resp_zeromq" = "yes" ]; then
    echo -n "Installing ZeroMQ pre-requisites..."
    chroot sdcard apt-get -y install libtool pkg-config build-essential autoconf automake &>/dev/null
    wget -P sdcard $libsodium_url
    chroot sdcard tar xzf libsodium-1.0.3.tar.gz
    rm sdcard/libsodium-1.0.3.tar.gz*
    chroot sdcard << EOF
cd libsodium-1.0.3/
./configure
make
make install
EOF
    rm -r sdcard/libsodium-1.0.3/
    echo "[done]"

    echo -n "Installing ZeroMQ..."
    wget -P sdcard $zeromq_url
    chroot sdcard << EOF
tar -zxf zeromq-4.1.3.tar.gz &>/dev/null
cd zeromq-4.1.3/
./configure
make
make install
ldconfig
cd ..
rm -r zeromq-4.1.3/
rm zeromq-4.1.3.tar.gz
EOF
    echo "[done]"
else
    echo "ZeroMQ not installed"
    echo "====================================================="
    echo ""
fi

echo "====================================================="
if [ "$resp_mqtt" = "" ] || [ "$resp_mqtt" = "y" ] || [ "$resp_mqtt" = "yes" ];
then
    echo -n "Installing MQTT..."
    chroot sdcard << EOF
curl -O $mqtt_key
apt-key add mosquitto-repo.gpg.key
rm mosquitto-repo.gpg.key
cd /etc/apt/sources.list.d/
curl -O $mqtt_url
apt-get update
apt-get -y install mosquitto mosquitto-clients python-mosquitto
EOF
    echo "[done]"
else
    echo "MQTT not installed"
    echo "====================================================="
    echo ""
fi

echo "====================================================="
if [ "$resp_cockroach" = "" ] || [ "$resp_cockroach" = "y" ] || [ "$resp_cockroach" = "yes" ];
then
    echo -n "Installing cockroachdb..."
    sudo su
    cat <<EOF >> sdcard/root/.bashrc
export PATH=\$PATH:/usr/local/go/bin
export GOPATH=\$HOME/work
EOF
    exit
    wget -P sdcard https://storage.googleapis.com/golang/go1.6.2.linux-armv6l.tar.gz
    chroot sdcard << EOF
tar xzf go1.6.2.linux-armv6l.tar.gz  -C /usr/local
rm go1.6.2.linux-armv6l.tar.gz
go get -d github.com/cockroachdb/cockroach
cd \$GOPATH/src/github.com/cockroachdb/cockroach
git checkout beta-20160505
make build
EOF
    echo "[done]"
else
    echo "cockroachdb not installed"
    echo "====================================================="
    echo ""
fi

echo "====================================================="
if [ "$resp_strongswan" = "" ] || [ "$resp_strongswan" = "y" ] || [ "$resp_strongswan" = "yes" ];
then
    echo -n "Installing StrongSwan..."
    chroot sdcard << EOF
curl -L -O https://raw.github.com/philplckthun/setup-strong-strongswan/master/setup.sh
./setup.sh
rm setup.sh
EOF
    echo "[done]"
else
    echo "StrongSwan not installed"
    echo "====================================================="
    echo ""
fi


# wrap up

sync
du -ch sdcard | grep total
echo "=====================================================" 
echo " "
echo "Unmounting mount points"
fuser -av sdcard
fuser -kv sdcard
umount sdcard/proc
umount sdcard/sys
umount sdcard/dev/pts
umount -v sdcard/boot
umount -v sdcard
kpartx -dv $image_name
fuser -av sdcard
rm -rf sdcard
echo " "
echo $start_time
echo $(date)
echo " "
echo -e "\n\niota install finished\n"
echo "Move .img file to sd-card"
echo "=====================================================" 
exit 0
