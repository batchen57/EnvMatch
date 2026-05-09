import requests
import base64
import json
import os

API_KEY = "sk-cp-0dpdGj00-MwfOuq9MkypMSI-IjWfcOVIOea_vxwr6b-ZdBXHBKQ6uuT_2cBphh34Vir9tcDzP1ySMRRT2X50yvH8W9rFligDCaDeL7oab-fl3zZg7qKbgJk"
BASE_URL = "https://api.minimaxi.com/v1/chat/completions"
MODEL_ID = "MiniMax-M2.7"

# 尝试不同的图片注入方式
def test_format(name, payload_content):
    print(f"\n--- Testing Format: {name} ---")
    payload = {
        "model": MODEL_ID,
        "messages": [{"role": "user", "content": payload_content}],
        "temperature": 0.2
    }
    headers = {"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"}
    try:
        response = requests.post(BASE_URL, headers=headers, json=payload, timeout=60)
        print(f"Status: {response.status_code}")
        if response.status_code == 200:
            content = response.json()['choices'][0]['message']['content']
            print(f"Response: {content[:200]}...")
            if "无法看到图片" not in content and "没收到图片" not in content:
                print(">>> SUCCESS: Model recognized the image!")
                return True
        else:
            print(f"Error: {response.text[:200]}")
    except Exception as e:
        print(f"Failed: {e}")
    return False

# 准备图片
import glob
jpgs = glob.glob("storage/**/*.jpg", recursive=True)
with open(jpgs[0], "rb") as f:
    b64_data = base64.b64encode(f.read()).decode('utf-8')

# 格式 1: 标准 OpenAI (之前失败了)
test_format("Standard OpenAI", [
    {"type": "text", "text": "描述这张图片"},
    {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64_data}"}}
])

# 格式 2: 某些国产模型要求的直接 base64
test_format("Direct Base64 String", [
    {"type": "text", "text": "描述这张图片"},
    {"type": "image", "image": b64_data}
])

# 格式 3: 检查是否对 data: 头部敏感
test_format("OpenAI without data: prefix", [
    {"type": "text", "text": "描述这张图片"},
    {"type": "image_url", "image_url": {"url": b64_data}}
])
