# BlueZcript Project Map

## Overview
BlueZcript is a BLE-based remote execution system. It allows an Android device to trigger shell scripts on a Raspberry Pi 4 over Bluetooth Low Energy.

## Components
- **Raspberry Pi Listener**: Python service using BLE peripheral mode to listen for trigger packets.
- **Android Controller**: A Kotlin/Compose application providing a simple UI to send the trigger.

## Directory Structure
- `raspberry-pi/`: Python source code and service configuration.
- `android-app/`: Kotlin/Compose Android project.
- `ROAD_MAP/`: Documentation of planned and completed features.
