import requests
import base64
import json
import os
import cv2
import glob

API_KEY = "sk-cp-0dpdGj00-MwfOuq9MkypMSI-IjWfcOVIOea_vxwr6b-ZdBXHBKQ6uuT_2cBphh34Vir9tcDzP1ySMRRT2X50yvH8W9rFligDCaDeL7oab-fl3zZg7qKbgJk"
# 搜索结果指出的原生视觉端点
VLM_URL = "https://api.minimaxi.com/v1/coding_plan/vlm"

def test_vlm_native():
    print(f"\n[VLM-TEST] Testing Native VLM Endpoint: {VLM_URL}")
    headers = {"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"}
    
    jpgs = glob.glob("storage/**/*.jpg", recursive=True)
    with open(jpgs[0], "rb") as f:
        b64 = base64.b64encode(f.read()).decode('utf-8')
    
    payload = {
        "prompt": "请描述这张图片中的环境，特别是建筑和地面。",
        "image_url": f"data:image/jpeg;base64,{b64}"
    }
    
    try:
        response = requests.post(VLM_URL, headers=headers, json=payload, timeout=60)
        print(f"Status: {response.status_code}")
        print(f"Response: {response.text[:500]}")
        if response.status_code == 200:
            return True
    except Exception as e:
        print(f"Ex: {e}")
    return False

test_vlm_native()
