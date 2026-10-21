import logging
import os
from datetime import datetime
from typing import Optional, List
from fastapi import FastAPI, HTTPException, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from database import engine
from models import Base, KnowledgeChunk
from rag_service import search_relevant_chunks, build_triage_prompt, parse_urgency
from mistral_client import call_mistral
from ingestion import ingest_pdf_bytes, seed_knowledge_base
from sqlalchemy.orm import Session as OrmSession

logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(name)s: %(message)s')
logger = logging.getLogger(__name__)

# Folder where PDF knowledge base files are stored
KNOWLEDGE_DIR = os.path.join(os.path.dirname(__file__), 'knowledge')

# ── App Setup ────────────────────────────────────────────────────────────────
app = FastAPI(
    title='ai-pawly',
    description='AI triage microservice for Pawly - RAG + Mistral via Ollama',
    version='1.0.0',
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=['http://localhost:8081', 'http://localhost:4200'],
    allow_methods=['*'],
    allow_headers=['*'],
)

# ── Startup ──────────────────────────────────────────────────────────────────
@app.on_event('startup')
def on_startup():
    # 1. Ensure DB tables exist
    Base.metadata.create_all(bind=engine)
    logger.info('Database tables verified/created')

    # 2. Check if knowledge base is already populated
    db = OrmSession(engine)
    try:
        existing = db.query(KnowledgeChunk).count()
    finally:
        db.close()

    if existing > 0:
        logger.info(f'Knowledge base already has {existing} chunks - skipping auto-ingest.')
        return

    # 3. Try to ingest PDFs from the knowledge/ folder
    pdf_files = [f for f in os.listdir(KNOWLEDGE_DIR) if f.endswith('.pdf')] if os.path.isdir(KNOWLEDGE_DIR) else []

    if pdf_files:
        total = 0
        for pdf_file in pdf_files:
            pdf_path = os.path.join(KNOWLEDGE_DIR, pdf_file)
            logger.info(f'Auto-ingesting PDF: {pdf_file}')
            with open(pdf_path, 'rb') as f:
                count = ingest_pdf_bytes(f.read(), source_name=pdf_file)
                total += count
        logger.info(f'Auto-ingested {total} chunks from {len(pdf_files)} PDF(s) in knowledge/')
    else:
        # 4. Fall back to built-in seed data
        logger.info('No PDFs found in knowledge/ - loading built-in triage knowledge base...')
        count = seed_knowledge_base()
        logger.info(f'Built-in seed loaded: {count} chunks')


# ── Schemas ──────────────────────────────────────────────────────────────────
class TriageRequest(BaseModel):
    symptoms: str  = Field(..., min_length=10, max_length=2000)
    species:  Optional[str] = None
    pet_name: Optional[str] = None
    pet_id:   Optional[int] = None

class TriageResponse(BaseModel):
    urgency_level:     str
    assessment:        str
    knowledge_sources: List[str]
    timestamp:         datetime
    ai_generated:      bool = True

class ChunkOut(BaseModel):
    id:            int
    topic:         str
    species:       Optional[str]
    urgency_level: Optional[str]
    keywords:      Optional[str]
    content_text:  str
    source:        str


# ── Endpoints ────────────────────────────────────────────────────────────────
@app.get('/health')
def health():
    db = OrmSession(engine)
    try:
        chunk_count = db.query(KnowledgeChunk).count()
    finally:
        db.close()
    return {
        'status': 'ok',
        'service': 'ai-pawly',
        'knowledge_chunks': chunk_count,
        'timestamp': datetime.now().isoformat(),
    }


@app.post('/triage', response_model=TriageResponse)
def perform_triage(req: TriageRequest):
    logger.info(f'Triage: species={req.species}, symptoms={req.symptoms[:60]}...')
    chunks     = search_relevant_chunks(req.symptoms, req.species)
    prompt     = build_triage_prompt(req.symptoms, req.species, req.pet_name, chunks)
    assessment = call_mistral(prompt)
    sources    = list(dict.fromkeys(c.topic for c in chunks))
    return TriageResponse(
        urgency_level=parse_urgency(assessment),
        assessment=assessment,
        knowledge_sources=sources,
        timestamp=datetime.now(),
    )


@app.post('/ingest/seed')
def seed_kb():
    count = seed_knowledge_base()
    if count == 0:
        return {'message': 'Knowledge base already populated - no changes made.'}
    return {'message': f'Seeded {count} chunks from built-in knowledge base.'}


@app.post('/ingest/pdf')
async def ingest_pdf(file: UploadFile = File(...)):
    if not file.filename.endswith('.pdf'):
        raise HTTPException(status_code=400, detail='Only PDF files are supported.')
    pdf_bytes = await file.read()
    count = ingest_pdf_bytes(pdf_bytes, source_name=file.filename)
    return {'message': f'Ingested {count} chunks from {file.filename}'}


@app.get('/chunks', response_model=List[ChunkOut])
def list_chunks(limit: int = 100, offset: int = 0):
    db = OrmSession(engine)
    try:
        chunks = db.query(KnowledgeChunk).offset(offset).limit(limit).all()
        return [ChunkOut(**c.to_dict()) for c in chunks]
    finally:
        db.close()

if __name__ == '__main__':
    import uvicorn
    port = int(os.getenv('AI_SERVICE_PORT', '8000'))
    uvicorn.run('main:app', host='0.0.0.0', port=port, reload=False)
