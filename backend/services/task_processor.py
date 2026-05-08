import uuid
import os
import shutil
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
import cv2
import numpy as np
from scenedetect import SceneManager, open_video, ContentDetector, AdaptiveDetector
import re
genai.configure(api_key=os.environ.get("GEMINI_API_KEY", "dummy_key_replace_me"))
def extract_json_from_text(text: str) -> dict:
    """
    Robustly extracts JSON from a potentially messy text response.
    """
    # 1. Try to find JSON block using regex
    json_pattern = r'\{.*\}'
    match = re.search(json_pattern, text, re.DOTALL)
    if match:
        json_str = match.group(0)
        try:
            return json.loads(json_str.strip())
        except:
            pass
            
    # 2. Try markdown code block fallback
    if "```json" in text:
        text = text.split("```json")[1].split("```")[0]
    elif "```" in text:
        text = text.split("```")[1].split("```")[0]
        
    try:
        return json.loads(text.strip())
    except Exception as e:
        print(f"JSON extraction failed for text: {text[:200]}...")
        raise ValueError(f"Could not parse JSON from model response: {e}")
def extract_frames(video_path: str, task_id: str, suffix: str, fps: int = 1, resolution: int = 720, denoise: bool = False, sampling_type: str = "fixed"):
    """
    Extracts frames from the video or handle images.
    Resolution is treated as target height (e.g. 720 = 720p).
    sampling_type can be "fixed" (fps based) or "perceptual" (scene change based).
    """
    output_dir = os.path.join("storage", f"{task_id}_{suffix}_frames")
    os.makedirs(output_dir, exist_ok=True)
    
    # Handle image inputs
    ext = os.path.splitext(video_path)[1].lower()
    if ext in ['.jpg', '.jpeg', '.png', '.bmp', '.webp']:
        target = os.path.join(output_dir, f"frame_001{ext}")
        shutil.copy2(video_path, target)
        return [target]
    output_pattern = os.path.join(output_dir, "frame_%03d.jpg")
    try:
        # Use simpler filter chain for better reliability
        if sampling_type == "perceptual":
            # Call specialized perceptual extractor
            return extract_perceptual_frames(video_path, output_dir, resolution)
        else:
            filter_str = f"fps={fps},scale=-1:{resolution}"
            
        if denoise:
            filter_str += ",hqdn3d"
            
        (
            ffmpeg
            .input(video_path)
            .filter_complex(filter_str)
            .output(output_pattern, vsync='vfr')
            .overwrite_output()
            .run(quiet=True, capture_stdout=True, capture_stderr=True)
        )
    except Exception as e:
        print(f"Error extracting frames from {video_path}: {e}")
        # Fallback to simple fps extraction if complex filter fails
        try:
            (
                ffmpeg
                .input(video_path)
                .filter('fps', fps=fps)
                .filter('scale', -1, resolution)
                .output(output_pattern)
                .overwrite_output()
                .run(quiet=True, capture_stdout=True, capture_stderr=True)
            )
        except:
            pass
        
    frames = []
    if os.path.exists(output_dir):
        for f in os.listdir(output_dir):
            if f.endswith(('.jpg', '.png')):
                # Normalize to forward slashes for web compatibility
                rel_path = os.path.join(output_dir, f).replace("\\", "/")
                frames.append(rel_path)
    
    frames.sort()
    return frames
