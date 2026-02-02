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

    def register_device(self, device_id, name, psk=None):
        """Generates a new PSK for a device."""
        if psk is None:
            psk = secrets.token_hex(16)
        self.devices[device_id] = {
            "name": name,
            "psk": psk,
            "last_nonce": 0,
            "added_at": int(time.time())
        }
        self._save_db()
        return psk

    def verify_any_device(self, nonce, received_sig):
        """
        Try to verify the signature with any registered device's PSK.
        Returns (is_valid, device_name) tuple.
        This allows MAC-independent authentication.
        """
        # Construct the message that was signed
        message = b"\x01" + nonce.to_bytes(4, 'big')
        
        for device_id, device in self.devices.items():
            # Check replay attack
            if nonce <= device["last_nonce"]:
                continue
            
            # Compute expected signature
            expected_sig = hmac.new(
                bytes.fromhex(device["psk"]),
                message,
                hashlib.sha256
            ).digest()[:8]
            
            # Check if signature matches
            if hmac.compare_digest(expected_sig, received_sig):
                # Valid! Update nonce and save
                device["last_nonce"] = nonce
                self._save_db()
                return True, device["name"]
        
        return False, None

    def verify_beacon(self, device_id, nonce, received_sig):
        """
        Legacy method: Verifies the HMAC signature for a specific device.
        Kept for backwards compatibility.
        """
        device = self.devices.get(device_id)
        if not device:
            return False, "Unknown device"

        if nonce <= device["last_nonce"]:
            return False, "Replay detected"

        # Construct data for HMAC: [Command(1 byte) + Nonce(4 bytes)]
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
