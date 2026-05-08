import sys

content = open('backend/main.py', 'r', encoding='utf-8').read()
lines = content.split('\n')
for i, line in enumerate(lines):
    if 'db_template = models.PromptTemplate(name=' in line and 'content=default_content)' in line:
        lines[i] = '        db_template = models.PromptTemplate(name=\"系统默认通用提示\", content=default_content)'
    elif '\"gemini-2.5-pro\"' in line and 'is_default' in line:
        lines[i] = '            {\"name\": \"Gemini 2.5 Pro\", \"identifier\": \"gemini-2.5-pro\", \"provider\": \"Google\", \"api_key\": \"\", \"base_url\": \"\", \"description\": \"多模态理解能力强\", \"capabilities\": [\"text\", \"image\", \"video\"], \"is_default\": \"true\"},'
    elif '\"gpt-4o\"' in line and 'is_default' in line:
        lines[i] = '            {\"name\": \"GPT-4o\", \"identifier\": \"gpt-4o\", \"provider\": \"OpenAI\", \"api_key\": \"\", \"base_url\": \"https://api.openai.com/v1\", \"description\": \"通用性强, 稳定可靠\", \"capabilities\": [\"text\", \"image\", \"video\"], \"is_default\": \"false\"},'
    elif '\"MiniMax-M2.7\"' in line and 'is_default' in line:
        lines[i] = '            {\"name\": \"MiniMax 2.7\", \"identifier\": \"MiniMax-M2.7\", \"provider\": \"MiniMax\", \"api_key\": \"\", \"base_url\": \"https://api.minimaxi.com/v1\", \"description\": \"国产优秀大模型\", \"capabilities\": [\"text\", \"image\"], \"is_default\": \"false\"},'
    elif '\"qwen-vl-plus\"' in line and 'is_default' in line:
        lines[i] = '            {\"name\": \"Qwen3-VL-Plus\", \"identifier\": \"qwen-vl-plus\", \"provider\": \"Alibaba\", \"api_key\": \"\", \"base_url\": \"https://dashscope.aliyuncs.com/compatible-mode/v1\", \"description\": \"视觉理解能力极强\", \"capabilities\": [\"text\", \"image\", \"video\"], \"is_default\": \"false\"}'

open('backend/main.py', 'w', encoding='utf-8').write('\n'.join(lines))
print('Fixed backend/main.py')
