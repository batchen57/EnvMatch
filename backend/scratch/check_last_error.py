from database import SessionLocal
import models

def check_last_error():
    db = SessionLocal()
    try:
        t = db.query(models.Task).order_by(models.Task.created_at.desc()).first()
        if t:
            print(f"Task ID: {t.id}")
            print(f"Status: {t.status}")
            tr = db.query(models.TaskResult).filter(models.TaskResult.task_id == t.id).first()
            if tr:
                print(f"Error Message: {tr.error_message}")
                print(f"Summary: {tr.summary}")
            else:
                print("No TaskResult found")
        else:
            print("No tasks found")
    finally:
        db.close()

if __name__ == "__main__":
    check_last_error()
