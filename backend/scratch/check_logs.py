from database import SessionLocal
import models

db = SessionLocal()
count = db.query(models.ModelCallLog).count()
print(f"Total ModelCallLogs: {count}")

latest = db.query(models.ModelCallLog).order_by(models.ModelCallLog.started_at.desc()).first()
if latest:
    print(f"Latest Log: Task {latest.task_name}, Model {latest.model_id}, Time {latest.started_at}")
else:
    print("No logs found.")
db.close()
