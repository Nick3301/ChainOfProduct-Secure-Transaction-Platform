#!/bin/bash
set -e

if [ "$EUID" -ne 0 ]; then echo "❌ Run as root"; exit 1; fi

echo "=== Setting up DATABASE Network ==="

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

# INTERNAL (Adapter 2 - Facing Firewall)
auto eth1
iface eth1 inet static
    address 192.168.2.40
    netmask 255.255.255.0
    # Route reply traffic back to Server network (1.x) via Firewall (2.30)
    up ip route add 192.168.1.0/24 via 192.168.2.30 dev eth1
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

# Allow PostgreSQL connections from server
iptables -A INPUT -i eth1 -p tcp -s 192.168.1.20 --dport 5432 -j ACCEPT

# Save iptables rules
netfilter-persistent save

echo "✓ Database network configured successfully!"
echo "  eth0: DHCP"
echo "  eth1: 192.168.2.40 (Route to Server added)"