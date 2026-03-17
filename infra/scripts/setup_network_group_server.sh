#!/bin/bash
set -e

if [ "$EUID" -ne 0 ]; then echo "❌ Run as root"; exit 1; fi

echo "=== Setting up GROUP SERVER Network ==="

echo "Backing up interfaces..."
cp /etc/network/interfaces /etc/network/interfaces.bak.$(date +%F_%T)

echo "Writing network configuration..."
cat > /etc/network/interfaces <<EOF
# This file describes the network interfaces available on your system
# and how to activate them. For more information, see interfaces(5).

source /etc/network/interfaces.d/*

# The loopback network interface
auto lo
iface lo inet loopback

# INTERNET (Adapter 1 - NAT)
auto eth0
iface eth0 inet dhcp

# LEG 1: Facing Client Firewall (Adapter 2)
auto eth1
iface eth1 inet static
    address 192.168.4.20
    netmask 255.255.255.0
    up ip route add 192.168.0.0/24 via 192.168.4.30 dev eth1
    up ip route add 192.168.3.0/24 via 192.168.4.30 dev eth1

EOF

echo "Restarting networking..."
systemctl restart networking

echo "Configuring iptables..."

apt-get install -y iptables-persistent

iptables -P INPUT DROP
iptables -P FORWARD DROP
iptables -P OUTPUT ACCEPT

iptables -F

# Allow established connections
iptables -A INPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT

# Allow traffic from Client network on port 8080
iptables -A INPUT -i eth1 -p tcp -s 192.168.0.0/24 --dport 8081 -j ACCEPT
# Allow traffic from Transaction Server
iptables -A INPUT -i eth1 -p tcp -s 192.168.3.0/24 --dport 8081 -j ACCEPT

# Save iptables rules
netfilter-persistent save

echo "✓ Auth Server network configured successfully!"
echo "  eth0: DHCP"
echo "  eth1: 192.168.4.20 (Auth API on port 8080)"

