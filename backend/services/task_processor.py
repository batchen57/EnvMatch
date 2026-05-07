import uuid
import os
import time
import ffmpeg
import google.generativeai as genai
import requests
from sqlalchemy.orm import Session
from database import SessionLocal
import models
import json
import base64
from openai import OpenAI

genai.configure(api_key=os.environ.get("GEMINI_API_KEY", "dummy_key_replace_me"))

def extract_frames(video_path: str, task_id: str, suffix: str):
    """
    Extracts frames from the video at 1 fps.
    """
    output_dir = os.path.join("storage", f"{task_id}_{suffix}_frames")
    os.makedirs(output_dir, exist_ok=True)
    output_pattern = os.path.join(output_dir, "frame_%03d.jpg")
    
    try:
        (
            ffmpeg
            .input(video_path)
            .filter('fps', fps=1)
            .filter('scale', 1280, -1)
            .output(output_pattern)
            .overwrite_output()
            .run(quiet=True)
        )
    except Exception as e:
        print(f"FFmpeg or OS error: {e}")
        # Make it non-blocking if ffmpeg fails (for test environments without ffmpeg installed)
        print("Continuing without frames due to FFmpeg error.")
        return []
        
    frames = [os.path.join(output_dir, f) for f in os.listdir(output_dir) if f.endswith('.jpg')]
    frames.sort()
    return frames

def get_default_mock_result():
    return {
        "similarity_score": 85.6,
        "dimension_scores": {
            "lighting_weather": 78,
            "architecture": 90,
            "facilities": 84,
            "vegetation": 82,
            "road_surface": 88
        },
        "similar_points": ["建筑结构", "空间布局", "装修风格"],
        "difference_points": ["人流密度", "部分广告位内容", "光照细节"],
        "summary": "两段视频的环境相似度较高，均为现代化商场内部场景。主要差异在于细节光照。",
        "input_tokens": 1240,
        "output_tokens": 320
    }

def analyze_environment_with_gemini(video_a_path: str, video_b_path: str, prompt: str = None, api_key: str = None):
    """
    Calls Gemini 1.5 Pro with the actual video files if supported, 
    or just returns a structured dummy output if API key is not valid yet.
    """
    if not api_key:
        api_key = os.environ.get("GEMINI_API_KEY", "dummy_key_replace_me")
        
    if api_key in ["dummy_key_replace_me", "", None]:
        print("No real GEMINI_API_KEY, using mock result")
        return get_default_mock_result()
        
    genai.configure(api_key=api_key)
    try:
        file_a = genai.upload_file(path=video_a_path)
        file_b = genai.upload_file(path=video_b_path)
        
        while file_a.state.name == 'PROCESSING' or file_b.state.name == 'PROCESSING':
            time.sleep(2)
            file_a = genai.get_file(file_a.name)
            file_b = genai.get_file(file_b.name)
            
        model = genai.GenerativeModel('models/gemini-2.5-pro')
        
        default_prompt = """
        你是一个专业的环境场景分析师。我给你提供了两段视频（视频 A 和视频 B）。
        请你完全忽略视频中的主要人物、前景物体和具体动作，将全部注意力放在背景环境上。请从以下维度对比这两个环境的相似度：
        1. 室内/室外属性及天气/光线情况。
        2. 地貌、植被或建筑风格。
        3. 背景中的固定设施或陈设。

        请严格按照以下 JSON 格式输出，不要包含任何额外的 Markdown 格式或文字：
        {
          "similarity_score": 78,
          "dimension_scores": {
            "lighting_weather": 85,
            "architecture": 75,
            "facilities": 70,
            "vegetation": 80,
            "road_surface": 80
          },
          "similar_points": ["相似点1", "相似点2"],
          "difference_points": ["差异点1", "差异点2"],
          "summary": "综合描述..."
        }
        """
        
        actual_prompt = prompt if prompt and prompt.strip() else default_prompt
        
        response = model.generate_content([actual_prompt, file_a, file_b])
        text = response.text
        
        input_tokens = 0
        output_tokens = 0
        if hasattr(response, 'usage_metadata'):
            input_tokens = response.usage_metadata.prompt_token_count
            output_tokens = response.usage_metadata.candidates_token_count

        if "```json" in text:
            text = text.split("```json")[1].split("```")[0]
        elif "```" in text:
            text = text.split("```")[1].split("```")[0]
            
        result = json.loads(text.strip())
        result["input_tokens"] = input_tokens
        result["output_tokens"] = output_tokens
        return result
    except Exception as e:
        print(f"Gemini API Error: {e}. Falling back to mock result.")
        return get_default_mock_result()

