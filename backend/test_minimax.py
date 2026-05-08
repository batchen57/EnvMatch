import os
import json
import base64
from openai import OpenAI
from database import SessionLocal
import models
def encode_image_to_base64(image_path: str) -> str:
    with open(image_path, "rb") as f:
        return base64.b64encode(f.read()).decode("utf-8")
def test_minimax():
    print("正在从本地数据库读取 MiniMax 2.7 配置...")
    db = SessionLocal()
    api_key = None
    identifier = "minimax-2.7"
    
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
    # Using the official Mainland China endpoint found in search
    base_url = "https://api.minimaxi.com/v1"
    # Testing both user's identifier and the standard flagship identifier
    test_models = ["MiniMax-M2.7", "abab6.5s-chat"]
    # In actual usage, it uses the identifier from DB as the model name
    test_models = [identifier, "MiniMax-M2.7", "abab6.5s-chat"]
    
    image_path = os.path.join(os.path.dirname(__file__), "..", "A.png")
    if not os.path.exists(image_path):
        image_path = "A.png"
    
    b64_img = encode_image_to_base64(image_path)
    prompt = "请分析这张图片的环境细节。"
    for model_name in test_models:
        print(f"\n--- 正在尝试模型: {model_name} (BaseURL: {base_url}) ---")
        client = OpenAI(api_key=api_key, base_url=base_url)
        try:
            completion = client.chat.completions.create(
                model=model_name,
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {"type": "text", "text": prompt},
                            {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{b64_img}"}}
                            {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{b64_img}"}},
                            {"type": "text", "text": prompt}
                        ]
                    }
                ],
                max_tokens=500
            )
            print("响应成功:")
            print(completion.choices[0].message.content)
            print(f"Tokens: Input {completion.usage.prompt_tokens}, Output {completion.usage.completion_tokens}")
            break # Success!
            # break # Success!
        except Exception as e:
            print(f"请求失败 ({model_name}): {e}")
if __name__ == "__main__":
    test_minimax()