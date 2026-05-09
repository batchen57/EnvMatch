import requests
import base64
import json
import os
import cv2

API_KEY = "sk-cp-0dpdGj00-MwfOuq9MkypMSI-IjWfcOVIOea_vxwr6b-ZdBXHBKQ6uuT_2cBphh34Vir9tcDzP1ySMRRT2X50yvH8W9rFligDCaDeL7oab-fl3zZg7qKbgJk"
# 尝试 abab6.5s-chat 这个公认的多模态标识符
MODEL_ID = "abab6.5s-chat" 
BASE_URL = "https://api.minimaxi.com/v1/chat/completions"

def run_test(name, payload):
    print(f"\n>> Testing: {name}")
    headers = {"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"}
    try:
        response = requests.post(BASE_URL, headers=headers, json=payload, timeout=60)
        if response.status_code == 200:
            res = response.json()
            ans = res['choices'][0]['message']['content']
            print(f"Response: {ans[:300]}")
            if "无法看到" not in ans and "没有提供图片" not in ans and len(ans) > 30:
                print("!!!!!! SUCCESS !!!!!!")
                return True
        else:
            print(f"Error {response.status_code}: {response.text}")
    except Exception as e:
        print(f"Ex: {e}")
    return False

# 准备一个极小的缩略图 (224x224)，排除体积问题
import glob
jpgs = glob.glob("storage/**/*.jpg", recursive=True)
img = cv2.imread(jpgs[0])
img_small = cv2.resize(img, (224, 224))
_, buffer = cv2.imencode(".jpg", img_small)
b64_small = base64.b64encode(buffer).decode('utf-8')

# 测试 1: OpenAI 格式 + abab6.5s-chat + 缩略图
run_test("OpenAI + abab6.5s-chat + Small Image", {
    "model": "abab6.5s-chat",
    "messages": [{
        "role": "user",
        "content": [
            {"type": "text", "text": "描述图片"},
            {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64_small}"}}
        ]
    }]
})

# 测试 2: 某些文档提到的 MiniMax 视觉特定格式 (gly-v1)
# 有些地方说 2.7 视觉版在 OpenAI 兼容路径下需要这个 model 名
run_test("OpenAI + MiniMax-M2.7-Vision", {
    "model": "MiniMax-M2.7",
    "messages": [{
        "role": "user",
        "content": [
            {"type": "text", "text": "图中有什么？"},
            {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64_small}"}}
        ]
    }]
})

# 测试 3: 检查是否需要 base64_image 字段
run_test("Special field 'base64_image'", {
    "model": "abab6.5s-chat",
    "messages": [{
        "role": "user",
        "content": [
            {"type": "text", "text": "描述图片"},
            {"type": "base64_image", "image": b64_small}
        ]
    }]
})
