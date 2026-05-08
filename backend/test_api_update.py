import requests
import json

uuid = "bb51ed7a-db3d-46c9-a3ce-613912b62fbd"
url = f"http://127.0.0.1:8000/models/{uuid}"
data = {
    "name": "DeepSeek Test",
    "identifier": "deepseek-chat",
    "provider": "DeepSeek",
    "api_key": "test_key",
    "base_url": "https://api.deepseek.com",
    "description": "Updated via script",
    "capabilities": ["text", "image"],
    "is_default": "false",
    "sort_order": 0
}

response = requests.put(url, json=data)
print(f"Status: {response.status_code}")
print(f"Response: {response.json()}")
