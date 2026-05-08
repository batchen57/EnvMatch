import requests
import json
import base64
import os
def test_minimax_anthropic():
    api_key = "sk-cp-0dpdYJ6N70V962KbgJk"
    # The database had this URL
    url = "https://api.minimaxi.com/anthropic/v1/messages"
    
    image_path = os.path.join(os.path.dirname(__file__), "..", "A.png")
    if not os.path.exists(image_path):
        image_path = "A.png"
        
    with open(image_path, "rb") as f:
        b64_img = base64.b64encode(f.read()).decode("utf-8")
    payload = {
        "model": "MiniMax-M2.7",
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "image",
                        "source": {
                            "type": "base64",
                            "media_type": "image/png",
                            "data": b64_img
                        }
                    },
                    {
                        "type": "text",
                        "text": "请分析这张图片的环境细节。"
                    }
                ]
            }
        ],
        "max_tokens": 1024
    }
    headers = {
        "Authorization": f"Bearer {api_key}",
        "anthropic-version": "2023-06-01",
        "Content-Type": "application/json"
    }
    print(f"Calling Anthropic-compatible MiniMax API (Model: MiniMax-M2.7)...")
    response = requests.post(url, headers=headers, json=payload)
    print(f"Status Code: {response.status_code}")
    print(f"Response: {response.text}")
if __name__ == "__main__":
    test_minimax_anthropic()