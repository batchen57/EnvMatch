import uuid
from sqlalchemy import Column, String, Float, DateTime, Enum, JSON, ForeignKey
from sqlalchemy.sql import func
from database import Base
import enum
import datetime

class TaskStatus(str, enum.Enum):
    PENDING = "PENDING"
    PROCESSING = "PROCESSING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"

def generate_uuid():
    return str(uuid.uuid4())

class Task(Base):
    __tablename__ = "tasks"
    id = Column(String(36), primary_key=True, default=generate_uuid, index=True)
    task_name = Column(String, index=True)
    video_a_path = Column(String)
    video_b_path = Column(String)
    status = Column(Enum(TaskStatus), default=TaskStatus.PENDING)
    similarity_score = Column(Float, nullable=True)
    model_id = Column(String, nullable=True)
    prompt = Column(String, nullable=True)
    created_at = Column(DateTime, default=datetime.datetime.now)
    updated_at = Column(DateTime, default=datetime.datetime.now, onupdate=datetime.datetime.now)
    input_tokens = Column(Float, nullable=True)
    output_tokens = Column(Float, nullable=True)
    
    # Video Metadata
    video_a_duration = Column(Float, nullable=True)
    video_b_duration = Column(Float, nullable=True)
    video_a_resolution = Column(String, nullable=True)
    video_b_resolution = Column(String, nullable=True)
    video_a_size = Column(Float, nullable=True)
    video_b_size = Column(Float, nullable=True)
    preprocess_options = Column(JSON, nullable=True)

class ModelCallLog(Base):
    __tablename__ = "model_call_logs"
    id = Column(String(36), primary_key=True, default=generate_uuid, index=True)
    task_id = Column(String(36), index=True)
    task_name = Column(String, nullable=True)
    model_id = Column(String)
    model_url = Column(String)
    request_payload = Column(JSON, nullable=True)
    response_body = Column(JSON, nullable=True)
    started_at = Column(DateTime)
    ended_at = Column(DateTime)
    status_code = Column(String, nullable=True)
    input_tokens = Column(Float, nullable=True)
    output_tokens = Column(Float, nullable=True)

class PromptTemplate(Base):
    __tablename__ = "prompt_templates"
    id = Column(String(36), primary_key=True, default=generate_uuid, index=True)
    name = Column(String, index=True)
    content = Column(String)
    created_at = Column(DateTime, default=datetime.datetime.now)
    updated_at = Column(DateTime, default=datetime.datetime.now, onupdate=datetime.datetime.now)

class AIModel(Base):
    __tablename__ = "ai_models"
    id = Column(String(36), primary_key=True, default=generate_uuid, index=True)
    name = Column(String, index=True) 
    identifier = Column(String) 
    provider = Column(String) 
    api_key = Column(String, nullable=True)
    base_url = Column(String, nullable=True)
    description = Column(String, nullable=True)
    capabilities = Column(JSON, nullable=True) 
    is_default = Column(String, default="false")
    sort_order = Column(Float, default=0.0)
    created_at = Column(DateTime, default=datetime.datetime.now)
    updated_at = Column(DateTime, default=datetime.datetime.now, onupdate=datetime.datetime.now)

class TaskResult(Base):
    __tablename__ = "task_results"
    task_id = Column(String(36), ForeignKey("tasks.id"), primary_key=True)
    dimension_scores = Column(JSON, nullable=True)
    similar_points = Column(JSON, nullable=True)
    difference_points = Column(JSON, nullable=True)
    summary = Column(String, nullable=True)
    key_frames_a = Column(JSON, nullable=True)
    key_frames_b = Column(JSON, nullable=True)
    error_message = Column(String, nullable=True)
    input_tokens = Column(Float, nullable=True)
    output_tokens = Column(Float, nullable=True)