import sqlite3
import json

conn = sqlite3.connect('envmatch.db')
cursor = conn.cursor()
cursor.execute("SELECT task_id, key_frames_a FROM task_results ORDER BY task_id DESC")
rows = cursor.fetchall()
for row in rows:
    print(f"Task: {row[0]}")
    print(f"Frames: {row[1]}")
    print("-" * 20)
conn.close()
