import requests
import json

try:
    r = requests.get('http://localhost:8000/dashboard-stats')
    print(json.dumps(r.json(), indent=2))
except Exception as e:
    print(f"Error: {e}")
