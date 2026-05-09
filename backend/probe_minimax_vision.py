import requests
import base64
import json
import os
import cv2
import glob

API_KEY = "sk-cp-0dpdGj00-MwfOuq9MkypMSI-IjWfcOVIOea_vxwr6b-ZdBXHBKQ6uuT_2cBphh34Vir9tcDzP1ySMRRT2X50yvH8W9rFligDCaDeL7oab-fl3zZg7qKbgJk"
BASE_URL = "https://api.minimaxi.com/v1/chat/completions"
MODEL_ID = "MiniMax-M2.7"

def try_payload(name, payload):
    print(f"\n[PROBE] Testing Format: {name}")
    headers = {"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"}
    try:
        response = requests.post(BASE_URL, headers=headers, json=payload, timeout=60)
        if response.status_code == 200:
            ans = response.json()['choices'][0]['message']['content']
            print(f"Ans: {ans[:200]}")
            if "无法" not in ans and "没有提供图片" not in ans and "空的" not in ans and len(ans) > 30:
                print(">>> CRITICAL SUCCESS! Use this format.")
                return True
        else:
            print(f"Failed {response.status_code}: {response.text[:200]}")
    except Exception as e:
        print(f"Ex: {e}")
    return False

# 准备极小图片
jpgs = glob.glob("storage/**/*.jpg", recursive=True)
img = cv2.imread(jpgs[0])
img = cv2.resize(img, (256, 256))
_, buf = cv2.imencode(".jpg", img)
b64 = base64.b64encode(buf).decode('utf-8')

# 方案 A: 扁平化 image_url (去掉嵌套 url)
try_payload("Flat image_url", {
    "model": MODEL_ID,
    "messages": [{"role": "user", "content": [
        {"type": "text", "text": "描述图中的物体"},
        {"type": "image_url", "image_url": f"data:image/jpeg;base64,{b64}"}
    ]}]
})

# 方案 B: 使用 images 顶层字段 + model 是 MiniMax-M2.7
try_payload("Top-level images field", {
    "model": MODEL_ID,
    "messages": [{"role": "user", "content": "描述这张图片", "images": [b64]}]
})

# 方案 C: 模仿 Gemini 格式 (parts)
try_payload("Gemini-style parts", {
    "model": MODEL_ID,
    "messages": [{"role": "user", "content": [
        {"text": "描述图片"},
        {"inline_data": {"mime_type": "image/jpeg", "data": b64}}
    ]}]
})

# 方案 D: 某些国产厂商要求的特殊 field
try_payload("Special 'image' field in content", {
    "model": MODEL_ID,
    "messages": [{"role": "user", "content": [
        {"type": "text", "text": "描述图片"},
        {"type": "image", "image": b64}
    ]}]
})
