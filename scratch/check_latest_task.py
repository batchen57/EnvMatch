import sqlite3
import json

db_path = r'd:\WorkSpace\bak\EnvMatch\backend\envmatch.db'
conn = sqlite3.connect(db_path)
conn.row_factory = sqlite3.Row
cursor = conn.cursor()
cursor.execute("SELECT * FROM tasks ORDER BY updated_at DESC LIMIT 1")
row = cursor.fetchone()
if row:
    print(json.dumps(dict(row), indent=2))
conn.close()
