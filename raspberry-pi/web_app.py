from fastapi import FastAPI, Request, Form
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from security_manager import SecurityManager
import qrcode
import io
import base64
import secrets
import re

app = FastAPI(title="BlueZcript Management")
templates = Jinja2Templates(directory="templates")
sm = SecurityManager()

last_generated_psk = None
pending_registrations = {}  # Temporary storage for pending device registrations

@app.get("/", response_class=HTMLResponse)
async def index(request: Request):
    global last_generated_psk
    devices = sm.devices
    psk_to_show = last_generated_psk
    last_generated_psk = None
    
    qr_base64 = None
    if psk_to_show:
        # QR Format: server_url|temp_id|psk
        # Android will scan this, extract MAC, and POST back to register
        server_url = str(request.base_url).rstrip('/')
        qr_data = f"{server_url}|{psk_to_show['temp_id']}|{psk_to_show['psk']}"
        img = qrcode.make(qr_data)
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        qr_base64 = base64.b64encode(buf.getvalue()).decode()

    return templates.TemplateResponse("index.html", {
        "request": request, 
        "devices": devices,
        "new_psk": psk_to_show,
        "qr_code": qr_base64
    })

@app.post("/add-device")
async def add_device(name: str = Form(...)):
    global last_generated_psk, pending_registrations
    
    # Generate a temporary ID and PSK
    temp_id = secrets.token_hex(8)
    psk = secrets.token_hex(16)
    
    # Store pending registration (will be completed when Android POSTs back)
    pending_registrations[temp_id] = {
        "name": name,
        "psk": psk
    }
    
    last_generated_psk = {
        "temp_id": temp_id,
        "psk": psk,
        "name": name
    }
    return RedirectResponse(url="/", status_code=303)

@app.post("/register-device")
async def register_device(
    device_id: str = Form(...),
    mac_address: str = Form(...),
    psk: str = Form(...)
):
    """Called by Android app to complete registration with actual MAC address"""
    global pending_registrations
    
    # Verify this was a pending registration
    if device_id not in pending_registrations:
        return {"success": False, "error": "Invalid device_id"}
    
    pending = pending_registrations[device_id]
    
    # Verify PSK matches
    if pending["psk"] != psk:
        return {"success": False, "error": "Invalid PSK"}
    
    # Normalize MAC address
    mac_clean = re.sub(r'[^0-9a-fA-F]', '', mac_address).lower()
    
    # Register device with actual MAC address
    sm.devices[mac_clean] = {
        "name": pending["name"],
        "psk": psk,
        "last_nonce": 0,
        "added_at": int(__import__('time').time())
    }
    sm._save_db()
    
    # Clean up pending registration
    del pending_registrations[device_id]
    
    return {"success": True}

@app.post("/manual-register")
async def manual_register(name: str = Form(...), mac_address: str = Form(...)):
    """Manually register a device with actual BLE MAC from listener logs"""
    # Normalize MAC address: remove colons and convert to lowercase
    mac_clean = re.sub(r'[^0-9a-fA-F]', '', mac_address).lower()
    
    if len(mac_clean) != 12:
        return RedirectResponse(url="/?error=invalid_mac", status_code=303)
    
    # Generate new PSK
    psk = secrets.token_hex(16)
    
    # Register device
    sm.devices[mac_clean] = {
        "name": name,
        "psk": psk,
        "last_nonce": 0,
        "added_at": int(__import__('time').time())
    }
    sm._save_db()
    
    return RedirectResponse(url="/", status_code=303)

@app.post("/delete-device/{device_id}")
async def delete_device(device_id: str):
    if device_id in sm.devices:
        del sm.devices[device_id]
        sm._save_db()
    return RedirectResponse(url="/", status_code=303)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
