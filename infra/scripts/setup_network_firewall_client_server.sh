#!/bin/bash
set -e

if [ "$EUID" -ne 0 ]; then echo "❌ Run as root"; exit 1; fi

echo "=== Setting up FIREWALL (Client-Server) Network ==="

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

# LEG 1: Facing Client (Adapter 2)
auto eth1
iface eth1 inet static
    address 192.168.0.30
    netmask 255.255.255.0

# LEG 2: Facing Transaction Server (Adapter 3)
auto eth2
iface eth2 inet static
    address 192.168.3.30
    netmask 255.255.255.0

# LEG 3: Facing Group Server DB (Adapter 4)
auto eth3
iface eth3 inet static
    address 192.168.4.30
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

# Redirect traffic hitting Firewall (0.30) on port 8443 to Server (3.20)
iptables -t nat -A PREROUTING -i eth1 -p tcp --dport 8443 -j DNAT --to-destination 192.168.3.20:8443

# We must allow the traffic to pass through now that it is directed to 3.20
iptables -A FORWARD -p tcp -d 192.168.3.20 --dport 8443 -j ACCEPT

# Forwarding: Allow Transaction Server (3.20) to reach Group Server (4.20)
iptables -A FORWARD -i eth2 -o eth3 -s 192.168.3.20 -d 192.168.4.20 -j ACCEPT

# Forwarding: Allow Client (0.x) to reach Group Server (4.20)
# First, the NAT rule (so clients can hit the Firewall IP)
iptables -t nat -A PREROUTING -i eth1 -p tcp --dport 8081 -j DNAT --to-destination 192.168.4.20:8081
# Then, the Forward rule
iptables -A FORWARD -p tcp -d 192.168.4.20 --dport 8081 -j ACCEPT

# Save iptables rules
netfilter-persistent save

echo "✓ Firewall (Client-Server) network configured successfully!"
echo "  IP Forwarding: ENABLED"
echo "  eth1: 192.168.0.30 (Facing Client)"
echo "  eth2: 192.168.3.30 (Facing Server)"
