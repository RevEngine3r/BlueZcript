# Secure Pairing & Triggering

## Overview
This feature implements a secure, authenticated trigger system to ensure only authorized devices can execute scripts on the Raspberry Pi.

## Architecture
- **Web UI**: A FastAPI-based dashboard on the Pi to manage pairings.
- **Authentication**: HMAC-SHA256 based signatures for BLE beacons.
- **Replay Protection**: Incrementing nonces stored per device.

## Steps
- [ ] **STEP 1**: Protocol Design & Key Storage (Define beacon format and JSON storage).
- [ ] **STEP 2**: Web UI Implementation (Device listing and 'Pairing Mode' toggle).
- [ ] **STEP 3**: Android Key Exchange (Pairing handshake via BLE connection).
- [ ] **STEP 4**: Authenticated Listener (Verify HMAC signatures on beacons).
