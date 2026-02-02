from fastapi import FastAPI, Request, Form
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from security_manager import SecurityManager
import qrcode
import io
import base64

app = FastAPI(title="BlueZcript Management")
templates = Jinja2Templates(directory="templates")
sm = SecurityManager()

last_generated_psk = None

@app.get("/", response_class=HTMLResponse)
async def index(request: Request):
    global last_generated_psk
    devices = sm.devices
    psk_to_show = last_generated_psk
    last_generated_psk = None
    
    qr_base64 = None
    if psk_to_show:
        # Format: device_id|psk
        qr_data = f"{psk_to_show['id']}|{psk_to_show['psk']}"
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
    global last_generated_psk
    import hashlib
    import time
    device_id = hashlib.md5(f"{name}{time.time()}".encode()).hexdigest()[:12]
    psk = sm.register_device(device_id, name)
    last_generated_psk = {"id": device_id, "psk": psk, "name": name}
    return RedirectResponse(url="/", status_code=303)

@app.post("/delete-device/{device_id}")
async def delete_device(device_id: str):
    if device_id in sm.devices:
        del sm.devices[device_id]
        sm._save_db()
    return RedirectResponse(url="/", status_code=303)