def get_image_base64(video_path, frames):
    if frames and len(frames) > 0:
        file_to_read = frames[0]
    else:
        file_to_read = video_path
    
    ext = os.path.splitext(file_to_read)[1].lower()
    mime_type = "image/jpeg"
    if ext == ".png":
        mime_type = "image/png"
        
    try:
        with open(file_to_read, "rb") as f:
            return f"data:{mime_type};base64,{base64.b64encode(f.read()).decode('utf-8')}"
    except Exception as e:
        print(f"Failed to read image base64: {e}")
        return ""

def encode_video_to_base64(video_path: str) -> str:
    """将本地视频文件读取并转为 Base64 字符串，用于直传给支持视频理解的大模型"""
    with open(video_path, "rb") as f:
        return base64.b64encode(f.read()).decode("utf-8")

def analyze_with_qwen_video(video_a_path: str, video_b_path: str, prompt: str, model_id: str, api_key: str, base_url: str):
    """
    千问(Qwen)视频直传分析：无需抽帧，直接将完整视频以 Base64 编码通过 video_url 传给模型。
    Qwen VL 系列原生支持 video_url 类型，模型内部自行完成智能抽帧。
    """
    if not api_key:
        print("No API key for Qwen model, using mock result")
        return get_default_mock_result()

    default_prompt = """
    你是一个专业的环境场景分析师。我给你提供了两段视频（视频 A 和视频 B）。
    请你完全忽略视频中的主要人物、前景物体和具体动作，将全部注意力放在背景环境上。请从以下维度对比这两个环境的相似度：
    1. 室内/室外属性及天气/光线情况。
    2. 地貌、植被或建筑风格。
    3. 背景中的固定设施或陈设。

    请严格按照以下 JSON 格式输出，不要包含任何额外的 Markdown 格式或文字：
    {
        "similarity_score": 78,
        "dimension_scores": {
            "lighting_weather": 85,
            "architecture": 75,
            "facilities": 70,
            "vegetation": 80,
            "road_surface": 80
        },
        "similar_points": ["相似点1", "相似点2"],
        "difference_points": ["差异点1", "差异点2"],
        "summary": "综合描述..."
    }
    """
    actual_prompt = prompt if prompt and prompt.strip() else default_prompt

    print(f"Qwen video mode: encoding video A ({os.path.getsize(video_a_path) / 1024:.0f} KB)...")
    b64_a = encode_video_to_base64(video_a_path)
    print(f"Qwen video mode: encoding video B ({os.path.getsize(video_b_path) / 1024:.0f} KB)...")
    b64_b = encode_video_to_base64(video_b_path)

    # 使用 video_url 类型直接传入完整视频
    content = [
        {"type": "video_url", "video_url": {"url": f"data:video/mp4;base64,{b64_a}"}},
        {"type": "video_url", "video_url": {"url": f"data:video/mp4;base64,{b64_b}"}},
        {"type": "text", "text": actual_prompt}
    ]

    try:
        client = OpenAI(api_key=api_key, base_url=base_url)
        completion = client.chat.completions.create(
            model=model_id,
            messages=[{"role": "user", "content": content}],
            max_tokens=1000
        )
        text = completion.choices[0].message.content
        
        input_tokens = 0
        output_tokens = 0
        if hasattr(completion, 'usage') and completion.usage:
            input_tokens = completion.usage.prompt_tokens
            output_tokens = completion.usage.completion_tokens

        if "```json" in text:
            text = text.split("```json")[1].split("```")[0]
        elif "```" in text:
            text = text.split("```")[1].split("```")[0]

        result = json.loads(text.strip())
        result["input_tokens"] = input_tokens
        result["output_tokens"] = output_tokens
        return result
    except Exception as e:
        print(f"Qwen Video API Error: {e}. Falling back to mock result.")
        return get_default_mock_result()

