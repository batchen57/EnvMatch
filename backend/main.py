from fastapi import FastAPI, BackgroundTasks, UploadFile, File, Form, Depends, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from sqlalchemy.orm import Session
from sqlalchemy import func
import models
from database import engine, get_db
import os
import time
import shutil
import uuid
import shutil
import json
models.Base.metadata.create_all(bind=engine)
app = FastAPI(title="EnvMatch API")
# Add CORS middleware for frontend connection
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
STORAGE_DIR = "storage"
os.makedirs(STORAGE_DIR, exist_ok=True)
# Serve video files from storage directory
app.mount("/storage", StaticFiles(directory=STORAGE_DIR), name="storage")
@app.post("/tasks/")
async def create_task(
    background_tasks: BackgroundTasks,
    task_name: str = Form(...),
    prompt: str = Form(None),
    model_id: str = Form("gemini-2.5-pro"),
    video_a: UploadFile = File(...),
    video_b: UploadFile = File(...),
    preprocess_options: str = Form(None),
    db: Session = Depends(get_db)
):
    # Save files locally
    video_a_ext = os.path.splitext(video_a.filename)[1] if video_a.filename else ".mp4"
    video_b_ext = os.path.splitext(video_b.filename)[1] if video_b.filename else ".mp4"
    
    task_id = str(uuid.uuid4())
    
    video_a_path = os.path.join(STORAGE_DIR, f"{task_id}_A{video_a_ext}")
    video_b_path = os.path.join(STORAGE_DIR, f"{task_id}_B{video_b_ext}")
    
    with open(video_a_path, "wb") as buffer:
        shutil.copyfileobj(video_a.file, buffer)
    with open(video_b_path, "wb") as buffer:
        shutil.copyfileobj(video_b.file, buffer)
        
    # Extract Metadata
    from services.task_processor import get_video_metadata
    meta_a = get_video_metadata(video_a_path)
    meta_b = get_video_metadata(video_b_path)
    # Create DB record
    db_task = models.Task(
        id=task_id,
        task_name=task_name,
        video_a_path=video_a_path,
        video_b_path=video_b_path,
        status=models.TaskStatus.PENDING,
        model_id=model_id,
        prompt=prompt,
        video_a_duration=meta_a["duration"],
        video_b_duration=meta_b["duration"],
        video_a_resolution=meta_a["resolution"],
        video_b_resolution=meta_b["resolution"],
        video_a_size=meta_a["size_mb"],
        video_b_size=meta_b["size_mb"],
        preprocess_options=json.loads(preprocess_options) if preprocess_options else None
    )
    db.add(db_task)
    db.commit()
    db.refresh(db_task)
    
    # Run async task
    from services.task_processor import process_video_task
    background_tasks.add_task(process_video_task, task_id)
    
    return {"task_id": db_task.id, "message": "Task created successfully"}
@app.get("/tasks/{task_id}")
def get_task(task_id: str, db: Session = Depends(get_db)):
    task = db.query(models.Task).filter(models.Task.id == task_id).first()
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    
    # Always try to fetch result if it exists (could contain error info or partial results)
    result = db.query(models.TaskResult).filter(models.TaskResult.task_id == task_id).first()
        
    return {"task": task, "result": result}
@app.delete("/tasks/{task_id}")
def delete_task(task_id: str, db: Session = Depends(get_db)):
    task = db.query(models.Task).filter(models.Task.id == task_id).first()
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    
    # Clean up physical files
    try:
        if task.video_a_path and os.path.exists(task.video_a_path):
            os.remove(task.video_a_path)
        if task.video_b_path and os.path.exists(task.video_b_path):
            os.remove(task.video_b_path)
            
        # Clean up frames directories
        frames_a_dir = os.path.join("storage", f"{task_id}_A_frames")
        frames_b_dir = os.path.join("storage", f"{task_id}_B_frames")
        if os.path.exists(frames_a_dir):
            shutil.rmtree(frames_a_dir)
        if os.path.exists(frames_b_dir):
            shutil.rmtree(frames_b_dir)
            
        # Clean up processed videos if any
        processed_a = os.path.join("storage", f"{task_id}_A_processed.mp4")
        processed_b = os.path.join("storage", f"{task_id}_B_processed.mp4")
        if os.path.exists(processed_a): os.remove(processed_a)
        if os.path.exists(processed_b): os.remove(processed_b)
    except Exception as e:
        print(f"Error cleaning up files for task {task_id}: {e}")
    # Delete from DB (cascading TaskResult if configured, but let's do it manually just in case)
    db.query(models.TaskResult).filter(models.TaskResult.task_id == task_id).delete()
    db.delete(task)
    db.commit()
    return {"status": "success", "message": f"Task {task_id} deleted"}
