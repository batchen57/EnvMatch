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
from typing import List, Dict

# --- 后端：强制标准 Key 输出与多帧保障 ---

def get_video_metadata(video_path: str) -> Dict:
    try:
        probe = ffmpeg.probe(video_path)
        vs = next((s for s in probe['streams'] if s['codec_type'] == 'video'), None)
        if not vs: return {"duration": 0, "resolution": "unknown", "size_mb": 0, "fps": 0}
        duration = float(probe['format']['duration'])
        return {"duration": duration, "resolution": f"{vs['width']}x{vs['height']}", "size_mb": round(os.path.getsize(video_path)/1024/1024, 2), "fps": eval(vs['avg_frame_rate']) if '/' in vs['avg_frame_rate'] else float(vs['avg_frame_rate'])}
    except: return {"duration": 0, "resolution": "unknown", "size_mb": 0, "fps": 0}

def analyze_with_vlm(frames_a: List[str], frames_b: List[str], prompt: str, api_key: str, base_url: str, model_id: str, provider: str = None):
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
    
    request_url = base_url
    if not is_native_vlm:
        # 如果是 OpenAI 兼容模式，确保 URL 以 /chat/completions 结尾
        if not request_url.endswith("/chat/completions"):
            request_url = request_url.rstrip("/") + "/chat/completions"
    
    if is_native_vlm:
        payload = {"prompt": vlm_prompt, "image_url": f"data:image/jpeg;base64,{b64_grid}"}
    else:
        # 标准 OpenAI 兼容格式
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
        text = ans
        if "```json" in text: 
            try:
                text = text.split("```json")[1].split("```")[0].strip()
            except IndexError:
                pass
        elif "```" in text: 
            try:
                text = text.split("```")[1].split("```")[0].strip()
            except IndexError:
                pass
        else:
            # 如果没有代码块标记，尝试寻找第一个 { 和最后一个 }
            import re
            match = re.search(r'\{.*\}', text, re.DOTALL)
            if match:
                text = match.group(0)
        
        if not text:
            raise Exception(f"AI 返回的内容中未找到有效的 JSON 数据块。原始回复: {ans[:200]}...")
            
        try:
            # 预处理：移除一些可能导致解析失败的非标准字符
            clean_text = text.strip()
            raw_res = json.loads(clean_text)
        except Exception as e:
            # 尝试修复一些常见的 JSON 错误（如多余逗号）
            import re
            fixed_text = re.sub(r',\s*([\]}])', r'\1', text)
            try:
                raw_res = json.loads(fixed_text)
            except Exception as e2:
                raise Exception(f"解析 AI 返回的 JSON 失败: {str(e2)}。待解析文本: {text[:200]}...")

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
    except Exception as e:
        print(f"VLM Analysis Error: {str(e)}")
        return {
            "similarity_score": 0, 
            "summary": f"分析失败: {str(e)}", 
            "dimension_scores": {"lighting_weather":0,"architecture":0,"facilities":0,"vegetation":0,"road_surface":0},
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
                cv2.imwrite(out, cv2.resize(f, (1280, 720)))
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
        
        tr = db.query(models.TaskResult).filter(models.TaskResult.task_id == task_id).first()
        if not tr: db.add(models.TaskResult(task_id=task_id, summary="处理中...", key_frames_a=fa, key_frames_b=fb))
        else: tr.key_frames_a, tr.key_frames_b = fa, fb
        db.commit()

        try:
            provider = cfg.provider if cfg else "Unknown"
            air = analyze_with_vlm(fa, fb, t.prompt, key, url, t.model_id, provider)
            
            # 即使分析结果包含 error，也尝试更新部分数据
            t.similarity_score = air.get("similarity_score", 0)
            usage = air.get("usage") or {}
            t.input_tokens = usage.get("prompt_tokens", 500)
            t.output_tokens = usage.get("completion_tokens", len(str(air)) // 2)
            
            tr = db.query(models.TaskResult).filter(models.TaskResult.task_id == task_id).first()
            if tr:
                tr.dimension_scores = air.get("dimension_scores", {})
                tr.similar_points = air.get("similar_points", [])
                tr.difference_points = air.get("difference_points", [])
                tr.summary = air.get("summary", "")
                if air.get("error"):
                    tr.error_message = air.get("error")
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