#!/bin/bash
set -e

# Ensure script runs in the directory where it is saved (and where docker-compose.yml is)
cd "$(dirname "$0")"

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

echo "=== Database Setup ==="

# Detect package manager
if command_exists apt; then
    PM="apt"
    UPDATE="sudo apt update -y"
    INSTALL="sudo apt install -y"
elif command_exists dnf; then
    PM="dnf"
    UPDATE="sudo dnf -y check-update"
    INSTALL="sudo dnf -y install"
else
    echo "❌ Unsupported package manager. Please install Docker manually."
    exit 1
fi

# Install Docker if not installed
if ! command_exists docker; then
    echo "Docker not found. Installing..."
    $UPDATE

    if [ "$PM" = "apt" ]; then
        # Try installing the modern plugin first
        if sudo apt-cache show docker-compose-plugin >/dev/null 2>&1; then
             $INSTALL docker.io docker-compose-plugin
        else
             # Fallback for Kali: Install docker.io and standalone docker-compose
             echo "⚠️  'docker-compose-plugin' not found. Installing standalone 'docker-compose'..."
             $INSTALL docker.io docker-compose
        fi

    elif [ "$PM" = "dnf" ]; then
        $INSTALL dnf-plugins-core
        sudo dnf config-manager --add-repo https://download.docker.com/linux/fedora/docker-ce.repo
        $INSTALL docker-ce docker-ce-cli containerd.io docker-compose-plugin
    fi
else
    echo "✓ Docker is already installed."
fi

# Start Docker service if not running
if ! systemctl is-active --quiet docker; then
    echo "Starting Docker service..."
    sudo systemctl enable docker
    sudo systemctl start docker
fi

echo "Launching PostgreSQL container..."
# Try modern command first, then fallback to legacy hyphenated command
if docker compose version >/dev/null 2>&1; then
    sudo docker compose up -d
else
    sudo docker-compose up -d
fi

# Simple verification
if sudo docker ps --format '{{.Names}}' | grep -q "postgres"; then
    echo "✓ Database container is running!"
else
    echo "⚠️  Container attempted to start but is not listed in 'docker ps'. Check logs with 'docker compose logs'."
fi