@app.get("/tasks/")
def list_tasks(status: str = None, skip: int = 0, limit: int = 100, db: Session = Depends(get_db)):
    query = db.query(models.Task)
    if status and status != 'ALL':
        if status == 'PROCESSING':
            query = query.filter(models.Task.status.in_([models.TaskStatus.PENDING, models.TaskStatus.PROCESSING]))
        else:
            query = query.filter(models.Task.status == status)
            
    tasks = query.order_by(models.Task.created_at.desc()).offset(skip).limit(limit).all()
    
    # Get total counts for each category (always based on entire DB)
    total = db.query(models.Task).count()
    processing = db.query(models.Task).filter(models.Task.status.in_([models.TaskStatus.PENDING, models.TaskStatus.PROCESSING])).count()
    completed = db.query(models.Task).filter(models.Task.status == models.TaskStatus.COMPLETED).count()
    failed = db.query(models.Task).filter(models.Task.status == models.TaskStatus.FAILED).count()
    
    return {
        "tasks": tasks,
        "total_counts": {
            "ALL": total,
            "PROCESSING": processing,
            "COMPLETED": completed,
            "FAILED": failed
        }
    }
@app.get("/dashboard-stats")
def get_dashboard_stats(db: Session = Depends(get_db)):
    total_tasks = db.query(models.Task).count()
    completed_tasks = db.query(models.Task).filter(models.Task.status == models.TaskStatus.COMPLETED).count()
    failed_tasks = db.query(models.Task).filter(models.Task.status == models.TaskStatus.FAILED).count()
    
    # Global metrics
    avg_sim = db.query(func.avg(models.Task.similarity_score)).filter(models.Task.status == models.TaskStatus.COMPLETED).scalar() or 0.0
    total_tokens = db.query(func.sum(models.Task.input_tokens + models.Task.output_tokens)).scalar() or 0.0
    total_duration = db.query(func.sum(models.Task.video_a_duration + models.Task.video_b_duration)).scalar() or 0.0
    total_size = db.query(func.sum(models.Task.video_a_size + models.Task.video_b_size)).scalar() or 0.0
    
    # Similarity distribution
    high_sim = db.query(models.Task).filter(models.Task.similarity_score >= 70).count()
    med_sim = db.query(models.Task).filter(models.Task.similarity_score >= 40, models.Task.similarity_score < 70).count()
    low_sim = db.query(models.Task).filter(models.Task.similarity_score < 40).count()
    # Dimension scores aggregation
    dim_sums = {"architecture": 0, "vegetation": 0, "lighting_weather": 0, "facilities": 0, "road_surface": 0}
    results = db.query(models.TaskResult).all()
    count_with_results = len(results)
    if count_with_results > 0:
        for r in results:
            ds = r.dimension_scores or {}
            for k in dim_sums:
                dim_sums[k] += ds.get(k, 0)
        avg_dims = {k: round(v / count_with_results, 1) for k, v in dim_sums.items()}
    else:
        avg_dims = {k: 0 for k in dim_sums}
    # Model Performance
    model_stats = db.query(
        models.Task.model_id, 
        func.count(models.Task.id).label("count"),
        func.avg(models.Task.similarity_score).label("avg_sim")
    ).group_by(models.Task.model_id).all()
    
    models_summary = []
    for m in model_stats:
        models_summary.append({
            "model_id": m.model_id or "Unknown",
            "count": m.count,
            "avg_similarity": round(m.avg_sim or 0, 1)
        })
    # Trend calculation (Last 7 days)
    import datetime
    today = datetime.date.today()
    dates = [(today - datetime.timedelta(days=i)).strftime("%m-%d") for i in range(6, -1, -1)]
    task_counts = []
    avg_sims = []
    token_usage = []
    
    for i in range(6, -1, -1):
        target_date = today - datetime.timedelta(days=i)
        start_time = datetime.datetime.combine(target_date, datetime.time.min)
        end_time = datetime.datetime.combine(target_date, datetime.time.max)
        
        count = db.query(models.Task).filter(models.Task.created_at >= start_time, models.Task.created_at <= end_time).count()
        day_avg = db.query(func.avg(models.Task.similarity_score)).filter(
            models.Task.created_at >= start_time, 
            models.Task.created_at <= end_time,
            models.Task.status == models.TaskStatus.COMPLETED
        ).scalar() or 0.0
        day_tokens = db.query(func.sum(models.Task.input_tokens + models.Task.output_tokens)).filter(
            models.Task.created_at >= start_time,
            models.Task.created_at <= end_time
        ).scalar() or 0.0
        
        task_counts.append(count)
        avg_sims.append(round(float(day_avg), 1))
        token_usage.append(int(day_tokens))
    return {
        "total": total_tasks,
        "completed": completed_tasks,
        "failed": failed_tasks,
        "avg_similarity": round(avg_sim, 1),
        "total_tokens": int(total_tokens),
        "total_duration": round(total_duration, 1),
        "total_size": round(total_size, 1),
        "avg_dimensions": avg_dims,
        "models_summary": models_summary,
        "distribution": {
            "high": high_sim,
            "medium": med_sim,
            "low": low_sim
        },
        "trend": {
            "dates": dates,
            "counts": task_counts,
            "similarities": avg_sims,
            "tokens": token_usage
        }
    }
