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

def analyze_with_minimax_vlm(frames_a, frames_b, prompt, api_key, base_url):
    vlm_url = "https://api.minimaxi.com/v1/coding_plan/vlm"
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
    
    def create_comparison_grid(fa, fb):
        def to_abs(p): return os.path.abspath(p)
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
    if not b64_grid: raise Exception("Grid Error")

    # 强化 Prompt，强制要求标准维度名
    vlm_prompt = f"""{prompt}
    请严格对比视频A（上行）与视频B（下行）的环境相似度。
    必须返回包含以下 5 个维度的 JSON 评分：
    lighting_weather (光照天气), architecture (建筑风格), facilities (固定设施), vegetation (植被绿化), road_surface (地面材质)。
    """
    
    payload = {"prompt": vlm_prompt, "image_url": f"data:image/jpeg;base64,{b64_grid}"}

    try:
        response = requests.post(vlm_url, headers=headers, json=payload, timeout=180)
        rj = response.json(); ans = rj.get('content', '').strip()
        text = ans
        if "```json" in text: text = text.split("```json")[1].split("```")[0].strip()
        elif "```" in text: text = text.split("```")[1].split("```")[0].strip()
        
        raw_res = json.loads(text)
        # 强制格式化输出，确保 5 个维度一个不落
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
    except:
        return {"similarity_score": 0, "summary": ans if 'ans' in locals() else "Error", "dimension_scores": {"lighting_weather":0,"architecture":0,"facilities":0,"vegetation":0,"road_surface":0}}

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
            air = analyze_with_minimax_vlm(fa, fb, t.prompt, key, url)
            t.status = models.TaskStatus.COMPLETED; t.similarity_score = air.get("similarity_score", 0)
            t.input_tokens = 500 # 估算值，因为 VLM 接口没返回
            t.output_tokens = len(str(air)) // 2
            tr = db.query(models.TaskResult).filter(models.TaskResult.task_id == task_id).first()
            if tr:
                tr.dimension_scores, tr.similar_points, tr.difference_points, tr.summary = air.get("dimension_scores", {}), air.get("similar_points", []), air.get("difference_points", []), air.get("summary", "")
            db.commit()
        except Exception as e: raise e
    except Exception as e:
        db.rollback()
        try:
            t = db.query(models.Task).filter(models.Task.id == task_id).first()
            if t:
                t.status = models.TaskStatus.FAILED
                tr = db.query(models.TaskResult).filter(models.TaskResult.task_id == task_id).first()
                if tr: tr.error_message, tr.summary = str(e), f"分析失败: {str(e)}"
                db.commit()
        except: pass
    finally: db.close()