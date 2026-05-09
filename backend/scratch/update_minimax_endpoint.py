from database import SessionLocal
import models

def update_minimax_endpoint():
    db = SessionLocal()
    try:
        # 针对 identifier 为 MiniMax-M2.7 的模型更新其 URL
        model = db.query(models.AIModel).filter(models.AIModel.identifier == "MiniMax-M2.7").first()
        if model:
            print(f"发现模型 {model.name} ({model.identifier})")
            new_url = "https://api.minimaxi.com/v1/coding_plan/vlm"
            if model.base_url != new_url:
                model.base_url = new_url
                model.description = "国产优秀大模型，视觉理解能力强，已切换至专用 VLM 端点。"
                db.commit()
                print(f"成功将端点更新为: {new_url}")
            else:
                print("端点已经是最新的。")
        else:
            print("未找到 MiniMax-M2.7 模型配置。")
    except Exception as e:
        print(f"更新失败: {e}")
        db.rollback()
    finally:
        db.close()

if __name__ == "__main__":
    update_minimax_endpoint()
