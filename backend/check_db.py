import sqlite3
import json
import os

db_path = 'envmatch.db'
if not os.path.exists(db_path):
    print("DB not found")
    exit()

conn = sqlite3.connect(db_path)
cursor = conn.cursor()
cursor.execute("SELECT task_id, key_frames_a FROM task_results WHERE key_frames_a IS NOT NULL LIMIT 5")
rows = cursor.fetchall()

for row in rows:
    print(f"Task: {row[0]}")
    print(f"Data: {row[1]}")
    print("-" * 20)

conn.close()
