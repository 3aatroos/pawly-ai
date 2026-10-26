"""Request and response models for the adoption Gemini routes.

Free-text fields reject an e-mail or a long digit run before the provider call.
Response models are the contract checked again on the provider JSON.
"""

from __future__ import annotations

import re
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

Gender = Literal["mâle", "femelle"]
Attention = Literal["low", "medium", "high"]

_EMAIL = re.compile(r"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}")
_LONG_DIGITS = re.compile(r"\d{8,}")
_VERDICT = re.compile(
    r"(candidature|dossier)\s+(est\s+)?(refus|accept)"
    r"|refus automatique"
    r"|accepter la candidature"
    r"|refuser la candidature",
    re.IGNORECASE,
)


def reject_personal_data(value: str) -> str:
    if _EMAIL.search(value) or _LONG_DIGITS.search(value):
        raise ValueError("personal data is not accepted")
    return value.strip()


def _optional_text(value: str | None, limit: int) -> str | None:
    if value is None:
        return None
    text = reject_personal_data(value)
    if not text:
        return None
    if len(text) > limit:
        raise ValueError("text is too long")
    return text


def _required_text(value: str, limit: int) -> str:
    text = reject_personal_data(value)
    if not text:
        raise ValueError("text is empty")
    if len(text) > limit:
        raise ValueError("text is too long")
    return text


class DraftRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    notes: str = Field(min_length=1, max_length=4000)
    photo_description: str | None = Field(default=None, max_length=2000)

    @field_validator("notes")
    @classmethod
    def clean_notes(cls, value: str) -> str:
        return _required_text(value, 4000)

    @field_validator("photo_description")
    @classmethod
    def clean_photo(cls, value: str | None) -> str | None:
        return _optional_text(value, 2000)


class LifestyleIn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    housing: str | None = None
    has_garden: bool | None = None
    hours_away_per_day: int | None = Field(default=None, ge=0, le=24)
    other_pets: bool | None = None
    children: bool | None = None
    experience: str | None = None
    sector: str | None = None

    @field_validator("housing", "experience", "sector")
    @classmethod
    def clean_label(cls, value: str | None) -> str | None:
        return _optional_text(value, 80)


class AnimalCardIn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: int = Field(gt=0)
    species: str | None = None
    breed: str | None = None
    age: str | None = None
    personality: str | None = None
    special_needs: str | None = None
    location: str | None = None

    @field_validator("id", mode="before")
    @classmethod
    def id_is_int(cls, value: object) -> object:
        if isinstance(value, bool):
            raise ValueError("invalid id")
        return value

    @field_validator("species", "breed", "age", "location")
    @classmethod
    def clean_short(cls, value: str | None) -> str | None:
        return _optional_text(value, 80)

    @field_validator("personality", "special_needs")
    @classmethod
    def clean_long(cls, value: str | None) -> str | None:
        return _optional_text(value, 500)


class MatchRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    lifestyle: LifestyleIn
    posts: list[AnimalCardIn] = Field(max_length=20)


class AnimalProfileIn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str | None = None
    species: str | None = None
    breed: str | None = None
    age: str | None = None
    gender: str | None = None
    personality: str | None = None
    description: str | None = None
    special_needs: str | None = None
    location: str | None = None

    @field_validator("name", "species", "breed", "age", "gender", "location")
    @classmethod
    def clean_short(cls, value: str | None) -> str | None:
        return _optional_text(value, 80)

    @field_validator("personality", "description", "special_needs")
    @classmethod
    def clean_long(cls, value: str | None) -> str | None:
        return _optional_text(value, 1500)


class ApplicationFormIn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    housing: str | None = None
    has_garden: bool | None = None
    hours_away_per_day: int | None = Field(default=None, ge=0, le=24)
    other_pets: bool | None = None
    children: bool | None = None
    experience: str | None = None
    motivation: str | None = None

    @field_validator("housing", "experience")
    @classmethod
    def clean_label(cls, value: str | None) -> str | None:
        return _optional_text(value, 80)

    @field_validator("motivation")
    @classmethod
    def clean_motivation(cls, value: str | None) -> str | None:
        return _optional_text(value, 2000)


class ReviewRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    animal: AnimalProfileIn
    form: ApplicationFormIn


