import sqlite3
import os

db_path = 'backend/envmatch.db'
if not os.path.exists(db_path):
    print("DB not found")
else:
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    cursor.execute("SELECT count(*) FROM tasks")
    print(f"Total Tasks: {cursor.fetchone()[0]}")
    cursor.execute("SELECT created_at FROM tasks ORDER BY created_at DESC LIMIT 5")
    print(f"Recent created_at: {cursor.fetchall()}")
    conn.close()
