import hmac
import hashlib
import json
import os
import secrets
import time

class SecurityManager:
    """Manages trusted devices, keys, and HMAC verification."""
    
    def __init__(self, db_path="trusted_devices.json"):
        self.db_path = db_path
        self.devices = self._load_db()

    def _load_db(self):
        if os.path.exists(self.db_path):
            with open(self.db_path, 'r') as f:
                return json.load(f)
        return {}

    def _save_db(self):
        with open(self.db_path, 'w') as f:
            json.dump(self.devices, f, indent=4)

    def register_device(self, device_id, name):
        """Generates a new PSK for a device."""
        psk = secrets.token_hex(16)
        self.devices[device_id] = {
            "name": name,
            "psk": psk,
            "last_nonce": 0,
            "added_at": int(time.time())
        }
        self._save_db()
        return psk

    def verify_beacon(self, device_id, nonce, received_sig):
        """
        Verifies the HMAC signature and checks for replay attacks.
        
        Args:
            device_id: Unique ID of the sender.
            nonce: 4-byte integer nonce.
            received_sig: 8-byte signature bytes.
        """
        device = self.devices.get(device_id)
        if not device:
            return False, "Unknown device"

        if nonce <= device["last_nonce"]:
            return False, "Replay detected"

        # Construct data for HMAC: [Command(1 byte) + Nonce(4 bytes)]
        # For simplicity, we assume command is 0x01 (Trigger)
        message = b"\x01" + nonce.to_bytes(4, 'big')
        
        expected_sig = hmac.new(
            bytes.fromhex(device["psk"]),
            message,
            hashlib.sha256
        ).digest()[:8]

        if hmac.compare_digest(expected_sig, received_sig):
            device["last_nonce"] = nonce
            self._save_db()
            return True, "Success"
        
        return False, "Invalid signature"
