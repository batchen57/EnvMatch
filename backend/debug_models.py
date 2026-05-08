import sqlite3
import os

db_path = "backend/envmatch.db"
if os.path.exists(db_path):
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    cursor.execute("SELECT id, name, identifier, capabilities FROM ai_models")
    rows = cursor.fetchall()
    for row in rows:
        print(f"UUID: {row[0]}, Name: {row[1]}, ID: {row[2]}, Capabilities: {row[3]}")
    conn.close()
else:
    print("Database file not found.")
