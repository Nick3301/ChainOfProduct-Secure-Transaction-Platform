#!/bin/bash
set -e

# Ensure script is run as root
if [ "$EUID" -ne 0 ]; then
  echo "❌ Please run as root (use sudo)"
  exit 1
fi

echo "=== Setting up CLIENT Network ==="

# 1. Backup existing config
echo "Backing up current interfaces file..."
cp /etc/network/interfaces /etc/network/interfaces.bak.$(date +%F_%T)

# 2. Write the configuration
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

# INTERNAL (Adapter 2 - Connects to Firewall)
auto eth1
iface eth1 inet static
    address 192.168.0.10
    netmask 255.255.255.0
EOF

# 3. Apply changes
echo "Restarting network services..."
systemctl restart networking

echo "✓ Client network configured successfully!"
echo "  eth0: DHCP (Internet)"
echo "  eth1: 192.168.0.10 (Internal)"