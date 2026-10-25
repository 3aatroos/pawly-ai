"""Strict request and response models for recommendation scoring.

The payload is a catalogue context: species, age band, budget tier, product
attributes, and interaction signals. Identity fields are rejected.
"""

from __future__ import annotations

import re
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

BudgetTier = Literal["budget", "mid", "premium", "unknown"]
Signal = Literal["view", "cart", "purchase", "review_pos", "review_neg"]
RelevanceClass = Literal["not_relevant", "somewhat", "relevant", "highly_relevant"]
ScoreSource = Literal["qlora", "content_based"]

_EMAIL = re.compile(r"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}")
_LONG_DIGITS = re.compile(r"\d{8,}")
_PRODUCT_ID = re.compile(r"^[A-Za-z0-9_\-]{1,32}$")


def reject_personal_data(value: str) -> str:
    if _EMAIL.search(value) or _LONG_DIGITS.search(value):
        raise ValueError("personal data is not accepted")
    return value


class ProfileIn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    pet_species: list[str] = Field(default_factory=list, max_length=6)
    pet_ages: list[str] = Field(default_factory=list, max_length=6)
    budget_tier: BudgetTier = "unknown"

    @field_validator("pet_species", "pet_ages")
    @classmethod
    def clean_tokens(cls, values: list[str]) -> list[str]:
        cleaned = []
        for value in values:
            text = reject_personal_data(str(value).strip().lower())[:40]
            if text:
                cleaned.append(text)
        return cleaned


class HistoryIn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    product_id: str = Field(pattern=_PRODUCT_ID.pattern)
    product_name: str = Field(min_length=1, max_length=200)
    signal: Signal
    weight: float = Field(ge=-1, le=1)
    category: str = Field(default="", max_length=80)
    species: str = Field(default="", max_length=40)

    @field_validator("product_name", "category", "species")
    @classmethod
    def no_personal_data(cls, value: str) -> str:
        return reject_personal_data(value.strip())


class CandidateIn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    product_id: str = Field(pattern=_PRODUCT_ID.pattern)
    name: str = Field(min_length=1, max_length=200)
    category: str = Field(default="", max_length=80)
    species: str = Field(default="", max_length=40)
    price_eur: float = Field(ge=0, le=100_000)
    description: str = Field(default="", max_length=2000)
    avg_rating: float = Field(default=0, ge=0, le=5)
    review_count: int = Field(default=0, ge=0, le=1_000_000)

    @field_validator("name", "category", "species", "description")
    @classmethod
    def no_personal_data(cls, value: str) -> str:
        return reject_personal_data(value.strip())


class ScoreRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    profile: ProfileIn
    history: list[HistoryIn] = Field(default_factory=list, max_length=10)
    candidates: list[CandidateIn] = Field(min_length=1, max_length=24)
    limit: int = Field(default=8, ge=1, le=12)


class RecommendationOut(BaseModel):
    model_config = ConfigDict(extra="forbid")

    product_id: str
    score: float = Field(ge=0, le=1)
    relevance_class: RelevanceClass
    justification: str = Field(min_length=1, max_length=400)


class ScoreResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    source: ScoreSource
    recommendations: list[RecommendationOut]
