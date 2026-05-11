import os
import cv2
import ffmpeg
import json
import base64
import requests
import numpy as np
from sqlalchemy.orm import Session
from database import SessionLocal
import models
import time
import datetime
import math
import logging
from typing import List, Dict
import pathlib

# --- 后端：强制标准 Key 输出与多帧保障 ---

def get_video_metadata(video_path: str) -> Dict:
    try:
        probe = ffmpeg.probe(video_path)
        vs = next((s for s in probe['streams'] if s['codec_type'] == 'video'), None)
        if not vs: return {"duration": 0, "resolution": "unknown", "size_mb": 0, "fps": 0}
        duration = float(probe['format']['duration'])
        return {"duration": duration, "resolution": f"{vs['width']}x{vs['height']}", "size_mb": round(os.path.getsize(video_path)/1024/1024, 2), "fps": eval(vs['avg_frame_rate']) if '/' in vs['avg_frame_rate'] else float(vs['avg_frame_rate'])}
    except: return {"duration": 0, "resolution": "unknown", "size_mb": 0, "fps": 0}

def _parse_json_result(ans: str) -> Dict:
    """提取并解析 AI 返回的内容中的 JSON 数据块"""
    import re
    text = ans
    if "```json" in text: 
        try:
            text = text.split("```json")[1].split("```")[0].strip()
        except IndexError: pass
    elif "```" in text: 
        try:
            text = text.split("```")[1].split("```")[0].strip()
        except IndexError: pass
    else:
        match = re.search(r'\{.*\}', text, re.DOTALL)
        if match: text = match.group(0)
    
    if not text:
        raise Exception(f"AI 返回的内容中未找到有效的 JSON 数据块。")
        
    try:
        clean_text = text.strip()
        raw_res = json.loads(clean_text)
    except:
        fixed_text = re.sub(r',\s*([\]}])', r'\1', text)
        try:
            raw_res = json.loads(fixed_text)
        except Exception as e:
            raise Exception(f"解析 AI 返回的 JSON 失败: {str(e)}")

    # 归一化维度评分
    d = raw_res.get("dimension_scores", {})
    mapped = {
        "lighting_weather": d.get("lighting_weather", d.get("光照天气", 0)),
        "architecture": d.get("architecture", d.get("建筑风格", 0)),
        "facilities": d.get("facilities", d.get("固定设施", 0)),
        "vegetation": d.get("vegetation", d.get("植被绿化", 0)),
        "road_surface": d.get("road_surface", d.get("地面材质", 0))
    }
    raw_res["dimension_scores"] = mapped
    return raw_res

# --- Qwen-VL 专用 Token 计算逻辑 ---
logger = logging.getLogger(__name__)
QWEN_FRAME_FACTOR = 2
QWEN_IMAGE_FACTOR = 32
QWEN_MAX_RATIO = 200
QWEN_VIDEO_MIN_PIXELS = 4 * 32 * 32
QWEN_VIDEO_MAX_PIXELS = 640 * 32 * 32
QWEN_FPS = 2.0
QWEN_FPS_MIN_FRAMES = 4
QWEN_FPS_MAX_FRAMES = 2000
QWEN_VIDEO_TOTAL_PIXELS = 131072 * 32 * 32

def round_by_factor(number: int, factor: int) -> int:
    return round(number / factor) * factor

def ceil_by_factor(number: int, factor: int) -> int:
    return math.ceil(number / factor) * factor

def floor_by_factor(number: int, factor: int) -> int:
    return math.floor(number / factor) * factor

def smart_nframes(fps_param, total_frames, video_fps):
    fps = fps_param or QWEN_FPS
    min_frames = ceil_by_factor(QWEN_FPS_MIN_FRAMES, QWEN_FRAME_FACTOR)
    max_frames = floor_by_factor(min(QWEN_FPS_MAX_FRAMES, total_frames), QWEN_FRAME_FACTOR)
    duration = total_frames / video_fps if video_fps != 0 else 0
    total_frames_adj = math.ceil(duration * video_fps)
    nframes = total_frames_adj / video_fps * fps if video_fps != 0 else 0
    nframes = int(min(min(max(nframes, min_frames), max_frames), total_frames))
    return nframes

