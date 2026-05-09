import sqlite3
conn = sqlite3.connect('envmatch.db')
cursor = conn.cursor()
# 切换到国内备用域名 minimaxi.com，并保持 M2.7 标识符
cursor.execute("UPDATE ai_models SET identifier='MiniMax-M2.7', base_url='https://api.minimaxi.com/v1' WHERE provider='MiniMax'")
conn.commit()
print("MiniMax configuration switched to domestic backup (api.minimaxi.com).")
conn.close()
