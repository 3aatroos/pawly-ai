import os
from dotenv import load_dotenv
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, DeclarativeBase

load_dotenv()

DB_HOST     = os.getenv('DB_HOST', 'localhost')
DB_PORT     = os.getenv('DB_PORT', '3306')
DB_NAME     = os.getenv('DB_NAME', 'pawly_db')
DB_USER     = os.getenv('DB_USER', 'root')
DB_PASSWORD = os.getenv('DB_PASSWORD', '')

OLLAMA_URL         = os.getenv('OLLAMA_URL', 'http://localhost:11434/api/generate')
OLLAMA_MODEL       = os.getenv('OLLAMA_MODEL', 'mistral')
OLLAMA_TEMPERATURE = float(os.getenv('OLLAMA_TEMPERATURE', '0.3'))
OLLAMA_TIMEOUT     = int(os.getenv('OLLAMA_TIMEOUT', '120'))

DATABASE_URL = (
    f'mysql+pymysql://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}'
    '?charset=utf8mb4'
)

engine  = create_engine(DATABASE_URL, echo=False, pool_pre_ping=True)
Session = sessionmaker(bind=engine)

class Base(DeclarativeBase):
    pass