@app.post("/cleanup")
def cleanup_old_files(days: int = 7):
    """
    Deletes files in the storage directory older than `days` days.
    """
    storage_dir = "storage"
    if not os.path.exists(storage_dir):
        return {"status": "ok", "deleted": 0}
        
    now = time.time()
    cutoff = now - (days * 86400)
    deleted_count = 0
    
    for filename in os.listdir(storage_dir):
        file_path = os.path.join(storage_dir, filename)
        if os.path.isfile(file_path):
            if os.path.getmtime(file_path) < cutoff:
                os.remove(file_path)
                deleted_count += 1
        elif os.path.isdir(file_path):
            if os.path.getmtime(file_path) < cutoff:
                shutil.rmtree(file_path)
                deleted_count += 1
                
    return {"status": "ok", "deleted": deleted_count}
from pydantic import BaseModel
class PromptTemplateCreate(BaseModel):
    name: str
    content: str
@app.get("/prompt-templates/")
def list_prompt_templates(db: Session = Depends(get_db)):
    templates = db.query(models.PromptTemplate).order_by(models.PromptTemplate.created_at.desc()).all()
    if not templates:
        default_content = """你是一个专业的环境场景分析师。我给你提供了两段视频（视频 A 和视频 B）。
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
}"""
        db_template = models.PromptTemplate(name="系统默认通用提示词", content=default_content)
        db.add(db_template)
        db.commit()
        templates = db.query(models.PromptTemplate).order_by(models.PromptTemplate.created_at.desc()).all()
    return templates
@app.post("/prompt-templates/")
def create_prompt_template(template: PromptTemplateCreate, db: Session = Depends(get_db)):
    db_template = models.PromptTemplate(name=template.name, content=template.content)
    db.add(db_template)
    db.commit()
    db.refresh(db_template)
    return db_template
@app.put("/prompt-templates/{template_id}")
def update_prompt_template(template_id: str, template: PromptTemplateCreate, db: Session = Depends(get_db)):
    db_template = db.query(models.PromptTemplate).filter(models.PromptTemplate.id == template_id).first()
    if not db_template:
        raise HTTPException(status_code=404, detail="Template not found")
    db_template.name = template.name
    db_template.content = template.content
    db.commit()
    db.refresh(db_template)
    return db_template
