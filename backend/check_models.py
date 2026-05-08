from database import SessionLocal
import models
import json
def check_models():
    db = SessionLocal()
    try:
        all_models = db.query(models.AIModel).all()
        print(f"Total models in DB: {len(all_models)}")
        for m in all_models:
            print(f"ID: {m.id}")
            print(f"Name: {m.name}")
            print(f"Identifier: {m.identifier}")
            print(f"Provider: {m.provider}")
            print(f"API Key: {m.api_key[:10]}...{m.api_key[-5:] if m.api_key else ''}")
            print(f"Base URL: {m.base_url}")
            print(f"Capabilities: {m.capabilities}")
            print("-" * 20)
    finally:
        db.close()
if __name__ == "__main__":
    check_models()