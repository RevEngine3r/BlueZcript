import asyncio
import logging
import subprocess
from bless import (
    BlessServer,
    BlessGATTCharacteristic,
    GATTCharacteristicProperties,
    GATTAttributePermissions
)

# Configuration
SERVICE_UUID = "A0010001-0000-1000-8000-00805F9B34FB"
CHAR_UUID = "A0010002-0000-1000-8000-00805F9B34FB"
TRIGGER_VALUE = b"1"
SCRIPT_PATH = "./trigger_action.sh"

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("BlueZcript")

def run_script():
    try:
        logger.info(f"Trigger received! Executing {SCRIPT_PATH}...")
        result = subprocess.run([SCRIPT_PATH], capture_output=True, text=True, check=True)
        logger.info(f"Script output: {result.stdout}")
    except Exception as e:
        logger.error(f"Failed to execute script: {e}")

def write_request(characteristic: BlessGATTCharacteristic, value: bytes, **kwargs):
    characteristic.value = value
    logger.info(f"Characteristic write request: {value}")
    if value == TRIGGER_VALUE:
        run_script()

async def run():
    server = BlessServer(name="BlueZcript-Pi")
    server.read_request_func = None # Not needed
    server.write_request_func = write_request

    # Add Service
    await server.add_new_service(SERVICE_UUID)
    
    # Add Characteristic
    char_flags = (
        GATTCharacteristicProperties.write |
        GATTCharacteristicProperties.write_without_response
    )
    permissions = (
        GATTAttributePermissions.writeable
    )
    await server.add_new_characteristic(
        SERVICE_UUID,
        CHAR_UUID,
        char_flags,
        None,
        permissions
    )

    logger.info("Starting BLE Server...")
    await server.start()
    
    logger.info("Server started. Waiting for connections...")
    while True:
        await asyncio.sleep(1)

if __name__ == "__main__":
    loop = asyncio.get_event_loop()
    loop.run_until_complete(run())
