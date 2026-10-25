"""Keyword RAG for the adoption feed.

Chunks are fictional shelter rules. Retrieval is a cosine similarity over word
counts, then the top excerpts are inserted into the Gemini prompt. This module
does not read the triage knowledge base and does not call Mistral.
"""

from __future__ import annotations

import math
import re
from collections import Counter

_TOKEN = re.compile(r"[a-zàâäéèêëïîôùûüç0-9]{3,}")
MAX_CHUNKS = 2

CHUNKS: list[dict[str, str]] = [
    {
        "id": "vaccin",
        "topic": "Statut vaccinal",
        "text": (
            "Le refuge fictif de Tunis n'inscrit un vaccin que s'il est écrit dans les notes. "
            "Sinon le champ reste vide et l'incertitude « statut vaccinal non indiqué » est ajoutée."
        ),
    },
    {
        "id": "pelage-race",
        "topic": "Race et pelage",
        "text": (
            "La couleur du pelage, par exemple roux ou rousse, ne devient pas une race. "
            "Si la race n'est pas écrite, le champ race reste null."
        ),
    },
    {
        "id": "chat-appartement",
        "topic": "Chat et appartement",
        "text": (
            "Un chat adulte calme peut vivre en appartement. "
            "Des absences longues sont un point à confirmer, pas un refus de candidature."
        ),
    },
    {
        "id": "chien-absences",
        "topic": "Chien, jardin et absences",
        "text": (
            "Un jeune chien qui demande des sorties longues est en tension avec un appartement "
            "sans jardin et plusieurs heures d'absence. Le score reste bas et un avertissement est écrit."
        ),
    },
    {
        "id": "decision-humaine",
        "topic": "Décision du refuge",
        "text": (
            "Le modèle ne refuse pas et n'accepte pas une candidature. "
            "Il prépare un résumé et un niveau d'attention. Le refuge décide."
        ),
    },
]


def search_adoption_chunks(query: str, limit: int = MAX_CHUNKS) -> list[dict[str, str]]:
    """Return the closest shelter rules. An empty query returns no chunk."""
    query_counts = _counts(query)
    if not query_counts:
        return []
    ranked = []
    for chunk in CHUNKS:
        score = _cosine(query_counts, _counts(f"{chunk['topic']} {chunk['text']}"))
        if score > 0:
            ranked.append((score, chunk["id"], chunk))
    ranked.sort(key=lambda item: (-item[0], item[1]))
    return [chunk for _score, _chunk_id, chunk in ranked[:limit]]


def format_adoption_context(chunks: list[dict[str, str]]) -> str:
    """Build the block inserted before the user data."""
    if not chunks:
        return ""
    lines = ["--- CONTEXTE REFUGE ---"]
    for index, chunk in enumerate(chunks, start=1):
        lines.append(f"[{index}] {chunk['topic']}")
        lines.append(chunk["text"])
    lines.append("--- FIN CONTEXTE ---")
    return "\n".join(lines)


def augment_user_text(user_text: str, query: str) -> tuple[str, list[str]]:
    """Prepend the retrieved rules. The user text stays data, after the context."""
    chunks = search_adoption_chunks(query)
    context = format_adoption_context(chunks)
    if not context:
        return user_text, []
    return f"{context}\n\n{user_text}", [chunk["id"] for chunk in chunks]


def _counts(text: str) -> Counter[str]:
    return Counter(_TOKEN.findall(text.lower()))


def _cosine(left: Counter[str], right: Counter[str]) -> float:
    if not left or not right:
        return 0.0
    dot = sum(left[token] * right[token] for token in left if token in right)
    left_norm = math.sqrt(sum(value * value for value in left.values()))
    right_norm = math.sqrt(sum(value * value for value in right.values()))
    if left_norm == 0 or right_norm == 0:
        return 0.0
    return dot / (left_norm * right_norm)
