#!/usr/bin/env bash

set -e

##########################################################
# Image configs
HOSTNAME="iota"
USERNAME="iota"
TZDATA="Europe/London"

#########################################################
cd
BASEDIR=$(pwd)
DEVICE_R=${BASEDIR}/
ARCH=$(uname -m)
export TZ=${TZDATA}

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

function configure_timezone() {
    printGreen "Setup timezone ${TZDATA}..."
    # Set time zone
    echo ${TZDATA} > /etc/timezone
    dpkg-reconfigure -f noninteractive tzdata
}

function apt_upgrade() {
    printGreen "Upgrade..."
    apt-get -f -y install
    dpkg --configure -a
    apt-get update
    apt-get -y -u dist-upgrade
}

function apt_clean() {
    printGreen "Clean packages..."
    apt-get -y autoremove
    apt-get clean
}


function create_groups() {
    printGreen "Create groups..."
    groupadd -f --system gpio
    groupadd -f --system i2c
    groupadd -f --system input
    groupadd -f --system spi

    # Create adduser hook
    cat <<'EOM' >/usr/local/sbin/adduser.local
#!/bin/sh
# This script is executed as the final step when calling `adduser`
# USAGE:
#   adduser.local USER UID GID HOME

# Add user to the Raspberry Pi specific groups
usermod -a -G adm,gpio,i2c,input,spi,video $1
EOM
    chmod +x /usr/local/sbin/adduser.local
}

# Create default user
function create_user() {
    printGreen "Create user..."
    apt-get -y install whois
    local DATE=$(date +%m%H%M%S)
    local PASSWD=$(mkpasswd -m sha-512 ${USERNAME} ${DATE})

    adduser --gecos "iota user" --add_extra_groups --disabled-password ${USERNAME}
    usermod -a -G sudo -p ${PASSWD} ${USERNAME}
}


function configure_ssh() {
    printGreen "Configure ssh..."
    apt-get -y install openssh-server
    cat > /etc/systemd/system/sshdgenkeys.service << EOF
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

    mkdir -p /etc/systemd/system/ssh.service.wants
    ln -s /etc/systemd/system/sshdgenkeys.service /etc/systemd/system/ssh.service.wants
}

function configure_network() {
    printGreen "Set hostename ${HOSTNAME}..."

    # Set up hosts
    echo ${HOSTNAME} >/etc/hostname
    cat <<EOM >/etc/hosts
127.0.0.1       localhost
::1             localhost ip6-localhost ip6-loopback
ff02::1         ip6-allnodes
ff02::2         ip6-allrouters

127.0.1.1       ${HOSTNAME}
EOM

    # Set up interfaces
    printGreen "Configure network..."
    cat <<EOM >/etc/network/interfaces
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

function install_zeromq(){

    printGreen "Installing ZeroMQ..."
    apt-get -y install libtool pkg-config build-essential autoconf automake
    wget -P $R https://download.libsodium.org/libsodium/releases/libsodium-1.0.3.tar.gz
    cd $R
    tar xzf libsodium-1.0.3.tar.gz
    rm $R/libsodium-1.0.3.tar.gz
    cd $R/libsodium-1.0.3/
    ./configure
    make
    make install
    cd
    rm -r $R/libsodium-1.0.3/
    wget -P $R http://download.zeromq.org/zeromq-4.1.3.tar.gz
    cd $R
    tar -zxf zeromq-4.1.3.tar.gz
    cd zeromq-4.1.3/
    ./configure
    make
    make install
    ldconfig
    cd ..
    rm -r zeromq-4.1.3/
    rm zeromq-4.1.3.tar.gz
    cd
}

function install_scala(){
    printGreen  "Installing Scala..."
    wget -P $R https://downloads.typesafe.com/scala/2.11.6/scala-2.11.6.tgz
    mkdir -p /usr/lib/scala
    cd $R
    tar -xzf scala-2.11.6.tgz -C /usr/lib/scala
    rm $R/scala-2.11.6.tgz
    ln -sf /usr/lib/scala/scala-2.11.6/bin/scala /bin/scala
    ln -sf /usr/lib/scala/scala-2.11.6/bin/scalac /bin/scalac

}

function install_spark(){
    printGreen "Installing Spark..."
    wget -P $R http://apache.mirror.anlx.net/spark/spark-1.6.1/spark-1.6.1-bin-hadoop2.6.tgz
    cd $R
    tar xzf spark-1.6.1-bin-hadoop2.6.tgz -C /opt
    rm $R/spark-1.6.1-bin-hadoop2.6.tgz
    cat <<EOF >> $R/etc/bash.bashrc
export PATH=\$PATH:/opt/spark-1.6.1-bin-hadoop2.6/bin
EOF
}

function install_software() {
    printGreen "Install extra packages..."
    apt-add-repository ppa:webupd8team/java
    apt-get update
    echo "oracle-java8-installer shared/accepted-oracle-license-v1-1 select true" | debconf-set-selections
    apt-get install -y oracle-java8-installer
    apt-get -y install htop nano avahi-utils vim git ntp

    install_zeromq

    printGreen "Installing MQTT..."
    apt-get -y install mosquitto mosquitto-clients

    printGreen  "Installing Redis...."
    apt-get -y install redis-server python-redis

    install_scala
    install_spark

    printGreen "Install iota packages..."

}


function stage_01_base() {
    printGreen "================================================"
    printGreen "Stage 1 - Base system"
    printGreen "================================================"

    #configure_timezone
    apt_upgrade
    apt_clean
}

function stage_02_desktop() {
    printGreen "================================================"
    printGreen "Stage 2 - Desktop"
    printGreen "================================================"

    #create_groups
    create_user
    #configure_ssh
    #configure_network
    #apt_upgrade
    #apt_clean
}

function stage_03_extra_SW() {
    printGreen "================================================"
    printGreen "Stage 3 - Add Extra Software"
    printGreen "================================================"

    R="${DEVICE_R}"
    install_software
    apt_upgrade
    apt_clean
}

#########################################################
# Start
startTime=$(date +%s)


#########################################################
# Main
stage_01_base
stage_02_desktop
stage_03_extra_SW


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
#!/usr/bin/env bash