import os
import base64
import requests
import json
from database import SessionLocal
import models

def test_minimax_from_db():
    db = SessionLocal()
    try:
        cfg = db.query(models.AIModel).filter(models.AIModel.identifier == "MiniMax-M2.7").first()
        if not cfg:
            print("MiniMax model not found in DB")
            return
        
        api_key = cfg.api_key
        base_url = cfg.base_url
        model_id = cfg.identifier
        
        print(f"Model: {model_id}")
        print(f"Base URL: {base_url}")
        print(f"API Key: {api_key[:10]}...")
        
        request_url = base_url
        if not request_url.endswith("/chat/completions"):
            request_url = request_url.rstrip("/") + "/chat/completions"
        
        print(f"Request URL: {request_url}")
        
        # Use a very small dummy image if possible or find one
        import glob
        jpgs = glob.glob("backend/storage/**/*.jpg", recursive=True)
        if not jpgs:
             jpgs = glob.glob("storage/**/*.jpg", recursive=True)
             
        if not jpgs:
            print("No images found to test with")
            return
            
        test_img = jpgs[0]
        print(f"Testing with {test_img}")
        
        with open(test_img, "rb") as f:
            b64_data = base64.b64encode(f.read()).decode('utf-8')
            
        payload = {
            "model": model_id,
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": "Hello, describe this image briefly."},
                        {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64_data}"}}
                    ]
                }
            ],
            "temperature": 0.1
        }
        
        headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
        
        response = requests.post(request_url, headers=headers, json=payload, timeout=30)
        print(f"Status Code: {response.status_code}")
        print(f"Response Text: {response.text[:500]}")
        
        try:
            res = response.json()
            print("Successfully parsed JSON")
        except Exception as e:
            print(f"JSON Parse Error: {e}")
            
    finally:
        db.close()

if __name__ == "__main__":
    test_minimax_from_db()
