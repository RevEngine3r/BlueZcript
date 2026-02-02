#!/bin/bash

# MikroTik SSH Configuration
MIKROTIK_HOST="192.168.1.1"  # Change to your MikroTik IP
MIKROTIK_PORT="22"            # Change if using non-standard port
MIKROTIK_USER="admin"         # Change to your username
MIKROTIK_PASS="password"      # Change to your password

# Log file
LOG_FILE="/var/log/bluezcript-trigger.log"

# Timestamp function
timestamp() {
    date "+%Y-%m-%d %H:%M:%S"
}

# Log function
log() {
    echo "[$(timestamp)] $1" | tee -a "$LOG_FILE"
}

log "========================================"
log "BlueZcript Trigger Activated"
log "========================================"

# Check if sshpass is installed
if ! command -v sshpass &> /dev/null; then
    log "ERROR: sshpass is not installed. Installing..."
    sudo apt-get update && sudo apt-get install -y sshpass
    if [ $? -ne 0 ]; then
        log "ERROR: Failed to install sshpass"
        exit 1
    fi
fi

log "Connecting to MikroTik at $MIKROTIK_HOST:$MIKROTIK_PORT..."

# Execute MikroTik command via SSH
# -o StrictHostKeyChecking=no: Auto-accept host key
# -o UserKnownHostsFile=/dev/null: Don't save to known_hosts
# -o LogLevel=ERROR: Suppress SSH warnings
sshpass -p "$MIKROTIK_PASS" ssh \
    -o StrictHostKeyChecking=no \
    -o UserKnownHostsFile=/dev/null \
    -o LogLevel=ERROR \
    -p "$MIKROTIK_PORT" \
    "$MIKROTIK_USER@$MIKROTIK_HOST" \
    "/system script run enableAPsSmart" 2>&1 | tee -a "$LOG_FILE"

# Check exit status
if [ ${PIPESTATUS[0]} -eq 0 ]; then
    log "SUCCESS: Command executed successfully"
else
    log "ERROR: Failed to execute command on MikroTik"
    exit 1
fi

log "========================================"
log "Trigger completed"
log "========================================"

exit 0