def _no_contact(value: str) -> str:
    if _EMAIL.search(value) or _LONG_DIGITS.search(value):
        raise ValueError("response contains personal data")
    return value.strip()


class DraftResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str | None = Field(max_length=80)
    species: str | None = Field(max_length=80)
    breed: str | None = Field(max_length=80)
    age: str | None = Field(max_length=80)
    gender: Gender | None
    personality_tags: list[str] = Field(max_length=12)
    description: str = Field(max_length=2000)
    special_needs: str | None = Field(max_length=500)
    uncertainties: list[str] = Field(max_length=12)

    @field_validator("name", "species", "breed", "age", "special_needs", mode="before")
    @classmethod
    def blank_to_none(cls, value: object) -> object:
        if value is None:
            return None
        if not isinstance(value, str):
            raise ValueError("invalid text")
        text = _no_contact(value)
        return text or None

    @field_validator("gender", mode="before")
    @classmethod
    def normalize_gender(cls, value: object) -> object:
        if value is None:
            return None
        if not isinstance(value, str):
            raise ValueError("invalid gender")
        text = value.strip().lower()
        if text in ("", "null", "none"):
            return None
        if text in ("mâle", "male"):
            return "mâle"
        if text in ("femelle", "female"):
            return "femelle"
        raise ValueError("invalid gender")

    @field_validator("description")
    @classmethod
    def clean_description(cls, value: str) -> str:
        return _no_contact(value)

    @field_validator("personality_tags", "uncertainties")
    @classmethod
    def clean_items(cls, values: list[str]) -> list[str]:
        cleaned: list[str] = []
        for value in values:
            if not isinstance(value, str):
                raise ValueError("invalid item")
            text = _no_contact(value)
            if not text:
                continue
            if len(text) > 200:
                raise ValueError("item too long")
            cleaned.append(text)
        return cleaned


class MatchItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    post_id: int = Field(gt=0)
    score: int = Field(ge=0, le=100)
    reason: str = Field(min_length=1, max_length=400)
    warning: str | None = Field(default=None, max_length=400)

    @field_validator("post_id", "score", mode="before")
    @classmethod
    def reject_bool(cls, value: object) -> object:
        if isinstance(value, bool):
            raise ValueError("invalid number")
        return value

    @field_validator("reason")
    @classmethod
    def clean_reason(cls, value: str) -> str:
        text = _no_contact(value)
        if not text:
            raise ValueError("reason is empty")
        return text

    @field_validator("warning", mode="before")
    @classmethod
    def clean_warning(cls, value: object) -> object:
        if value is None:
            return None
        if not isinstance(value, str):
            raise ValueError("invalid warning")
        text = _no_contact(value)
        return text or None


class MatchResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    matches: list[MatchItem] = Field(max_length=5)

    @model_validator(mode="after")
    def sorted_unique(self) -> "MatchResponse":
        ids = [item.post_id for item in self.matches]
        if len(ids) != len(set(ids)):
            raise ValueError("duplicate post_id")
        self.matches = sorted(self.matches, key=lambda item: (-item.score, item.post_id))
        return self


class ReviewResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    summary: str = Field(min_length=1, max_length=2000)
    points_to_verify: list[str] = Field(max_length=12)
    suggested_attention: Attention

    @field_validator("suggested_attention", mode="before")
    @classmethod
    def lower_attention(cls, value: object) -> object:
        if isinstance(value, str):
            return value.strip().lower()
        return value

    @field_validator("summary")
    @classmethod
    def clean_summary(cls, value: str) -> str:
        return _no_contact(value)

    @field_validator("points_to_verify")
    @classmethod
    def clean_points(cls, values: list[str]) -> list[str]:
        cleaned: list[str] = []
        for value in values:
            if not isinstance(value, str):
                raise ValueError("invalid item")
            text = _no_contact(value)
            if not text:
                continue
            if len(text) > 300:
                raise ValueError("item too long")
            cleaned.append(text)
        return cleaned

    @model_validator(mode="after")
    def no_automatic_decision(self) -> "ReviewResponse":
        blob = " ".join([self.summary, *self.points_to_verify])
        if _VERDICT.search(blob):
            raise ValueError("automatic decision is not allowed")
        return self
