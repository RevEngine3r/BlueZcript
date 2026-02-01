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
        # Filter for manufacturer data (Type 0xFF)
        mfg_data = dev.getValueText(255)
        if not mfg_data:
            return

        try:
            # Convert hex string to bytes
            data_bytes = bytes.fromhex(mfg_data)
            
            # Protocol check:
            # [0:2] Company ID (0xFFFF)
            # [2] Type (0x01)
            # [3:7] Nonce (4 bytes)
            # [7:15] Signature (8 bytes)
            
            if len(data_bytes) < 15:
                return

            company_id = int.from_bytes(data_bytes[0:2], 'big')
            if company_id != 0xFFFF:
                return

            cmd_type = data_bytes[2]
            nonce = int.from_bytes(data_bytes[3:7], 'big')
            signature = data_bytes[7:15]
            
            # Identify device by address (simplified for this implementation)
            device_id = dev.addr.replace(":", "").lower()
            
            is_valid, reason = self.sm.verify_beacon(device_id, nonce, signature)
            
            if is_valid:
                print(f"[*] Valid trigger from {device_id} (Nonce: {nonce})")
                self.execute_trigger()
            else:
                if reason != "Unknown device": # Don't log every random BLE device
                    print(f"[!] Blocked beacon from {device_id}: {reason}")

        except Exception as e:
            # Quietly ignore malformed packets
            pass

    def execute_trigger(self):
        script_path = "./trigger_script.sh"
        if os.path.exists(script_path):
            subprocess.Popen(["bash", script_path])
        else:
            print("[!] Trigger received but trigger_script.sh not found")

async def main():
    sm = SecurityManager()
    scanner = Scanner().withDelegate(ScanDelegate(sm))
    
    print("--- BlueZcript Authenticated Listener Active ---")
    while True:
        # Scan for 2 seconds, then repeat
        scanner.scan(2.0, passive=True)
        await asyncio.sleep(0.1)

if __name__ == "__main__":
    asyncio.run(main())
