from fastapi import FastAPI, Request, Form
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from security_manager import SecurityManager
import os

app = FastAPI(title="BlueZcript Management")
templates = Jinja2Templates(directory="templates")
sm = SecurityManager()

# Global state to store the last generated PSK for the user to copy
last_generated_psk = None

@app.get("/", response_class=HTMLResponse)
async def index(request: Request):
    global last_generated_psk
    devices = sm.devices
    psk_to_show = last_generated_psk
    # Reset after showing once to prevent confusion
    last_generated_psk = None
    
    return templates.TemplateResponse("index.html", {
        "request": request, 
        "devices": devices,
        "new_psk": psk_to_show
    })

@app.post("/add-device")
async def add_device(name: str = Form(...)):
    global last_generated_psk
    # Generate a simple unique ID for the device based on its name/timestamp
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

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
