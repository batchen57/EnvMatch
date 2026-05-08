import sqlite3
import os

db_path = "backend/envmatch.db"
if os.path.exists(db_path):
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    try:
        cursor.execute("ALTER TABLE ai_models ADD COLUMN capabilities JSON;")
        conn.commit()
        print("Successfully added 'capabilities' column to 'ai_models' table.")
    except sqlite3.OperationalError as e:
        if "duplicate column name" in str(e).lower():
            print("Column 'capabilities' already exists.")
        else:
            print(f"Error: {e}")
    finally:
        conn.close()
else:
    print("Database file not found.")
