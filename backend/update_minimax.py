from database import SessionLocal
import models
def update_minimax_config():
    db = SessionLocal()
    try:
        # Update MiniMax 2.7 configuration
        identifier = "minimax-2.7"
        model = db.query(models.AIModel).filter(models.AIModel.identifier == identifier).first()
        if model:
            print(f"Updating configuration for {model.name}...")
            # Changing base_url to the OpenAI-compatible one
            model.base_url = "https://api.minimaxi.com/v1"
            # Changing identifier to the actual model name required by the API
            # model.identifier = "MiniMax-M2.7" 
            # Actually, let's keep identifier but maybe the code should use a different field for the API model name?
            # Looking at models.py, there isn't a separate field for 'api_model_name'.
            # So identifier IS the model name passed to the API.
            model.identifier = "MiniMax-M2.7"
            db.commit()
            print("Update successful.")
        else:
            print(f"Model with identifier {identifier} not found.")
    except Exception as e:
        print(f"Error: {e}")
        db.rollback()
    finally:
        db.close()
if __name__ == "__main__":
    update_minimax_config()
