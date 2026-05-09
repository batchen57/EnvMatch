import sqlite3
import json

conn = sqlite3.connect('envmatch.db')
cursor = conn.cursor()
cursor.execute("SELECT t.id, t.status, tr.error_message, t.input_tokens, t.output_tokens, tr.key_frames_a, tr.key_frames_b FROM tasks t LEFT JOIN task_results tr ON t.id = tr.task_id ORDER BY t.created_at DESC")
rows = cursor.fetchall()
for row in rows:
    tid, status, err, it, ot, kfa, kfb = row
    kfa_list = json.loads(kfa) if kfa else []
    kfb_list = json.loads(kfb) if kfb else []
    print(f"Task: {tid} | Status: {status}")
    if err: print(f"Error: {err}")
    print(f"Tokens: In={it}, Out={ot}")
    print(f"Frames: A={len(kfa_list)}, B={len(kfb_list)}")
    print("-" * 20)
conn.close()
