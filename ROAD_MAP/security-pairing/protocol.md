# BlueZcript Beacon Protocol

To ensure secure triggering, BlueZcript uses an authenticated advertising format within the BLE Manufacturer Data field.

## Beacon Format (Total: 15 Bytes)
| Offset | Size | Field | Description |
|--------|------|-------|-------------|
| 0      | 2    | ID    | Company ID (0xFFFF for development) |
| 2      | 1    | TYPE  | Command Type (0x01 = Trigger) |
| 3      | 4    | NONCE | Monotonically increasing counter (Big Endian) |
| 7      | 8    | SIG   | Truncated HMAC-SHA256 (using PSK) |

## Authentication Logic
1. **Message Construction**: `Payload = Type + Nonce` (5 bytes total).
2. **Signature Generation**: `HMAC-SHA256(PSK, Payload)`.
3. **Truncation**: Take the first 8 bytes of the resulting hash.
4. **Verification**: 
   - Device must exist in `trusted_devices.json`.
   - `Nonce` must be strictly greater than `last_nonce`.
   - `SIG` must match the locally computed HMAC.
