import os
import cv2
import json
import base64
import requests
import numpy as np
import glob
from typing import List

# 配置
API_KEY = "sk-cp-0dpdGj00-MwfOuq9MkypMSI-IjWfcOVIOea_vxwr6b-ZdBXHBKQ6uuT_2cBphh34Vir9tcDzP1ySMRRT2X50yvH8W9rFligDCaDeL7oab-fl3zZg7qKbgJk"
VLM_URL = "https://api.minimaxi.com/v1/coding_plan/vlm"

def analyze_vlm(fa, fb):
    # 1. 缝合图像
    imgs_a = [cv2.imread(os.path.abspath(f)) for f in fa[:3] if os.path.exists(f)]
    imgs_b = [cv2.imread(os.path.abspath(f)) for f in fb[:3] if os.path.exists(f)]
    
    # 缩放并拼接
    tw, th = 400, 225
    def res(ims): return [cv2.resize(i, (tw, th)) for i in ims if i is not None]
    
    ra = np.hstack(res(imgs_a))
    rb = np.hstack(res(imgs_b))
    grid = np.vstack([ra, rb])
    
    _, buffer = cv2.imencode(".jpg", grid, [cv2.IMWRITE_JPEG_QUALITY, 80])
    b64 = base64.b64encode(buffer).decode('utf-8')
    
    # 2. 调用 VLM
    prompt = """请对比这两组视频帧的环境相似度。第一行是视频A，第二行是视频B。
    请严格按照以下 JSON 格式输出结果：
    {
      "similarity_score": 0-100的数字,
      "summary": "综合分析摘要",
      "dimension_scores": {
        "lighting_weather": 0-100,
        "architecture": 0-100,
        "facilities": 0-100,
        "vegetation": 0-100,
        "road_surface": 0-100
      },
      "similar_points": ["点1", "点2"],
      "difference_points": ["点1", "点2"]
    }
    """
    
    payload = {
        "prompt": prompt,
        "image_url": f"data:image/jpeg;base64,{b64}"
    }
    
    print("Sending VLM request...")
    headers = {"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"}
    response = requests.post(VLM_URL, headers=headers, json=payload, timeout=60)
    print(f"Status: {response.status_code}")
    
    res_text = response.json().get('content', '')
    print("\n--- RAW AI RESPONSE ---")
    print(res_text)
    
    # 解析 JSON
    json_str = res_text
    if "```json" in res_text:
        json_str = res_text.split("```json")[1].split("```")[0].strip()
    elif "```" in res_text:
        json_str = res_text.split("```")[1].split("```")[0].strip()
    
    try:
        final_json = json.loads(json_str)
        print("\n--- FINAL PARSED JSON ---")
        print(json.dumps(final_json, indent=2, ensure_ascii=False))
        return final_json
    except:
        print("\nFailed to parse JSON directly. Attempting to repair...")
        return None

# 执行全流程
print("Starting Full Workflow Local Test...")
# 寻找本地 jpg 文件进行模拟
all_jpgs = glob.glob("storage/**/*.jpg", recursive=True)
if len(all_jpgs) < 6:
    print("Not enough images in storage to test. Please ensure some tasks were run before.")
else:
    fa = all_jpgs[:3]
    fb = all_jpgs[3:6]
    analyze_vlm(fa, fb)
