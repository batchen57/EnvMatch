import os
import sys
import json
from services.task_processor import analyze_with_vlm

# 配置信息
API_KEY = "sk-cp-0dpdGj00-MwfOuq9MkypMSI-IjWfcOVIOea_vxwr6b-ZdBXHBKQ6uuT_2cBphh34Vir9tcDzP1ySMRRT2X50yvH8W9rFligDCaDeL7oab-fl3zZg7qKbgJk"
BASE_URL = "https://api.minimaxi.com/v1/coding_plan/vlm"
MODEL_ID = "MiniMax-M2.7" # 或 "abab6.5s-chat"

def verify_fix():
    print(f"--- 验证 MiniMax 修复方案 ---")
    print(f"模型: {MODEL_ID}")
    print(f"URL: {BASE_URL}")
    
    # 模拟帧路径
    # 寻找一个存在的图片
    test_img = "A.png"
    if not os.path.exists(test_img):
        # 尝试在 backend 下寻找
        test_img = os.path.join("..", "A.png")
    
    if not os.path.exists(test_img):
        # 尝试在 storage 下寻找
        import glob
        jpgs = glob.glob("storage/**/*.jpg", recursive=True)
        if jpgs:
            test_img = jpgs[0]
        else:
            print("错误: 找不到测试图片")
            return

    print(f"使用图片: {test_img}")
    
    # 模拟 frames_a 和 frames_b
    frames_a = [test_img]
    frames_b = [test_img]
    
    prompt = "对比这两张图片的环境相似度。"
    
    try:
        # 调用改进后的 analyze_with_vlm
        # 注意：analyze_with_vlm 内部会处理 MiniMax 特殊格式
        result = analyze_with_vlm(
            frames_a=frames_a,
            frames_b=frames_b,
            prompt=prompt,
            api_key=API_KEY,
            base_url=BASE_URL,
            model_id=MODEL_ID,
            provider="MiniMax"
        )
        
        print("\n--- 分析结果 ---")
        print(json.dumps(result, indent=2, ensure_ascii=False))
        
        if result.get("similarity_score", 0) > 0 and result.get("summary"):
            if "分析失败" not in result.get("summary"):
                print("\n✅ 验证成功: AI 成功输出了结论！")
            else:
                print("\n❌ 验证失败: AI 返回了错误信息")
        else:
            print("\n❌ 验证失败: AI 未返回有效结论")
            
    except Exception as e:
        print(f"\n❌ 验证过程中发生异常: {e}")

if __name__ == "__main__":
    verify_fix()