def extract_perceptual_frames(video_path: str, output_dir: str, resolution: int = 720):
    """
    高级感知抽帧方案：
    1. PySceneDetect (ContentDetector + AdaptiveDetector)：捕获场景剧变（如突然出现的物体、镜头切换）。
    2. 稀疏光流法 (Lucas-Kanade)：追踪摄像机移动，解决极慢速扫摄下的细节遗漏。
    """
    frames = []
    
    # 1. PySceneDetect 检测剧烈变化
    print(f"[Perceptual] Running Scene Detection on {video_path}...")
    try:
        video = open_video(video_path)
        scene_manager = SceneManager()
        # ContentDetector 对像素剧变敏感，AdaptiveDetector 对渐变/光照变化更鲁棒
        scene_manager.add_detector(ContentDetector(threshold=27.0))
        scene_manager.add_detector(AdaptiveDetector(adaptive_threshold=3.0))
        
        scene_manager.detect_scenes(video, show_progress=False)
        scene_list = scene_manager.get_scene_list()
        # 记录场景开始帧
        scene_frame_indices = [scene[0].get_frames() for scene in scene_list]
        print(f"[Perceptual] Found {len(scene_frame_indices)} scenes via PySceneDetect.")
    except Exception as e:
        print(f"[Perceptual] Scene detection failed: {e}")
        scene_frame_indices = []
    # 2. 光流法追踪摄像机位移
    print(f"[Perceptual] Running Optical Flow for motion tracking...")
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        return []
    
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    # 位移阈值：画面宽度的 30%
    threshold_px = width * 0.3
    
    # 获取第一帧作为起始
    ret, prev_frame = cap.read()
    if not ret:
        cap.release()
        return []
    
    prev_gray = cv2.cvtColor(prev_frame, cv2.COLOR_BGR2GRAY)
    # 使用 goodFeaturesToTrack 寻找特征点进行稀疏光流追踪
    lk_params = dict(winSize=(15, 15), maxLevel=2, criteria=(cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT, 10, 0.03))
    p0 = cv2.goodFeaturesToTrack(prev_gray, mask=None, maxCorners=100, qualityLevel=0.3, minDistance=7, blockSize=7)
    
    forced_indices = {0} # 默认包含首帧
    accumulated_motion = 0.0
    frame_idx = 1
    
    # 为了性能，可以跳帧检测位移，但为了精准度，我们逐帧计算
    while True:
        ret, frame = cap.read()
        if not ret:
            break
            
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        
        if p0 is not None and len(p0) > 0:
            # 计算稀疏光流
            p1, st, err = cv2.calcOpticalFlowPyrLK(prev_gray, gray, p0, None, **lk_params)
            
            if p1 is not None and len(p1[st == 1]) > 0:
                good_new = p1[st == 1]
                good_old = p0[st == 1]
                
                # 计算特征点的平均位移
                # 这里我们主要关注水平位移（扫摄），也可以计算欧几里得距离
                diff = good_new - good_old
                avg_dx = np.mean(diff[:, 0])
                avg_dy = np.mean(diff[:, 1])
                
                # 累计位移距离
                step_dist = np.sqrt(avg_dx**2 + avg_dy**2)
                accumulated_motion += step_dist
                
                # 达到 30% 阈值，记录帧索引并重置
                if accumulated_motion >= threshold_px:
                    forced_indices.add(frame_idx)
                    accumulated_motion = 0.0
                    # 重新寻找特征点，防止点跑出画面
                    p0 = cv2.goodFeaturesToTrack(gray, mask=None, maxCorners=100, qualityLevel=0.3, minDistance=7, blockSize=7)
                else:
                    p0 = good_new.reshape(-1, 1, 2)
            else:
                # 追踪丢失，重新寻找特征点
                p0 = cv2.goodFeaturesToTrack(gray, mask=None, maxCorners=100, qualityLevel=0.3, minDistance=7, blockSize=7)
        else:
            p0 = cv2.goodFeaturesToTrack(gray, mask=None, maxCorners=100, qualityLevel=0.3, minDistance=7, blockSize=7)
            
        prev_gray = gray
        frame_idx += 1
        
        # 兜底：如果采样点过多（例如超过100帧），说明阈值可能不合适或视频极长，进行截断
        if len(forced_indices) > 100:
            break
    cap.release()
    print(f"[Perceptual] Optical Flow generated {len(forced_indices)} mandatory frames.")
    
    # 合并所有关键帧索引
    all_indices = sorted(list(forced_indices.union(set(scene_frame_indices))))
    
    # 针对 VLM 优化：如果帧数过多（通常 > 20 会导致处理慢且成本高），进行等距采样
    max_vlm_frames = 20
    if len(all_indices) > max_vlm_frames:
        print(f"[Perceptual] Reducing {len(all_indices)} frames to {max_vlm_frames} for VLM efficiency.")
        step = len(all_indices) / max_vlm_frames
        selected_indices = [all_indices[int(i * step)] for i in range(max_vlm_frames)]
    else:
        selected_indices = all_indices
        
    # 提取并保存帧图片
    cap = cv2.VideoCapture(video_path)
    for idx in selected_indices:
        cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
        ret, frame = cap.read()
        if ret:
            # 统一调整分辨率
            h, w = frame.shape[:2]
            new_h = resolution
            new_w = int(w * (new_h / h))
            frame_resized = cv2.resize(frame, (new_w, new_h), interpolation=cv2.INTER_AREA)
            
            output_path = os.path.join(output_dir, f"perceptual_{idx:05d}.jpg")
            cv2.imwrite(output_path, frame_resized, [int(cv2.IMWRITE_JPEG_QUALITY), 90])
            frames.append(output_path.replace("\\", "/"))
    cap.release()
    
    return sorted(frames)
