import os
import json
import base64
from openai import OpenAI
from database import SessionLocal
import models

def encode_image_to_base64(image_path: str) -> str:
    with open(image_path, "rb") as f:
        return base64.b64encode(f.read()).decode("utf-8")

def test_glm_qianfan():
    print("正在从本地数据库读取 GLM5.1 (qianfan-code-latest) 配置...")
    db = SessionLocal()
    api_key = None
    base_url = None
    identifier = "qianfan-code-latest"
    
    try:
        model_config = db.query(models.AIModel).filter(models.AIModel.identifier == identifier).first()
        if model_config:
            api_key = model_config.api_key
            base_url = model_config.base_url
            print(f"  API Key: {api_key[:8]}****{api_key[-4:] if len(api_key)>4 else ''}")
            print(f"  Base URL: {base_url}")
        else:
            print(f"数据库中未找到 {identifier} 模型记录")
    finally:
        db.close()
        
    if not api_key:
        print("未能在数据库中找到有效 API Key。")
        return

    # OpenAI client expects the base URL to NOT include /chat/completions
    # Baidu Qianfan V2 OpenAI compatible endpoint is https://qianfan.baidubce.com/v2
    # If the user put https://qianfan.baidubce.com/v2/coding, it might be for a specific API.
    
    client = OpenAI(api_key=api_key, base_url=base_url)
    
    # Use a dummy image from the project
    image_path = os.path.join(os.path.dirname(__file__), "..", "A.png")
    if not os.path.exists(image_path):
        # Try to find any png in the root
        image_path = "A.png"

    if not os.path.exists(image_path):
        print(f"未找到测试图片: {image_path}")
        return

    print(f"正在读取测试图片: {image_path}")
    b64_img = encode_image_to_base64(image_path)
    
    prompt = "请分析这张图片的环境细节。"
    
    print(f"正在发起请求 (Model: {identifier})...")
    try:
        completion = client.chat.completions.create(
            model=identifier,
            messages=[
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": prompt},
                        {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{b64_img}"}}
                    ]
                }
            ],
            max_tokens=500
        )
        print("\n--- 响应结果 ---")
        print(completion.choices[0].message.content)
        print("----------------")
        print(f"Tokens: Input {completion.usage.prompt_tokens}, Output {completion.usage.completion_tokens}")
    except Exception as e:
        print(f"\n请求失败: {e}")

if __name__ == "__main__":
    test_glm_qianfan()
