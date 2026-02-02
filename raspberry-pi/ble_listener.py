import asyncio
from bluepy.btle import Scanner, DefaultDelegate
from security_manager import SecurityManager
import subprocess
import os

class ScanDelegate(DefaultDelegate):
    def __init__(self, security_manager):
        DefaultDelegate.__init__(self)
        self.sm = security_manager

    def handleDiscovery(self, dev, isNewDev, isNewData):
        # Check for manufacturer data with Company ID 0xFFFF
        # Note: bluepy returns manufacturer data indexed by company ID
        for (adtype, desc, value) in dev.getScanData():
            if adtype == 255:  # Manufacturer Specific Data
                try:
                    # Parse the manufacturer data
                    # Format: Company ID (2 bytes little-endian) + payload
                    data_bytes = bytes.fromhex(value)
                    
                    if len(data_bytes) < 2:
                        continue
                    
                    # Extract company ID (little-endian in BLE advertisement)
                    company_id = int.from_bytes(data_bytes[0:2], 'little')
                    
                    if company_id != 0xFFFF:
                        continue
                    
                    # Extract payload (everything after company ID)
                    payload = data_bytes[2:]
                    
                    # Protocol for payload:
                    # [0] Type (0x01)
                    # [1:5] Nonce (4 bytes, big-endian)
                    # [5:13] Signature (8 bytes)
                    
                    if len(payload) < 13:
                        print(f"[!] Invalid payload length: {len(payload)} (expected 13)")
                        continue

                    cmd_type = payload[0]
                    if cmd_type != 0x01:
                        print(f"[!] Unknown command type: 0x{cmd_type:02x}")
                        continue
                    
                    nonce = int.from_bytes(payload[1:5], 'big')
                    signature = payload[5:13]
                    
                    # Identify device by address
                    device_id = dev.addr.replace(":", "").lower()
                    
                    print(f"[*] Received beacon from {device_id} (Nonce: {nonce})")
                    
                    is_valid, reason = self.sm.verify_beacon(device_id, nonce, signature)
                    
                    if is_valid:
                        print(f"[✓] Valid trigger from {device_id}")
                        self.execute_trigger()
                    else:
                        print(f"[✗] Blocked beacon from {device_id}: {reason}")

                except Exception as e:
                    print(f"[!] Error parsing advertisement: {e}")
                    pass

    def execute_trigger(self):
        script_path = "./trigger_action.sh"
        if os.path.exists(script_path):
            print("[→] Executing trigger action...")
            subprocess.Popen(["bash", script_path])
        else:
            print("[!] Trigger received but trigger_action.sh not found")

async def main():
    sm = SecurityManager()
    scanner = Scanner().withDelegate(ScanDelegate(sm))
    
    print("╔═══════════════════════════════════════════════════╗")
    print("║  BlueZcript Authenticated Listener Active        ║")
    print("╚═══════════════════════════════════════════════════╝")
    print(f"Trusted devices: {len(sm.devices)}")
    print("Scanning for BLE advertisements...\n")
    
    while True:
        # Scan for 2 seconds, then repeat
        scanner.scan(2.0, passive=False)
        await asyncio.sleep(0.1)

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[!] Listener stopped by user")
