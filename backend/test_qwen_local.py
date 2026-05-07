import os
import json
import base64
from openai import OpenAI
from database import SessionLocal
import models

def encode_video_to_base64(video_path: str) -> str:
    """将本地视频文件读取并转为 Base64 字符串"""
    with open(video_path, "rb") as f:
        return base64.b64encode(f.read()).decode("utf-8")

def test_qwen_vl_plus():
    print("正在从本地数据库读取 Qwen3-VL-Plus 配置...")
    db = SessionLocal()
    api_key = None
    base_url = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    
    try:
        qwen_model = db.query(models.AIModel).filter(models.AIModel.identifier == "qwen-vl-plus").first()
        if qwen_model:
            api_key = qwen_model.api_key
            if qwen_model.base_url:
                base_url = qwen_model.base_url
            print(f"  API Key: {api_key[:8]}****{api_key[-4:]}")
            print(f"  Base URL: {base_url}")
        else:
            print("数据库中未找到 qwen-vl-plus 模型记录")
    finally:
        db.close()
        
    if not api_key:
        print("未能在数据库中找到 Qwen3-VL-Plus 的有效 API Key。")
        print("请确保您已在网页端【模型管理】中配置并保存了该模型的 API Key!")
        return

    model_id = "qwen-vl-plus"
    
    # 使用本地测试视频
    video_a_path = os.path.join(os.path.dirname(__file__), "..", "A.mp4")
    video_b_path = os.path.join(os.path.dirname(__file__), "..", "B.mp4")
    
    if not os.path.exists(video_a_path) or not os.path.exists(video_b_path):
        print(f"测试视频文件不存在: {video_a_path} 或 {video_b_path}")
        return
    
    print(f"\n正在将视频 A 转为 Base64 ({os.path.getsize(video_a_path) / 1024:.0f} KB)...")
    b64_a = encode_video_to_base64(video_a_path)
    print(f"正在将视频 B 转为 Base64 ({os.path.getsize(video_b_path) / 1024:.0f} KB)...")
    b64_b = encode_video_to_base64(video_b_path)

    prompt = """
    你是一个专业的环境场景分析师。我给你提供了两段视频（视频 A 和视频 B）。
    请你完全忽略视频中的主要人物、前景物体和具体动作，将全部注意力放在背景环境上。请从以下维度对比这两个环境的相似度：
    1. 室内/室外属性及天气/光线情况。
    2. 地貌、植被或建筑风格。
    3. 背景中的固定设施或陈设。

    请严格按照以下 JSON 格式输出，不要包含任何额外的 Markdown 格式或文字：
    {
        "similarity_score": 78,
        "dimension_scores": {
            "lighting_weather": 85,
            "architecture": 75,
            "facilities": 70,
            "vegetation": 80,
            "road_surface": 80
        },
        "similar_points": ["相似点1", "相似点2"],
        "difference_points": ["差异点1", "差异点2"],
        "summary": "综合描述..."
    }
    """

    # 使用 video_url 类型直接传入视频，无需抽帧
    content = [
        {
            "type": "video_url",
            "video_url": {
                "url": f"data:video/mp4;base64,{b64_a}"
            }
        },
        {
            "type": "video_url",
            "video_url": {
                "url": f"data:video/mp4;base64,{b64_b}"
            }
        },
        {"type": "text", "text": prompt}
    ]

    print(f"\n正在调用模型: {model_id} (直接传入视频，无需抽帧)")
    print(f"Base URL: {base_url}")
    
    try:
        client = OpenAI(
            api_key=api_key,
            base_url=base_url,
        )
        
        completion = client.chat.completions.create(
            model=model_id,
            messages=[
                {
                    "role": "user",
                    "content": content
                }
            ],
            max_tokens=1000
        )
        
        raw_text = completion.choices[0].message.content
        print("\n=== 模型原始返回结果 ===")
        print(raw_text)
        
        # 尝试解析JSON
        if "```json" in raw_text:
            json_text = raw_text.split("```json")[1].split("```")[0]
        elif "```" in raw_text:
            json_text = raw_text.split("```")[1].split("```")[0]
        else:
            json_text = raw_text
            
        parsed_result = json.loads(json_text.strip())
        print("\n=== 解析成功的 JSON 报告 ===")
        print(json.dumps(parsed_result, indent=2, ensure_ascii=False))
        
        print("\n测试通过! Qwen3-VL-Plus 视频直传调用成功。")
        
    except Exception as e:
        print(f"\n调用失败: {e}")

if __name__ == "__main__":
    test_qwen_vl_plus()
