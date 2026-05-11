import requests
import json

try:
    r = requests.get('http://localhost:8000/tasks/')
    print(f"Status: {r.status_code}")
    data = r.json()
    print(f"Tasks in list: {len(data['tasks'])}")
    print(f"Total counts: {data['total_counts']}")
except Exception as e:
    print(f"Error: {e}")
