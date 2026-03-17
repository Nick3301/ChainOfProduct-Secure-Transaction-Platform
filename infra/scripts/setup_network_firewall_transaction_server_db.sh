#!/bin/bash
set -e

if [ "$EUID" -ne 0 ]; then echo "❌ Run as root"; exit 1; fi

echo "=== Setting up FIREWALL Network ==="

# 1. Enable IP Forwarding (Critical for routing traffic)
echo "Enabling persistent IP Forwarding..."
# Uncomment the line in sysctl.conf if it exists, or append it
if grep -q "#net.ipv4.ip_forward=1" /etc/sysctl.conf; then
    sed -i 's/#net.ipv4.ip_forward=1/net.ipv4.ip_forward=1/' /etc/sysctl.conf
elif ! grep -q "net.ipv4.ip_forward=1" /etc/sysctl.conf; then
    echo "net.ipv4.ip_forward=1" >> /etc/sysctl.conf
fi
# Apply immediately
sysctl -p

# 2. Configure Interfaces
echo "Writing network configuration..."
cp /etc/network/interfaces /etc/network/interfaces.bak.$(date +%F_%T)

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

# LEG 1: Facing Server (Adapter 2)
auto eth1
iface eth1 inet static
    address 192.168.1.30
    netmask 255.255.255.0

# LEG 2: Facing Database (Adapter 3)
auto eth2
iface eth2 inet static
    address 192.168.2.30
    netmask 255.255.255.0
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
iptables -A FORWARD -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT

# Allow Server to Database traffic on PostgreSQL port
iptables -A FORWARD -i eth1 -o eth2 -p tcp -s 192.168.1.20 -d 192.168.2.40 --dport 5432 -j ACCEPT

# Save iptables rules
netfilter-persistent save

echo "✓ Firewall network configured successfully!"
echo "  IP Forwarding: ENABLED"
echo "  eth1: 192.168.1.30"
echo "  eth2: 192.168.2.30"