@app.delete("/prompt-templates/{template_id}")
def delete_prompt_template(template_id: str, db: Session = Depends(get_db)):
    db_template = db.query(models.PromptTemplate).filter(models.PromptTemplate.id == template_id).first()
    if not db_template:
        raise HTTPException(status_code=404, detail="Template not found")
    db.delete(db_template)
    db.commit()
    return {"status": "ok"}
class AIModelCreate(BaseModel):
    name: str
    identifier: str
    provider: str
    api_key: str = ""
    base_url: str = ""
    description: str = ""
    capabilities: list[str] = []
    is_default: str = "false"
    sort_order: float = 0.0
@app.get("/models/")
def list_models(db: Session = Depends(get_db)):
    models_list = db.query(models.AIModel).order_by(models.AIModel.sort_order.asc(), models.AIModel.created_at.desc()).all()
    if not models_list:
        # Seed default models if empty
        defaults = [
            {"name": "Gemini 2.5 Pro", "identifier": "gemini-2.5-pro", "provider": "Google", "api_key": "", "base_url": "", "description": "多模态理解能力强, 适用于复杂场景和环境细节分析，精准扣分。", "capabilities": ["text", "image", "video"], "is_default": "true"},
            {"name": "GPT-4o", "identifier": "gpt-4o", "provider": "OpenAI", "api_key": "", "base_url": "https://api.openai.com/v1", "description": "通用性强, 稳定可靠", "capabilities": ["text", "image", "video"], "is_default": "false"},
            {"name": "MiniMax 2.7", "identifier": "minimax-2.7", "provider": "MiniMax", "api_key": "", "base_url": "https://api.minimax.chat/v1", "description": "国产优秀大模型", "capabilities": ["text", "image"], "is_default": "false"},
            {"name": "MiniMax 2.7", "identifier": "MiniMax-M2.7", "provider": "MiniMax", "api_key": "", "base_url": "https://api.minimaxi.com/v1", "description": "国产优秀大模型，支持超长上下文和多轮对话。", "capabilities": ["text", "image"], "is_default": "false"},
            {"name": "Qwen3-VL-Plus", "identifier": "qwen-vl-plus", "provider": "Alibaba", "api_key": "", "base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1", "description": "视觉理解能力极强", "capabilities": ["text", "image", "video"], "is_default": "false"}
        ]
        for m in defaults:
            db_model = models.AIModel(**m)
            db.add(db_model)
        db.commit()
        models_list = db.query(models.AIModel).order_by(models.AIModel.sort_order.asc(), models.AIModel.created_at.desc()).all()
    return models_list
@app.post("/models/")
def create_model(model: AIModelCreate, db: Session = Depends(get_db)):
    # Ensure only one default model
    if model.is_default == 'true':
        db.query(models.AIModel).filter(models.AIModel.is_default == 'true').update({"is_default": "false"})
    db_model = models.AIModel(**model.dict())
    db.add(db_model)
    db.commit()
    db.refresh(db_model)
    return db_model
@app.put("/models/{model_id}")
def update_model(model_id: str, model: AIModelCreate, db: Session = Depends(get_db)):
    db_model = db.query(models.AIModel).filter(models.AIModel.id == model_id).first()
    if not db_model:
        raise HTTPException(status_code=404, detail="Model not found")
    # Ensure only one default model
    if model.is_default == 'true':
        db.query(models.AIModel).filter(models.AIModel.id != model_id, models.AIModel.is_default == 'true').update({"is_default": "false"})
    for key, value in model.dict().items():
        setattr(db_model, key, value)
    db.commit()
    db.refresh(db_model)
    return db_model
@app.delete("/models/{model_id}")
def delete_model(model_id: str, db: Session = Depends(get_db)):
    db_model = db.query(models.AIModel).filter(models.AIModel.id == model_id).first()
    if not db_model:
        raise HTTPException(status_code=404, detail="Model not found")
    db.delete(db_model)
    db.commit()
    return {"status": "ok"}