def preprocess_video(video_path: str, task_id: str, suffix: str, resolution: int = 720, denoise: bool = False):
    """
    Creates a compressed/resized version of the video.
    Resolution is treated as target height (e.g. 720 = 720p).
    """
    output_path = os.path.join("storage", f"{task_id}_{suffix}_processed.mp4")
    try:
        stream = ffmpeg.input(video_path)
        if denoise:
            stream = stream.filter('scale', -1, resolution).filter('hqdn3d')
        else:
            stream = stream.filter('scale', -1, resolution)
        (
            stream
            .output(output_path, vcodec='libx264', crf=23, preset='fast')
            .overwrite_output()
            .run(quiet=True, capture_stdout=True, capture_stderr=True)
        )
        return output_path
    except ffmpeg.Error as e:
        print(f"FFmpeg error preprocessing video {video_path}: {e.stderr.decode() if e.stderr else str(e)}")
        return video_path
    except Exception as e:
        print(f"Error preprocessing video {video_path}: {e}")
        return video_path
def get_video_metadata(video_path: str):
    """
    Extracts duration, resolution and size from video file.
    """
    try:
        probe = ffmpeg.probe(video_path)
        video_stream = next((stream for stream in probe['streams'] if stream['codec_type'] == 'video'), None)
        duration = float(probe.get('format', {}).get('duration', 0))
        width = int(video_stream.get('width', 0)) if video_stream else 0
        height = int(video_stream.get('height', 0)) if video_stream else 0
        size_bytes = os.path.getsize(video_path)
        
        return {
            "duration": duration,
            "resolution": f"{width}x{height}",
            "size_mb": round(size_bytes / (1024 * 1024), 2)
        }
    except Exception as e:
        print(f"Error probing video {video_path}: {e}")
        return {
            "duration": 0,
            "resolution": "0x0",
            "size_mb": round(os.path.getsize(video_path) / (1024 * 1024), 2) if os.path.exists(video_path) else 0
        }
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
        api_key = os.environ.get("GEMINI_API_KEY")
        
    if not api_key or api_key == "dummy_key_replace_me":
        raise ValueError("Missing Gemini API Key. Please configure it in Model Management.")
        
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
        result = extract_json_from_text(text)
        result["input_tokens"] = input_tokens
        result["output_tokens"] = output_tokens
        return result
    except Exception as e:
        print(f"Gemini API Error: {e}")
        raise e
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
        raise ValueError(f"Missing API Key for model {model_id}. Please configure it in Model Management.")
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
        result = extract_json_from_text(text)
        result["input_tokens"] = input_tokens
        result["output_tokens"] = output_tokens
        return result
    except Exception as e:
        print(f"Qwen Video API Error: {e}")
        raise e