def qwen_token_calculate(video_path, fps=1):
    if not video_path or not os.path.exists(video_path): return 0
    try:
        cap = cv2.VideoCapture(video_path)
        w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        total_f = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        v_fps = cap.get(cv2.CAP_PROP_FPS)
        cap.release()
        if total_f <= 0 or v_fps <= 0: return 0
        
        nframes = smart_nframes(fps, total_f, v_fps)
        max_pixels = max(min(QWEN_VIDEO_MAX_PIXELS, QWEN_VIDEO_TOTAL_PIXELS / nframes * QWEN_FRAME_FACTOR), int(QWEN_VIDEO_MIN_PIXELS * 1.05))
        
        h_bar = max(QWEN_IMAGE_FACTOR, round_by_factor(h, QWEN_IMAGE_FACTOR))
        w_bar = max(QWEN_IMAGE_FACTOR, round_by_factor(w, QWEN_IMAGE_FACTOR))
        if h_bar * w_bar > max_pixels:
            beta = math.sqrt((h * w) / max_pixels)
            h_bar = floor_by_factor(h / beta, QWEN_IMAGE_FACTOR)
            w_bar = floor_by_factor(w / beta, QWEN_IMAGE_FACTOR)
        elif h_bar * w_bar < QWEN_VIDEO_MIN_PIXELS:
            beta = math.sqrt(QWEN_VIDEO_MIN_PIXELS / (h * w))
            h_bar = ceil_by_factor(h * beta, QWEN_IMAGE_FACTOR)
            w_bar = ceil_by_factor(w * beta, QWEN_IMAGE_FACTOR)
            
        video_token = int(math.ceil(nframes / 2) * (h_bar / 32) * (w_bar / 32)) + 2
        return video_token
    except: return 0

