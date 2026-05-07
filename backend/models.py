import uuid
from sqlalchemy import Column, String, Float, DateTime, Enum, JSON, ForeignKey
from sqlalchemy.sql import func
from database import Base
import enum

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
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())
    input_tokens = Column(Float, nullable=True)
    output_tokens = Column(Float, nullable=True)

class PromptTemplate(Base):
    __tablename__ = "prompt_templates"

    id = Column(String(36), primary_key=True, default=generate_uuid, index=True)
    name = Column(String, index=True)
    content = Column(String)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())

class AIModel(Base):
    __tablename__ = "ai_models"

    id = Column(String(36), primary_key=True, default=generate_uuid, index=True)
    name = Column(String, index=True) # e.g., "Gemini 1.5 Pro"
    identifier = Column(String) # e.g., "gemini-1.5-pro"
    provider = Column(String) # e.g., "Google"
    api_key = Column(String, nullable=True)
    base_url = Column(String, nullable=True)
    description = Column(String, nullable=True)
    is_default = Column(String, default="false")
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())

class TaskResult(Base):
    __tablename__ = "task_results"

    task_id = Column(String(36), ForeignKey("tasks.id"), primary_key=True)
    dimension_scores = Column(JSON, nullable=True)
    similar_points = Column(JSON, nullable=True)
    difference_points = Column(JSON, nullable=True)
    summary = Column(String, nullable=True)
    key_frames_a = Column(JSON, nullable=True)
    key_frames_b = Column(JSON, nullable=True)
    input_tokens = Column(Float, nullable=True)
    output_tokens = Column(Float, nullable=True)
