import re
import logging
from typing import List, Optional
from sqlalchemy import text
from database import Session
from models import KnowledgeChunk

logger = logging.getLogger(__name__)

MAX_CHUNKS      = 3    # keep prompt small for free tier
MAX_CHUNK_CHARS = 400  # truncate each chunk to limit tokens


def search_relevant_chunks(symptoms: str, species: Optional[str] = None, limit: int = MAX_CHUNKS) -> List[KnowledgeChunk]:
    """Retrieve the most relevant knowledge chunks."""
    db = Session()
    seen_ids = set()
    results  = []

    try:
        # 1. MySQL full-text search
        boolean_query = _build_boolean_query(symptoms)
        if boolean_query:
            rows = db.execute(
                text(
                    'SELECT * FROM knowledge_chunks '
                    'WHERE MATCH(content_text) AGAINST(:q IN BOOLEAN MODE) '
                    'LIMIT :lim'
                ),
                {'q': boolean_query, 'lim': limit}
            ).mappings().all()
            for row in rows:
                chunk = db.get(KnowledgeChunk, row['id'])
                if chunk and chunk.id not in seen_ids:
                    seen_ids.add(chunk.id)
                    results.append(chunk)

        # 2. Keyword fallback if not enough results
        if len(results) < limit:
            words = [w for w in symptoms.lower().split() if len(w) > 4][:3]
            for word in words:
                if len(results) >= limit:
                    break
                kw_chunks = db.query(KnowledgeChunk).filter(
                    KnowledgeChunk.keywords.like(f'%{word}%')
                ).limit(limit).all()
                for chunk in kw_chunks:
                    if chunk.id not in seen_ids and len(results) < limit:
                        seen_ids.add(chunk.id)
                        results.append(chunk)

        logger.info(f'Retrieved {len(results)} knowledge chunks')
        return results[:limit]

    except Exception as e:
        logger.error(f'RAG search error: {e}')
        return []
    finally:
        db.close()


def build_triage_prompt(symptoms: str, species: Optional[str], pet_name: Optional[str], chunks: List[KnowledgeChunk]) -> str:
    """Build a compact RAG prompt for Mistral."""
    lines = [
        'You are a veterinary triage assistant. Be concise.',
        'Use the context below to assess the pet emergency.',
        '',
        '--- KNOWLEDGE BASE ---',
    ]
    for i, chunk in enumerate(chunks, 1):
        # truncate content to keep token count low
        content = chunk.content_text[:MAX_CHUNK_CHARS]
        if len(chunk.content_text) > MAX_CHUNK_CHARS:
            content += '...'
        tag = f'[{i}] {chunk.topic}'
        if chunk.urgency_level:
            tag += f' ({chunk.urgency_level})'
        lines += [tag, content, '']

    lines += ['--- SYMPTOMS ---']
    if species:
        lines.append(f'Species: {species}')
    if pet_name:
        lines.append(f'Name: {pet_name}')
    lines.append(f'Symptoms: {symptoms}')
    lines += [
        '',
        'Respond with:',
        'URGENCY: [CRITICAL/URGENT/MODERATE/MILD]',
        'ASSESSMENT: (2-3 sentences)',
        'ACTION: (what owner should do now)',
    ]
    return '\n'.join(lines)


def parse_urgency(text: str) -> str:
    t = text.upper()
    if 'CRITICAL' in t: return 'CRITICAL'
    if 'URGENT'   in t: return 'URGENT'
    if 'MODERATE' in t: return 'MODERATE'
    if 'MILD'     in t: return 'MILD'
    return 'UNKNOWN'


def _build_boolean_query(text: str) -> str:
    words = re.findall(r'[a-z]{4,}', text.lower())
    return ' '.join(f'+{w}*' for w in words[:6])