import sqlite3
conn = sqlite3.connect('envmatch.db')
cursor = conn.cursor()
cursor.execute("SELECT name, identifier, base_url, api_key FROM ai_models WHERE provider='MiniMax'")
row = cursor.fetchone()
if row:
    print(f"Name: {row[0]}")
    print(f"ID: {row[1]}")
    print(f"URL: {row[2]}")
    print(f"Key: {row[3]}")
conn.close()