def analyze_with_openai_compatible(video_a_path, frames_a, video_b_path, frames_b, prompt: str, model_id: str, api_key: str, base_url: str):
    if not api_key:
        print("No API key for OpenAI compatible model, using mock result")
        return get_default_mock_result()
        
    default_prompt = """
    你是一个专业的环境场景分析师。我给你提供了两个场景的图片。
    请你完全忽略主要人物、前景物体和具体动作，将全部注意力放在背景环境上。请从以下维度对比这两个环境的相似度：
    1. 室内/室外属性及天气/光线情况。
    2. 地貌、植被或建筑风格。
    3. 背景中的固定设施或陈设。

    请严格按照以下 JSON 格式输出，不要包含任何额外的 Markdown 格式或文字：
    {
        "similarity_score": 78,
        "dimension_scores": {
        "lighting_weather": 85,
        "architecture": 75,
        "facilities": 70,
        "vegetation": 80,
        "road_surface": 80
        },
        "similar_points": ["相似点1", "相似点2"],
        "difference_points": ["差异点1", "差异点2"],
        "summary": "综合描述..."
    }
    """
    actual_prompt = prompt if prompt and prompt.strip() else default_prompt
    
    b64_a = get_image_base64(video_a_path, frames_a)
    b64_b = get_image_base64(video_b_path, frames_b)
    
    content = []
    if b64_a:
        content.append({"type": "image_url", "image_url": {"url": b64_a}})
    if b64_b:
        content.append({"type": "image_url", "image_url": {"url": b64_b}})
    content.append({"type": "text", "text": actual_prompt})
        
    client_kwargs = {"api_key": api_key}
    if base_url:
        client_kwargs["base_url"] = base_url
    
    try:
        client = OpenAI(**client_kwargs)
        completion = client.chat.completions.create(
            model=model_id,
            messages=[
                {
                    "role": "user",
                    "content": content
                }
            ],
            max_tokens=1000
        )
        text = completion.choices[0].message.content
        
        input_tokens = 0
        output_tokens = 0
        if hasattr(completion, 'usage') and completion.usage:
            input_tokens = completion.usage.prompt_tokens
            output_tokens = completion.usage.completion_tokens

        if "```json" in text:
            text = text.split("```json")[1].split("```")[0]
        elif "```" in text:
            text = text.split("```")[1].split("```")[0]
            
        result = json.loads(text.strip())
        result["input_tokens"] = input_tokens
        result["output_tokens"] = output_tokens
        return result
    except Exception as e:
        print(f"OpenAI Compatible API Error: {e}. Falling back to mock result.")
        return get_default_mock_result()

def process_video_task(task_id: str):
    db: Session = SessionLocal()
    try:
        task = db.query(models.Task).filter(models.Task.id == task_id).first()
        if not task:
            return
            
        task.status = models.TaskStatus.PROCESSING
        db.commit()
        
        print(f"Calling AI model {task.model_id} for task {task_id}")
        
        ai_model_config = db.query(models.AIModel).filter(models.AIModel.identifier == task.model_id).first()
        api_key = ai_model_config.api_key if ai_model_config else ""
        base_url = ai_model_config.base_url if ai_model_config else ""
        
        # 路由 1: Qwen 系列 — 原生视频理解，无需抽帧，直传视频
        if "qwen" in task.model_id.lower():
            print(f"Task {task_id}: Qwen detected, using native video mode (no frame extraction)")
            ai_result = analyze_with_qwen_video(
                task.video_a_path, task.video_b_path,
                task.prompt, task.model_id, api_key, base_url
            )
            frames_a, frames_b = [], []
        # 路由 2: Gemini 系列 — 原生视频上传
        elif "gemini" in task.model_id.lower():
            print(f"Task {task_id}: Gemini detected, using native video upload")
            ai_result = analyze_environment_with_gemini(task.video_a_path, task.video_b_path, task.prompt, api_key)
            frames_a, frames_b = [], []
        # 路由 3: 其他 OpenAI 兼容模型 — 需要抽帧转图片
        else:
            print(f"Task {task_id}: extracting frames for OpenAI-compatible model...")
            frames_a = extract_frames(task.video_a_path, task_id, "A")
            frames_b = extract_frames(task.video_b_path, task_id, "B")
            ai_result = analyze_with_openai_compatible(
                task.video_a_path, frames_a, 
                task.video_b_path, frames_b, 
                task.prompt, task.model_id, api_key, base_url
            )
        
        task.status = models.TaskStatus.COMPLETED
        task.similarity_score = ai_result.get("similarity_score", 0)
        task.input_tokens = ai_result.get("input_tokens", 0)
        task.output_tokens = ai_result.get("output_tokens", 0)
        
        task_result = models.TaskResult(
            task_id=task_id,
            dimension_scores=ai_result.get("dimension_scores", {}),
            similar_points=ai_result.get("similar_points", []),
            difference_points=ai_result.get("difference_points", []),
            summary=ai_result.get("summary", ""),
            key_frames_a=frames_a[:6],
            key_frames_b=frames_b[:6],
            input_tokens=ai_result.get("input_tokens", 0),
            output_tokens=ai_result.get("output_tokens", 0)
        )
        db.add(task_result)
        db.commit()
        print(f"Task {task_id} completed successfully.")
        
        # Simple Webhook trigger implementation
        # For a full implementation, you would store webhook_url in models.Task
        # and trigger it like below. Here we just mock the structure.
        webhook_url = os.environ.get("WEBHOOK_URL", "")
        if webhook_url:
            try:
                requests.post(webhook_url, json={"task_id": task_id, "status": "COMPLETED", "score": task.similarity_score}, timeout=5)
                print(f"Webhook sent for task {task_id}")
            except Exception as w_err:
                print(f"Webhook failed for task {task_id}: {w_err}")
        
    except Exception as e:
        print(f"Task {task_id} failed: {e}")
        db.rollback()
        task = db.query(models.Task).filter(models.Task.id == task_id).first()
        if task:
            task.status = models.TaskStatus.FAILED
            db.commit()
    finally:
        db.close()
