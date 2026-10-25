import io
import logging
from datetime import datetime
from typing import Optional
from database import Session
from models import KnowledgeChunk

logger = logging.getLogger(__name__)


def ingest_pdf_bytes(pdf_bytes: bytes, source_name: str = 'Uploaded PDF') -> int:
    """Parse a PDF from bytes and store chunks in MySQL. Returns number of chunks saved."""
    try:
        import PyPDF2
    except ImportError:
        raise RuntimeError('PyPDF2 is not installed. Run: pip install PyPDF2')

    reader = PyPDF2.PdfReader(io.BytesIO(pdf_bytes))
    raw_text = ''
    for page in reader.pages:
        raw_text += page.extract_text() + '\n'

    chunks = _split_into_chunks(raw_text, source_name)
    logger.info(f'PDF parsed into {len(chunks)} chunks from {len(reader.pages)} pages')

    db = Session()
    try:
        db.add_all(chunks)
        db.commit()
        logger.info(f'Saved {len(chunks)} chunks from {source_name}')
        return len(chunks)
    except Exception as e:
        db.rollback()
        logger.error(f'Failed to save PDF chunks: {e}')
        raise
    finally:
        db.close()


def seed_knowledge_base() -> int:
    """Seed the built-in triage knowledge base. Idempotent - skips if chunks exist."""
    db = Session()
    try:
        count = db.query(KnowledgeChunk).count()
        if count > 0:
            logger.info(f'Knowledge base already has {count} chunks - skipping seed.')
            return 0
        chunks = _build_seed_chunks()
        db.add_all(chunks)
        db.commit()
        logger.info(f'Seeded {len(chunks)} knowledge chunks')
        return len(chunks)
    except Exception as e:
        db.rollback()
        logger.error(f'Seed failed: {e}')
        raise
    finally:
        db.close()


def _split_into_chunks(text: str, source: str, chunk_size: int = 800) -> list[KnowledgeChunk]:
    """Split raw text into overlapping chunks for RAG ingestion."""
    paragraphs = [p.strip() for p in text.split('\n\n') if len(p.strip()) > 100]
    chunks = []
    buffer = ''
    for para in paragraphs:
        if len(buffer) + len(para) < chunk_size:
            buffer += '\n\n' + para
        else:
            if buffer.strip():
                chunks.append(_make_chunk(buffer.strip(), source))
            buffer = para
    if buffer.strip():
        chunks.append(_make_chunk(buffer.strip(), source))
    return chunks


def _make_chunk(text: str, source: str, topic: str = 'General') -> KnowledgeChunk:
    c = KnowledgeChunk()
    c.topic        = topic
    c.content_text = text
    c.source       = source
    c.reviewed_date = datetime.now()
    return c


