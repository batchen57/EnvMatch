import sqlite3

conn = sqlite3.connect('backend/envmatch.db')
cursor = conn.cursor()

def get_columns(table):
    cursor.execute(f"PRAGMA table_info({table})")
    return [info[1] for info in cursor.fetchall()]

tables = ['tasks', 'task_results', 'ai_models', 'prompt_templates']
for table in tables:
    print(f"Table {table}: {get_columns(table)}")

conn.close()
