from fastapi import FastAPI, Request, Form, HTTPException
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from security_manager import SecurityManager
import os

app = FastAPI(title="BlueZcript Management")
templates = Jinja2Templates(directory="templates")
sm = SecurityManager()

# Pairing state (global for simplicity in this version)
pairing_enabled = False

@app.get("/", response_class=HTMLResponse)
async def index(request: Request):
    devices = sm.devices
    return templates.TemplateResponse("index.html", {
        "request": request, 
        "devices": devices,
        "pairing_enabled": pairing_enabled
    })

@app.post("/toggle-pairing")
async def toggle_pairing():
    global pairing_enabled
    pairing_enabled = not pairing_enabled
    return RedirectResponse(url="/", status_code=303)

@app.post("/delete-device/{device_id}")
async def delete_device(device_id: str):
    if device_id in sm.devices:
        del sm.devices[device_id]
        sm._save_db()
    return RedirectResponse(url="/", status_code=303)

@app.post("/add-device-manual")
async def add_manual(device_id: str = Form(...), name: str = Form(...)):
    """Manual addition for testing or headless setups."""
    psk = sm.register_device(device_id, name)
    return {"status": "success", "device_id": device_id, "psk": psk}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
