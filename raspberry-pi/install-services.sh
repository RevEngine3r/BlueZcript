#!/bin/bash

# BlueZcript Service Installer
# Installs and enables systemd services for BLE listener and Web UI

set -e

echo "========================================"
echo "  BlueZcript Service Installer"
echo "========================================"
echo ""

# Check if running as root
if [ "$EUID" -ne 0 ]; then 
    echo "Please run as root (use sudo)"
    exit 1
fi

# Get the directory where script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
REPO_DIR="$(dirname "$SCRIPT_DIR")"

echo "[1/5] Installing dependencies..."
apt-get update
apt-get install -y python3 python3-pip sshpass bluetooth bluez

# Install Python dependencies
echo "[2/5] Installing Python packages..."
pip3 install fastapi uvicorn jinja2 qrcode pillow bluepy python-multipart

echo "[3/5] Installing systemd services..."

# Update service files with correct paths
sed -i "s|/home/pi/Bluezcript|$REPO_DIR|g" "$SCRIPT_DIR/bluezcript-listener.service"
sed -i "s|/home/pi/Bluezcript|$REPO_DIR|g" "$SCRIPT_DIR/bluezcript-webui.service"

# Copy service files
cp "$SCRIPT_DIR/bluezcript-listener.service" /etc/systemd/system/
cp "$SCRIPT_DIR/bluezcript-webui.service" /etc/systemd/system/

# Make scripts executable
chmod +x "$SCRIPT_DIR/trigger_action.sh"
chmod +x "$SCRIPT_DIR/ble_listener.py"
chmod +x "$SCRIPT_DIR/web_app.py"

echo "[4/5] Enabling services..."
systemctl daemon-reload
systemctl enable bluezcript-listener.service
systemctl enable bluezcript-webui.service

echo "[5/5] Starting services..."
systemctl start bluezcript-listener.service
systemctl start bluezcript-webui.service

echo ""
echo "========================================"
echo "  Installation Complete!"
echo "========================================"
echo ""
echo "Services Status:"
systemctl status bluezcript-listener.service --no-pager -l || true
echo ""
systemctl status bluezcript-webui.service --no-pager -l || true
echo ""
echo "Useful Commands:"
echo "  View listener logs:  sudo journalctl -u bluezcript-listener -f"
echo "  View Web UI logs:    sudo journalctl -u bluezcript-webui -f"
echo "  Restart listener:    sudo systemctl restart bluezcript-listener"
echo "  Restart Web UI:      sudo systemctl restart bluezcript-webui"
echo "  Stop services:       sudo systemctl stop bluezcript-listener bluezcript-webui"
echo ""
echo "Web UI: http://$(hostname -I | awk '{print $1}'):8000"
echo ""
