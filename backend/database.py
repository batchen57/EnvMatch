from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.ext.declarative import declarative_base

# PostgreSQL URL from user request (commented out for fallback)
# SQLALCHEMY_DATABASE_URL = "postgresql://postgres:123456@localhost:5432/kiray"
# Fallback to SQLite to allow flow testing when PostgreSQL is not running
SQLALCHEMY_DATABASE_URL = "sqlite:///./envmatch.db"

# SQLite requires check_same_thread=False
engine = create_engine(
    SQLALCHEMY_DATABASE_URL, connect_args={"check_same_thread": False}
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base = declarative_base()

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