def analyze_with_vlm(frames_a: List[str], frames_b: List[str], prompt: str, api_key: str, base_url: str, model_id: str, provider: str = None, recognition_mode: str = "image", video_a_path: str = None, video_b_path: str = None, task_id: str = None, task_name: str = None):
    """
    通用 VLM 分析接口，支持 MiniMax 专用端点和标准 OpenAI 兼容格式
    """
    print(f"DEBUG: Starting VLM analysis with model {model_id} at {base_url}")
    def create_comparison_grid(fa, fb):
        def to_abs(p): return os.path.abspath(os.path.join(os.path.dirname(__file__), "..", p))
        imgs_a = [cv2.imread(to_abs(f)) for f in fa[:4] if os.path.exists(to_abs(f))]
        imgs_b = [cv2.imread(to_abs(f)) for f in fb[:4] if os.path.exists(to_abs(f))]
        imgs_a = [i for i in imgs_a if i is not None]
        imgs_b = [i for i in imgs_b if i is not None]
        if not imgs_a or not imgs_b: return None
        target_w, target_h = 400, 225
        def res(ims): return [cv2.resize(i, (target_w, target_h)) for i in ims]
        row_a = np.hstack(res(imgs_a))
        row_b = np.hstack(res(imgs_b))
        grid = np.vstack([row_a, row_b])
        _, buf = cv2.imencode(".jpg", grid, [cv2.IMWRITE_JPEG_QUALITY, 85])
        return base64.b64encode(buf).decode('utf-8')

    b64_grid = create_comparison_grid(frames_a, frames_b)
    if not b64_grid: raise Exception("无法生成对比图，请检查视频帧提取是否成功")

    vlm_prompt = f"""{prompt}
    请严格对比视频A（上行）与视频B（下行）的环境相似度。
    必须返回包含以下格式的 JSON 评分，不要包含任何其他文字：
    {{
      "similarity_score": 85,
      "dimension_scores": {{
        "lighting_weather": 80,
        "architecture": 90,
        "facilities": 70,
        "vegetation": 85,
        "road_surface": 100
      }},
      "similar_points": ["点1", "点2"],
      "difference_points": ["点1"],
      "summary": "综合分析结论..."
    }}
    """
    
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
    
    # 自动识别端点类型并处理 URL
    is_native_vlm = "/coding_plan/vlm" in base_url
    started_at = datetime.datetime.now()
    
    request_url = base_url
    if not is_native_vlm:
        # 如果是 OpenAI 兼容模式，确保 URL 以 /chat/completions 结尾
        if not request_url.endswith("/chat/completions"):
            request_url = request_url.rstrip("/") + "/chat/completions"
    
    # --- 特殊处理：Qwen-VL-Plus 原生视频识别 (使用 DashScope SDK) ---
    if recognition_mode == "video" and "qwen" in model_id.lower() and video_a_path and video_b_path:
        try:
            import dashscope
            dashscope.api_key = api_key
            
            # 将相对路径转换为绝对路径
            abs_a = str(pathlib.Path(video_a_path).absolute())
            abs_b = str(pathlib.Path(video_b_path).absolute())
            
            print(f"DEBUG: Using DashScope Native Video Analysis for {model_id}")
            print(f"Video A: {abs_a}, Video B: {abs_b}")

            # 构造多模态消息，包含 A 和 B 两个视频
            messages = [{
                "role": "user",
                "content": [
                    {"video": f"file://{abs_a}"},
                    {"video": f"file://{abs_b}"},
                    {"text": vlm_prompt}
                ]
            }]
            
            response = dashscope.MultiModalConversation.call(
                model=model_id,
                messages=messages
            )
            
            if response.status_code == 200:
                ended_at = datetime.datetime.now()
                ans = response.output.choices[0].message.content[0]["text"]
                # 提取 Token
                it = response.usage.input_tokens if hasattr(response.usage, "input_tokens") else 0
                ot = response.usage.output_tokens if hasattr(response.usage, "output_tokens") else 0
                
                # 如果 Token 为 0，进行估算以获得更准确的记录
                if it == 0:
                    it = qwen_token_calculate(video_a_path) + qwen_token_calculate(video_b_path) + (len(vlm_prompt) // 2 + 150)
                if ot == 0: ot = len(ans) // 2

                # 记录日志
                try:
                    # 将 DashScope 对象转换为可序列化的字典
                    res_body = {}
                    if hasattr(response, 'output'):
                        try: res_body = json.loads(json.dumps(response.output))
                        except: res_body = str(response.output)

                    db = SessionLocal()
                    log = models.ModelCallLog(
                        task_id=task_id, task_name=task_name,
                        model_id=model_id, model_url=base_url or "DashScope SDK",
                        request_payload=messages, response_body=res_body,
                        started_at=started_at, ended_at=ended_at, status_code="200",
                        input_tokens=it, output_tokens=ot
                    )
                    db.add(log)
                    db.commit()
                    db.close()
                except Exception as le:
                    print(f"Logging Error (Video Mode): {le}")

                # DashScope 的 Token 统计在 usage 字段
                usage = {
                    "prompt_tokens": response.usage.input_tokens if hasattr(response.usage, "input_tokens") else 0,
                    "completion_tokens": response.usage.output_tokens if hasattr(response.usage, "output_tokens") else 0
                }
                # 解析返回的 JSON (复用下面的解析逻辑)
                # 由于后面还有解析逻辑，我们先跳转到结果处理
                return {"result": _parse_json_result(ans), "usage": usage}
            else:
                print(f"DashScope Error: {response.code} - {response.message}")
                # 如果 SDK 失败，回退到普通逻辑
        except Exception as de:
            print(f"DashScope SDK Exception: {de}")
            # 回退
    
    if is_native_vlm:
        payload = {"prompt": vlm_prompt, "image_url": f"data:image/jpeg;base64,{b64_grid}"}
    elif recognition_mode == "video" and ("qwen" in model_id.lower() or "gemini" in model_id.lower() or "gpt-4" in model_id.lower()):
        # 视频识别模式：传送到支持多图/视频流的模型（如 Qwen, Gemini, GPT-4o）
        # 将采样帧作为独立图像序列发送，利用模型的时序理解能力
        content_list = [{"type": "text", "text": vlm_prompt}]
        
        # 提取 A 和 B 的采样帧 (最多各取 5 帧，避免 Token 溢出)
        for f in frames_a[:5]:
            with open(os.path.abspath(f), "rb") as image_file:
                b64 = base64.b64encode(image_file.read()).decode('utf-8')
                content_list.append({"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64}"}})
        
        for f in frames_b[:5]:
            with open(os.path.abspath(f), "rb") as image_file:
                b64 = base64.b64encode(image_file.read()).decode('utf-8')
                content_list.append({"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64}"}})

        payload = {
            "model": model_id,
            "messages": [{"role": "user", "content": content_list}],
            "temperature": 0.1,
            "max_tokens": 1024
        }
    else:
        # 标准 OpenAI 兼容格式 (图像网格模式)
        payload = {
            "model": model_id,
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": vlm_prompt},
                        {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64_grid}"}}
                    ]
                }
            ],
            "temperature": 0.1,
            "max_tokens": 1024
        }
        
        # MiniMax 特殊处理: 某些版本在 /chat/completions 下也需要 top-level images 字段，或者不支持 content 列表格式
        if provider == "MiniMax" or "minimax" in model_id.lower():
            # 尝试使用 MiniMax 推荐的混合格式：content 为字符串 + images 数组
            payload["messages"][0] = {
                "role": "user",
                "content": vlm_prompt,
                "images": [b64_grid]
            }

    try:
        response = requests.post(request_url, headers=headers, json=payload, timeout=120)
        ended_at = datetime.datetime.now()
        
        it, ot = 0, 0
        ans = ""
        if response.status_code == 200:
            rj = response.json()
            usage = rj.get("usage", {})
            it = usage.get("prompt_tokens", usage.get("input_tokens", 0))
            ot = usage.get("completion_tokens", usage.get("output_tokens", 0))
            
            # 尝试提取回答内容用于估算
            if is_native_vlm:
                ans = rj.get('content') or ""
            else:
                choices = rj.get('choices', [])
                if choices: ans = choices[0].get('message', {}).get('content', '')
            
            # 如果 Token 为 0，进行估算
            if it == 0: it = len(vlm_prompt) // 2 + 150
            if ot == 0: ot = len(ans) // 2

        # 记录日志
        try:
            db = SessionLocal()
            log = models.ModelCallLog(
                task_id=task_id, task_name=task_name,
                model_id=model_id, model_url=request_url,
                request_payload=payload, response_body=response.json() if response.status_code == 200 else {"error": response.text},
                started_at=started_at, ended_at=ended_at, status_code=str(response.status_code),
                input_tokens=it, output_tokens=ot
            )
            db.add(log)
            db.commit()
            db.close()
        except: pass

        # 记录调试信息
        print(f"VLM Request URL: {request_url}")
        print(f"VLM Response Status: {response.status_code}")
        
        if response.status_code != 200:
            raise Exception(f"API 返回错误: {response.status_code} - {response.text}")
            
        if not response.text.strip():
            raise Exception(f"API 返回了空响应 (Status: {response.status_code})")
            
        try:
            rj = response.json()
        except Exception as e:
            raise Exception(f"解析 API 响应失败: {str(e)}。状态码: {response.status_code}, 内容预览: {response.text[:200]}...")
            
        # 处理不同格式的响应
        if is_native_vlm:
            # 优先尝试 root-level content (MiniMax Native VLM)
            ans = rj.get('content')
            if not ans:
                # 备选：尝试 choices (有些版本可能返回这个)
                choices = rj.get('choices', [])
                if choices:
                    ans = choices[0].get('message', {}).get('content', '')
                else:
                    # 再次备选：如果是 dict 且包含 output/results 等
                    ans = rj.get('output', {}).get('text', '') or rj.get('result', '')
            ans = str(ans or '').strip()
        else:
            choices = rj.get('choices', [])
            if not choices:
                # 检查是否有错误信息在响应中
                if 'error' in rj:
                    raise Exception(f"API 返回业务错误: {rj['error']}")
                raise Exception(f"API 返回格式异常: {rj}")
            ans = choices[0].get('message', {}).get('content', '').strip()
            
        if not ans: raise Exception("API 返回内容为空")
        
        # 记录原始返回以便调试
        print(f"AI Response Content: {ans[:200]}...")
        
        # 解析 JSON
        raw_res = _parse_json_result(ans)
        
        # 提取 Token 使用信息
        usage = rj.get("usage", {})
        
        # 如果 usage 为空 (MiniMax Native VLM 或其他情况)，进行估算
        if not usage:
            completion_tokens = len(ans) // 2
            if recognition_mode == "video" and video_a_path and video_b_path:
                # 使用 Qwen 专用算法计算视频 Token
                prompt_tokens = qwen_token_calculate(video_a_path) + qwen_token_calculate(video_b_path) + (len(vlm_prompt) // 2 + 150)
            else:
                prompt_tokens = len(vlm_prompt) // 2 + 150 
            
            usage = {
                "prompt_tokens": prompt_tokens,
                "completion_tokens": completion_tokens,
                "total_tokens": prompt_tokens + completion_tokens,
                "estimated": True
            }
        
        return {"result": raw_res, "usage": usage}
    except Exception as e:
        print(f"VLM Analysis Error: {str(e)}")
        return {
            "result": {
                "similarity_score": 0, 
                "summary": f"分析失败: {str(e)}", 
                "dimension_scores": {"lighting_weather":0,"architecture":0,"facilities":0,"vegetation":0,"road_surface":0}
            },
            "usage": {},
            "error": str(e)
        }

def extract_frames(video_path: str, task_id: str, suffix: str, fps: int = 1, resolution: int = 720, denoise: bool = False, sampling_type: str = "fixed"):
    od = os.path.join("storage", f"{task_id}_{suffix}_frames"); os.makedirs(od, exist_ok=True)
    frames = []; vp = video_path.replace("\\", "/"); op = os.path.join(od, "frame_%03d.jpg").replace("\\", "/")
    try:
        if sampling_type == "perceptual":
            frames = extract_perceptual_frames(vp, od, resolution)
        else:
            fs = f"fps={fps},scale=-1:{resolution}"
            ffmpeg.input(vp).output(op, vf=fs, qscale=2).overwrite_output().run(capture_stdout=True, capture_stderr=True)
            for fn in sorted(os.listdir(od)):
                if fn.endswith(".jpg"): frames.append(os.path.join(od, fn).replace("\\", "/"))
    except: pass
        
    if len(frames) < 3:
        cap = cv2.VideoCapture(video_path)
        tot = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        for p in [0, tot//2, int(tot*0.9)]:
            cap.set(cv2.CAP_PROP_POS_FRAMES, p); ret, f = cap.read()
            if ret:
                out = os.path.join(od, f"f_{p}.jpg")
                # 修复：回退方案也应遵循用户设定的分辨率，而非硬编码 1280x720
                h, w = f.shape[:2]
                target_h = resolution
                target_w = int(w * (target_h / h))
                cv2.imwrite(out, cv2.resize(f, (target_w, target_h)))
                frames.append(out.replace("\\", "/"))
        cap.release()
    return sorted(list(set(frames)))

def extract_perceptual_frames(video_path: str, output_dir: str, resolution: int = 720):
    from scenedetect import open_video, SceneManager, ContentDetector
    frames = []
    try:
        v = open_video(video_path); sm = SceneManager(); sm.add_detector(ContentDetector(threshold=24.0)); sm.detect_scenes(v)
        si = [s[0].get_frames() for s in sm.get_scene_list()]
    except: si = []
    cap = cv2.VideoCapture(video_path)
    fps, tot = cap.get(cv2.CAP_PROP_FPS) or 25, int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    ret, pf = cap.read()
    if not ret: return []
    pg = cv2.cvtColor(pf, cv2.COLOR_BGR2GRAY)
    curr, am, fi, p0 = 0, 0, {0}, cv2.goodFeaturesToTrack(pg, None, 100, 0.3, 7, 7)
    step = max(1, int(fps / 2))
    while curr + step < tot:
        curr += step; cap.set(cv2.CAP_PROP_POS_FRAMES, curr); ret, f = cap.read()
        if not ret: break
        g = cv2.cvtColor(f, cv2.COLOR_BGR2GRAY)
        if p0 is not None:
            p1, st, _ = cv2.calcOpticalFlowPyrLK(pg, g, p0, None)
            if p1 is not None and len(p1[st==1]) > 0:
                dist = np.median(np.linalg.norm(p1[st==1] - p0[st==1], axis=1))
                am += dist
                if am > (f.shape[1] * 0.15): # 进一步灵敏
                    fi.add(curr); am = 0; p0 = cv2.goodFeaturesToTrack(g, None, 100, 0.3, 7, 7)
                else: p0 = p1[st==1].reshape(-1, 1, 2)
        pg = g.copy()
    cap.release()
    sel = sorted(list(fi.union(set(si))))
    if len(sel) > 15: s = len(sel)//15; sel = sel[::s][:15]
    cap = cv2.VideoCapture(video_path)
    for idx in sel:
        cap.set(cv2.CAP_PROP_POS_FRAMES, idx); ret, f = cap.read()
        if ret:
            fr = cv2.resize(f, (int(f.shape[1]*(resolution/f.shape[0])), resolution))
            out = os.path.join(output_dir, f"p_{idx:05d}.jpg"); cv2.imwrite(out, fr)
            frames.append(out.replace("\\", "/"))
    cap.release()
    return sorted(frames)

def preprocess_video_for_vlm(video_path: str, task_id: str, suffix: str, target_res: int) -> str:
    """使用 FFmpeg 对原始视频进行压制和缩放，以适配 VLM 分析需求"""
    out_path = os.path.join("storage", f"{task_id}_{suffix}_processed.mp4").replace("\\", "/")
    try:
        # scale=-2:target_res 确保高度为 target_res 且宽度为偶数（libx264 要求）
        (
            ffmpeg
            .input(video_path)
            .output(out_path, vf=f"scale=-2:{target_res}", vcodec='libx264', crf=28, preset='faster', acodec='aac')
            .overwrite_output()
            .run(capture_stdout=True, capture_stderr=True)
        )
        return out_path
    except Exception as e:
        print(f"Video Compression Error for {suffix}: {e}")
        return video_path

def process_video_task(task_id: str):
    db = SessionLocal()
    try:
        t = db.query(models.Task).filter(models.Task.id == task_id).first()
        if not t: return
        t.status = models.TaskStatus.PROCESSING; db.commit()
        cfg = db.query(models.AIModel).filter(models.AIModel.identifier == t.model_id).first()
        key, url = (cfg.api_key if cfg else ""), (cfg.base_url if cfg else "")
        opts = t.preprocess_options or {}
        st, fps, res = opts.get("sampling_type", "fixed"), opts.get("sampling_fps", 1), opts.get("resolution_val", 720)
        
        # 预先获取元数据
        meta_a = get_video_metadata(t.video_a_path)
        meta_b = get_video_metadata(t.video_b_path)
        t.video_a_duration, t.video_a_resolution, t.video_a_size = meta_a["duration"], meta_a["resolution"], meta_a["size_mb"]
        t.video_b_duration, t.video_b_resolution, t.video_b_size = meta_b["duration"], meta_b["resolution"], meta_b["size_mb"]
        db.commit()

        fa = extract_frames(t.video_a_path, task_id, "A", fps, res, False, st)
        fb = extract_frames(t.video_b_path, task_id, "B", fps, res, False, st)
        
        recognition_mode = opts.get("recognition_mode", "image")
        v_a_payload_path, v_b_payload_path = t.video_a_path, t.video_b_path

        # 如果是视频识别模式且开启了分辨率调整，则执行实际的视频压缩
        if recognition_mode == "video" and opts.get("resolution"):
            print(f"Starting video compression to {res}p...")
            v_a_payload_path = preprocess_video_for_vlm(t.video_a_path, task_id, "A", res)
            v_b_payload_path = preprocess_video_for_vlm(t.video_b_path, task_id, "B", res)
            
            # 更新元数据以反映压缩后的实际载荷规格
            meta_a_p = get_video_metadata(v_a_payload_path)
            meta_b_p = get_video_metadata(v_b_payload_path)
            t.video_a_resolution, t.video_a_size = meta_a_p["resolution"], meta_a_p["size_mb"]
            t.video_b_resolution, t.video_b_size = meta_b_p["resolution"], meta_b_p["size_mb"]
            db.commit()

        tr = db.query(models.TaskResult).filter(models.TaskResult.task_id == task_id).first()
        if not tr: db.add(models.TaskResult(task_id=task_id, summary="处理中...", key_frames_a=fa, key_frames_b=fb))
        else: tr.key_frames_a, tr.key_frames_b = fa, fb
        db.commit()

        # 更新最终分析后的规格数据（反映实际提交给 AI 的分辨率和规模）
        def get_payload_info(frames):
            if not frames: return "0x0", 0.0
            try:
                # 获取第一帧的分辨率作为代表
                first_frame = frames[0]
                # 确保路径在 Windows 下能被 cv2 正确读取
                abs_path = os.path.abspath(first_frame)
                img = cv2.imread(abs_path)
                if img is not None:
                    res_str = f"{img.shape[1]}x{img.shape[0]}"
                    # 计算所有帧的总大小 (MB)
                    total_bytes = sum(os.path.getsize(os.path.abspath(f)) for f in frames if os.path.exists(os.path.abspath(f)))
                    return res_str, round(total_bytes / 1024 / 1024, 2)
                return "unknown", 0.0
            except Exception as e:
                print(f"Payload Info Error: {e}")
                return "error", 0.0

        if recognition_mode == "image":
            res_a, size_a = get_payload_info(fa)
            res_b, size_b = get_payload_info(fb)
            t.video_a_resolution, t.video_a_size = res_a, size_a
            t.video_b_resolution, t.video_b_size = res_b, size_b
            db.commit()
            print(f"Image Mode Payload Specs - A: {res_a} ({size_a}MB), B: {res_b} ({size_b}MB)")
        
        db.refresh(t)

        try:
            provider = cfg.provider if cfg else "Unknown"
            # 传入压缩后（或原始）视频路径以支持原生视频识别，同时传入任务 ID 和名称用于日志记录
            vlm_response = analyze_with_vlm(fa, fb, t.prompt, key, url, t.model_id, provider, recognition_mode, v_a_payload_path, v_b_payload_path, t.id, t.task_name)
            air = vlm_response.get("result", {})
            usage = vlm_response.get("usage", {})
            
            # 即使分析结果包含 error，也尝试更新部分数据
            t.similarity_score = air.get("similarity_score", 0)
            
            # 更新 Token 统计 (优先使用 API 返回值，无则使用估算值)
            t.input_tokens = usage.get("prompt_tokens", usage.get("input_tokens", 0))
            t.output_tokens = usage.get("completion_tokens", usage.get("output_tokens", 0))
            
            tr = db.query(models.TaskResult).filter(models.TaskResult.task_id == task_id).first()
            if tr:
                tr.dimension_scores = air.get("dimension_scores", {})
                tr.similar_points = air.get("similar_points", [])
                tr.difference_points = air.get("difference_points", [])
                tr.summary = air.get("summary", "")
                # 确保 TaskResult 中的数据也是最新校准过的
                tr.input_tokens = t.input_tokens
                tr.output_tokens = t.output_tokens
                
                if vlm_response.get("error"):
                    tr.error_message = vlm_response.get("error")
                    t.status = models.TaskStatus.FAILED
                else:
                    t.status = models.TaskStatus.COMPLETED
            db.commit()
        except Exception as e:
            print(f"Workflow Exception: {str(e)}")
            raise e
    except Exception as e:
        # 这里不再使用 db.rollback()，因为我们希望保留之前成功 commit 的部分（如关键帧）
        try:
            t = db.query(models.Task).filter(models.Task.id == task_id).first()
            if t:
                t.status = models.TaskStatus.FAILED
                tr = db.query(models.TaskResult).filter(models.TaskResult.task_id == task_id).first()
                if tr: 
                    tr.error_message = str(e)
                    tr.summary = f"分析过程中止: {str(e)}"
                db.commit()
        except Exception as db_e:
            print(f"Failed to save error status: {db_e}")
    finally: db.close()