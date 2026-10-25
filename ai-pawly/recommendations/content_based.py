"""
Content-based fallback used when QLoRA is disabled, times out, or returns
an unusable answer. Weights stay aligned with the Java ContentBasedRanker.
"""

from __future__ import annotations

import math
import re

from recommendations.schemas import CandidateIn, RecommendationOut, ScoreRequest

_TOKEN = re.compile(r"[a-z0-9]+")
_POSITIVE = {"view", "cart", "purchase", "review_pos"}

BASE = 0.15
SPECIES_MATCH = 0.35
SPECIES_MISMATCH = -0.25
CATEGORY_MATCH = 0.20
CONTENT_MATCH = 0.25
RATING_BONUS = 0.10
PRIOR_POSITIVE = 0.10
PRIOR_NEGATIVE = -0.20


def score_to_class(score: float) -> str:
    if score <= 0.25:
        return "not_relevant"
    if score <= 0.50:
        return "somewhat"
    if score <= 0.75:
        return "relevant"
    return "highly_relevant"


def _tokens(text: str) -> dict[str, float]:
    counts: dict[str, float] = {}
    for token in _TOKEN.findall(text.lower()):
        counts[token] = counts.get(token, 0.0) + 1.0
    return counts


def _cosine(left: dict[str, float], right: dict[str, float]) -> float:
    if not left or not right:
        return 0.0
    dot = sum(weight * right.get(token, 0.0) for token, weight in left.items())
    left_norm = math.sqrt(sum(weight * weight for weight in left.values()))
    right_norm = math.sqrt(sum(weight * weight for weight in right.values()))
    if left_norm == 0.0 or right_norm == 0.0:
        return 0.0
    return dot / (left_norm * right_norm)


def _clamp(value: float) -> float:
    return round(min(1.0, max(0.0, value)), 4)


def _species_matches(candidate: CandidateIn, species: set[str]) -> bool | None:
    target = (candidate.species or "").strip().lower()
    if not target or target == "all" or not species:
        return None
    return target in species


def rank(request: ScoreRequest) -> list[RecommendationOut]:
    species = {item.strip().lower() for item in request.profile.pet_species if item.strip()}
    positive = [item for item in request.history if item.signal in _POSITIVE and item.weight > 0]
    negative_ids = {item.product_id for item in request.history if item.signal == "review_neg"}
    positive_ids = {item.product_id for item in positive}
    categories = {(item.category or "").strip().lower() for item in positive if item.category}
    history_text = " ".join(
        " ".join(part for part in (item.product_name, item.category, item.species) if part)
        for item in positive
    )
    history_vector = _tokens(history_text)
    ranked: list[RecommendationOut] = []

    for candidate in request.candidates:
        notes: list[str] = []
        score = BASE
        matched = _species_matches(candidate, species)
        if matched is True:
            score += SPECIES_MATCH
            notes.append(f"Species match ({candidate.species.strip().lower()}).")
        elif matched is False:
            score += SPECIES_MISMATCH
            notes.append(f"Species differs from the profile ({candidate.species.strip().lower()}).")

        category = (candidate.category or "").strip().lower()
        if category and category in categories:
            score += CATEGORY_MATCH
            notes.append("Category matches recent interest.")

        content = _cosine(
            _tokens(" ".join((candidate.name, candidate.description, candidate.category))),
            history_vector,
        )
        if content > 0:
            score += CONTENT_MATCH * content
            notes.append("Similar to recently considered products.")

        if candidate.avg_rating > 0:
            score += RATING_BONUS * (candidate.avg_rating / 5.0)
            notes.append(f"Rating {candidate.avg_rating:.1f}/5.")

        if candidate.product_id in positive_ids:
            score += PRIOR_POSITIVE
            notes.append("Already considered by the shopper.")
        if candidate.product_id in negative_ids:
            score += PRIOR_NEGATIVE
            notes.append("Previous feedback was negative.")

        if not notes:
            notes.append("Popular catalogue fallback.")
        justification = " ".join(notes)
        words = justification.split()
        if len(words) > 40:
            justification = " ".join(words[:40])
        value = _clamp(score)
        ranked.append(
            RecommendationOut(
                product_id=candidate.product_id,
                score=value,
                relevance_class=score_to_class(value),
                justification=justification,
            )
        )

    ranked.sort(key=lambda item: (-item.score, item.product_id))
    return ranked[: request.limit]
