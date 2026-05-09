import requests
import base64
import json
import os

# 配置信息
API_KEY = "sk-cp-0dpdGj00-MwfOuq9MkypMSI-IjWfcOVIOea_vxwr6b-ZdBXHBKQ6uuT_2cBphh34Vir9tcDzP1ySMRRT2X50yvH8W9rFligDCaDeL7oab-fl3zZg7qKbgJk"
BASE_URL = "https://api.minimaxi.com/v1/chat/completions"
MODEL_ID = "MiniMax-M2.7"

# 找一个现有的测试图片
test_image_path = "storage/ac6005f1-59eb-4512-8172-bd85646ea410_A_frames/perceptual_00000.jpg"
if not os.path.exists(test_image_path):
    # 如果不存在，随便找一个 jpg
    import glob
    jpgs = glob.glob("storage/**/*.jpg", recursive=True)
    if jpgs:
        test_image_path = jpgs[0]
    else:
        print("No test image found. Please run a task first.")
        exit(1)

print(f"Testing with image: {test_image_path}")

with open(test_image_path, "rb") as f:
    b64_data = base64.b64encode(f.read()).decode('utf-8')

payload = {
    "model": MODEL_ID,
    "messages": [
        {
            "role": "user",
            "content": [
                {"type": "text", "text": "请简要描述这张图片的内容。"},
                {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64_data}"}}
            ]
        }
    ],
    "temperature": 0.2
}

headers = {
    "Authorization": f"Bearer {API_KEY}",
    "Content-Type": "application/json"
}

print(f"Sending request to {BASE_URL}...")
try:
    response = requests.post(BASE_URL, headers=headers, json=payload, timeout=60)
    print(f"Status Code: {response.status_code}")
    print("Response Headers:", response.headers)
    print("Response Body (Raw):")
    print(response.text)
    
    if response.status_code == 200:
        try:
            print("\nParsed JSON:")
            print(json.dumps(response.json(), indent=2, ensure_ascii=False))
        except:
            print("\nFailed to parse JSON from 200 response.")
except Exception as e:
    print(f"Request failed: {e}")
