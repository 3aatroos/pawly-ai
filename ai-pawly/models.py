from sqlalchemy import Column, BigInteger, String, Text, DateTime, func
from sqlalchemy.dialects.mysql import LONGTEXT, JSON
from database import Base

class KnowledgeChunk(Base):
    __tablename__ = 'knowledge_chunks'

    id            = Column(BigInteger, primary_key=True, autoincrement=True)
    topic         = Column(String(255), nullable=False)
    species       = Column(String(100), nullable=True)
    urgency_level = Column(String(50),  nullable=True)
    keywords      = Column(Text,         nullable=True)
    content_text  = Column(LONGTEXT,     nullable=False)
    source        = Column(String(255),  nullable=False, default='Pet Emergency Triage KB')
    reviewed_date = Column(DateTime,     nullable=True)
    metadata_json = Column(JSON,         nullable=True)
    created_at    = Column(DateTime,     server_default=func.now())
    updated_at    = Column(DateTime,     server_default=func.now(), onupdate=func.now())

    def to_dict(self):
        return {
            'id':            self.id,
            'topic':         self.topic,
            'species':       self.species,
            'urgency_level': self.urgency_level,
            'keywords':      self.keywords,
            'content_text':  self.content_text,
            'source':        self.source,
        }