def _build_seed_chunks() -> list[KnowledgeChunk]:
    """Hard-coded seed data extracted from the Pet Emergency Triage PDF."""
    rd = datetime(2024, 10, 1)
    data = [
        ('Urgency Classification', None, 'CRITICAL', 'critical,emergency,life-threatening',
         'CRITICAL - go to vet immediately:\n- Difficulty breathing or gasping\n- Collapse or inability to stand\n- Seizures\n- Suspected poisoning\n- Severe trauma (hit by car, animal attack)\n- Uncontrolled bleeding\n- Pale, white, blue, or grey gums\n- Swollen abdomen with distress (bloat/GDV)\n- Urinary blockage (male cats - fatal within hours)'),
        ('Urgency Classification', None, 'URGENT', 'urgent,hours,same-day',
         'URGENT - see vet within a few hours:\n- Vomiting >3 times in 24h\n- Diarrhea with blood or >24h\n- Not eating >24h (cats) or >48h (dogs)\n- Limping/inability to bear weight\n- Sudden behavioral changes\n- Straining to urinate with little output'),
        ('Urgency Classification', None, 'MODERATE', 'moderate,monitor,24-48 hours',
         'MODERATE - schedule vet visit within 24-48 hours:\n- Mild vomiting 1-2 times\n- Mild diarrhea (no blood) <24h\n- Slight appetite decrease\n- Minor cuts or scrapes\n- Mild limping with full weight bearing'),
        ('Emergency Red Flags', None, 'CRITICAL', 'red flags,gums,breathing,collapse',
         'GUM COLOR CHANGES:\n- Pale/white = shock, anemia, internal bleeding\n- Blue/purple (cyanosis) = lack of oxygen\n- Yellow = liver disease\n- Grey = extreme shock\n\nBREATHING EMERGENCIES:\n- Open-mouth breathing in cats (always emergency)\n- Labored breathing, belly breathing\n\nNEUROLOGICAL:\n- Seizures >2 minutes\n- Sudden paralysis or dragging hind limbs\n\nGI EMERGENCIES:\n- Bloated hard abdomen with retching, no vomit (GDV dogs)\n- Bloody diarrhea with weakness'),
        ('Dog Emergency Signs', 'dog', 'CRITICAL', 'dog,bloat,GDV,parvovirus,heat stroke',
         'BLOAT/GDV: distended abdomen, unproductive retching, restlessness - FATAL within hours, needs emergency surgery.\nPARVOVIRUS: bloody diarrhea, vomiting, lethargy, fever - isolate dog, seek vet immediately.\nHEAT STROKE: panting, drooling, collapse, temp >104F - cool with wet towels, vet immediately.'),
        ('Cat Emergency Signs', 'cat', 'CRITICAL', 'cat,urinary blockage,open-mouth breathing',
         'URINARY BLOCKAGE (male cats): straining to urinate, crying in litter box, distended bladder - FATAL within 24-48h.\nOPEN-MOUTH BREATHING: always emergency in cats - indicates heart failure or respiratory disease.\nFELINE HEPATIC LIPIDOSIS: cats not eating 2-3 days develop fatty liver - jaundice, vomiting, lethargy.'),
        ('First Aid - Bleeding', None, 'CRITICAL', 'first aid,bleeding,wound,pressure',
         '1. Apply firm pressure with clean cloth for 5-10 min\n2. Do NOT remove soaked cloth - add more on top\n3. Emergency vet for: spurting blood, wounds not stopping after 10 min, deep punctures, wounds near eyes/chest/abdomen'),
        ('First Aid - Seizures', None, 'CRITICAL', 'first aid,seizure,convulsion',
         '1. Do NOT put anything in mouth\n2. Clear sharp objects around pet\n3. Time the seizure\n4. Do NOT restrain\n5. Call vet if: >2 min, multiple in 24h, first-ever seizure, any seizure in a cat'),
        ('First Aid - Poisoning', None, 'CRITICAL', 'poisoning,toxic,xylitol,chocolate,grapes,antifreeze,lilies',
         'Toxins: xylitol (dogs - hypoglycemia), grapes/raisins (kidney failure), chocolate (cardiac), lilies (cats - FATAL kidney failure), antifreeze (FATAL), permethrin dog flea products (cats - FATAL).\n1. Do NOT induce vomiting without vet instruction\n2. Save the packaging\n3. Note time and quantity\n4. Go to emergency vet immediately'),
        ('Medication Safety', None, 'CRITICAL', 'medication,drugs,toxic,ibuprofen,acetaminophen',
         'NEVER give dogs: Ibuprofen, Naproxen, Acetaminophen.\nNEVER give cats (even more sensitive): Acetaminophen (FATAL), Aspirin, Ibuprofen, Permethrin flea products (FATAL neurological toxicity).\nIf ingested: call vet immediately with product name and dose.'),
        ('Intake Questions', None, None, 'intake,assessment,history,triage',
         'Ask: species, breed, age, sex, weight, vaccines. When did symptoms start? Getting better or worse? Vomiting/diarrhea (blood?)? Eating/drinking normally? Exposure to toxins or foreign objects? Any trauma?\nVital signs: gum color (pink=normal), breathing rate 15-30/min, temp 100-102.5F normal for dogs and cats.'),
        ('AI Triage Disclaimer', None, None, 'disclaimer,limitations,AI,consult vet',
         'This AI CAN: assess urgency, provide first-aid guidance. This AI CANNOT: replace physical exam, diagnose conditions, prescribe medications.\nALWAYS consult a licensed veterinarian for serious symptoms and treatment decisions. When in doubt, seek vet care.'),
    ]
    chunks = []
    for topic, species, urgency, keywords, content in data:
        c = KnowledgeChunk()
        c.topic         = topic
        c.species       = species
        c.urgency_level = urgency
        c.keywords      = keywords
        c.content_text  = content
        c.source        = 'Pet Emergency Triage Knowledge Base'
        c.reviewed_date = rd
        chunks.append(c)
    return chunks