import requests
import base64
import json
import os
import glob

API_KEY = "sk-cp-0dpdGj00-MwfOuq9MkypMSI-IjWfcOVIOea_vxwr6b-ZdBXHBKQ6uuT_2cBphh34Vir9tcDzP1ySMRRT2X50yvH8W9rFligDCaDeL7oab-fl3zZg7qKbgJk"
BASE_URL = "https://api.minimaxi.com/v1/chat/completions"
MODEL_ID = "MiniMax-M2.7"

def test_format(name, message_obj):
    print(f"\n--- Testing Format: {name} ---")
    payload = {
        "model": MODEL_ID,
        "messages": [message_obj],
        "temperature": 0.2
    }
    headers = {"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"}
    try:
        response = requests.post(BASE_URL, headers=headers, json=payload, timeout=60)
        print(f"Status: {response.status_code}")
        if response.status_code == 200:
            res_json = response.json()
            content = res_json['choices'][0]['message']['content']
            print(f"Response: {content[:200]}...")
            if "无法看到图片" not in content and "没收到图片" not in content and len(content) > 20:
                print(">>> SUCCESS: Model recognized the image!")
                return True
        else:
            print(f"Error: {response.text[:200]}")
    except Exception as e:
        print(f"Failed: {e}")
    return False

# 准备图片
jpgs = glob.glob("storage/**/*.jpg", recursive=True)
with open(jpgs[0], "rb") as f:
    b64_data_raw = base64.b64encode(f.read()).decode('utf-8')

# 格式 4: MiniMax Native Vision Format (images 数组在 message 顶层)
test_format("MiniMax Native (images array)", {
    "role": "user",
    "content": "请详细描述这张图片的内容",
    "images": [b64_data_raw]
})

# 格式 5: 带有 data: 头的 images 数组
test_format("MiniMax Native (images array with data prefix)", {
    "role": "user",
    "content": "请详细描述这张图片的内容",
    "images": [f"data:image/jpeg;base64,{b64_data_raw}"]
})
