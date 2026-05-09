import sqlite3
conn = sqlite3.connect('envmatch.db')
cursor = conn.cursor()
cursor.execute("SELECT name, identifier, base_url FROM ai_models")
rows = cursor.fetchall()
for row in rows:
    print(f"Name: {row[0]}, ID: {row[1]}, URL: {row[2]}")
conn.close()