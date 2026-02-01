#!/bin/bash

# Navigate to the directory where the script is located
cd "$(dirname "$0")"

# Create virtual environment if it doesn't exist
if [ ! -d ".venv" ]; then
    echo "Creating virtual environment (.venv)..."
    python3 -m venv .venv
fi

# Activate the virtual environment
source .venv/bin/activate

# Install or update requirements
if [ -f "requirements.txt" ]; then
    echo "Installing requirements from requirements.txt..."
    pip install --upgrade pip
    pip install -r requirements.txt
else
    echo "requirements.txt not found, installing 'bless' directly..."
    pip install bless
fi

# Start the Python BLE server
echo "Starting BlueZcript BLE Server..."
python3 listener.py