def analyze_with_openai_compatible(video_a_path, frames_a, video_b_path, frames_b, prompt: str, model_id: str, api_key: str, base_url: str):
    if not api_key:
        raise ValueError(f"Missing API Key for model {model_id}. Please configure it in Model Management.")
        
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
        result = extract_json_from_text(text)
        result["input_tokens"] = input_tokens
        result["output_tokens"] = output_tokens
        return result
    except Exception as e:
        print(f"OpenAI Compatible API Error: {e}")
        raise e
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
        
        options = task.preprocess_options or {}
        recognition_mode = options.get("recognition_mode", "image")
        sampling_type = options.get("sampling_type", "fixed")
        fps = options.get("sampling_fps", 1)
        res = options.get("resolution_val", 720) if options.get("resolution") else 720
        denoise = options.get("denoise", False)
        
        # Real Video Preprocessing (Compression/Scaling)
        if options.get("resolution") or options.get("denoise") or options.get("format_convert"):
            print(f"Task {task_id}: performing real video preprocessing (Res:{res}, Denoise:{denoise})...")
            task.video_a_path = preprocess_video(task.video_a_path, task_id, "A", res, denoise)
            task.video_b_path = preprocess_video(task.video_b_path, task_id, "B", res, denoise)
            
            # Update metadata after preprocessing
            meta_a = get_video_metadata(task.video_a_path)
            meta_b = get_video_metadata(task.video_b_path)
            task.video_a_duration = meta_a["duration"]
            task.video_b_duration = meta_b["duration"]
            task.video_a_resolution = meta_a["resolution"]
            task.video_b_resolution = meta_b["resolution"]
            task.video_a_size = meta_a["size_mb"]
            task.video_b_size = meta_b["size_mb"]
            db.commit()
        # Always extract frames for UI display
        print(f"Task {task_id}: extracting frames (Mode:{recognition_mode}, Type:{sampling_type}, FPS:{fps}, Res:{res})...")
        frames_a = extract_frames(task.video_a_path, task_id, "A", fps, res, denoise, sampling_type)
        frames_b = extract_frames(task.video_b_path, task_id, "B", fps, res, denoise, sampling_type)
        # 路由选择：如果是视频模式，且模型支持视频（Qwen/Gemini），则走视频路由
        # 否则走图片抽帧路由
        if recognition_mode == "video":
            if "qwen" in task.model_id.lower():
                print(f"Task {task_id}: Qwen video mode")
                ai_result = analyze_with_qwen_video(
                    task.video_a_path, task.video_b_path,
                    task.prompt, task.model_id, api_key, base_url
                )
            elif "gemini" in task.model_id.lower():
                print(f"Task {task_id}: Gemini video mode")
                ai_result = analyze_environment_with_gemini(task.video_a_path, task.video_b_path, task.prompt, api_key)
            else:
                # Fallback to image mode if model doesn't support video
                print(f"Task {task_id}: Model {task.model_id} doesn't support video, falling back to image mode")
                ai_result = analyze_with_openai_compatible(
                    task.video_a_path, frames_a, 
                    task.video_b_path, frames_b, 
                    task.prompt, task.model_id, api_key, base_url
                )
        else:
            # 抽帧图片识别模式
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
            key_frames_a=frames_a,
            key_frames_b=frames_b,
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
        error_msg = str(e)
        print(f"Task {task_id} failed: {error_msg}")
        db.rollback()
        
        # Update task status
        task = db.query(models.Task).filter(models.Task.id == task_id).first()
        if task:
            task.status = models.TaskStatus.FAILED
            
            # Create a TaskResult record even for failed tasks to store error info
            # Check if result already exists
            existing_result = db.query(models.TaskResult).filter(models.TaskResult.task_id == task_id).first()
            if not existing_result:
                task_result = models.TaskResult(
                    task_id=task_id,
                    error_message=error_msg,
                    summary="分析任务执行失败"
                )
                db.add(task_result)
            else:
                existing_result.error_message = error_msg
                
            db.commit()
    finally:
        db.close()