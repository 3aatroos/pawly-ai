"""Adoption routes. Spring is the only caller. Gemini never changes a status."""

from __future__ import annotations

import logging

from fastapi import APIRouter, HTTPException
from pydantic import ValidationError

from adoption_prompts import (
    DRAFT_FEW_SHOT_MODEL,
    DRAFT_FEW_SHOT_USER,
    DRAFT_POST_SYSTEM_PROMPT,
    DRAFT_TEMPERATURE,
    MATCH_FEW_SHOT_MODEL,
    MATCH_FEW_SHOT_USER,
    MATCH_SYSTEM_PROMPT,
    MATCH_TEMPERATURE,
    REVIEW_APPLICATION_SYSTEM_PROMPT,
    REVIEW_FEW_SHOT_MODEL,
    REVIEW_FEW_SHOT_USER,
    REVIEW_TEMPERATURE,
    draft_user_text,
    match_user_text,
    review_user_text,
)
from adoption_schemas import (
    DraftRequest,
    DraftResponse,
    MatchRequest,
    MatchResponse,
    ReviewRequest,
    ReviewResponse,
)
from adoption_rag import augment_user_text
from gemini_client import GeminiError, generate_json

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/adoption", tags=["adoption"])

SCHEMA_MISMATCH = "AI service returned a JSON payload that does not match the expected schema."
UNEXPECTED = "AI service returned an unexpected error."


def _schema_error(exc: ValidationError) -> HTTPException:
    fields = [".".join(str(part) for part in err.get("loc", ())) for err in exc.errors()]
    logger.warning("adoption schema mismatch fields=%s", fields)
    return HTTPException(status_code=502, detail=SCHEMA_MISMATCH)


def _run(raw_loader, parser):
    try:
        raw = raw_loader()
        return parser(raw)
    except GeminiError as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.message) from None
    except ValidationError as exc:
        raise _schema_error(exc) from None
    except HTTPException:
        raise
    except Exception as exc:
        logger.error("adoption call failed error_type=%s", type(exc).__name__)
        raise HTTPException(status_code=500, detail=UNEXPECTED) from None


@router.post("/draft", response_model=DraftResponse)
def draft_post(body: DraftRequest) -> DraftResponse:
    logger.info(
        "adoption draft notes_chars=%s photo=%s",
        len(body.notes),
        bool(body.photo_description),
    )
    user_text, sources = augment_user_text(
        draft_user_text(body.notes, body.photo_description),
        " ".join(part for part in (body.notes, body.photo_description) if part),
    )
    logger.info("adoption draft rag sources=%s", sources)

    def load() -> dict:
        return generate_json(
            system_prompt=DRAFT_POST_SYSTEM_PROMPT,
            user_text=user_text,
            temperature=DRAFT_TEMPERATURE,
            few_shot_user=DRAFT_FEW_SHOT_USER,
            few_shot_model=DRAFT_FEW_SHOT_MODEL,
        )

    return _run(load, DraftResponse.model_validate)


@router.post("/match", response_model=MatchResponse)
def match_posts(body: MatchRequest) -> MatchResponse:
    logger.info("adoption match posts=%s", len(body.posts))
    allowed = {post.id for post in body.posts}
    lifestyle = body.lifestyle.model_dump()
    posts = [post.model_dump() for post in body.posts]
    user_text, sources = augment_user_text(
        match_user_text(lifestyle, posts),
        " ".join(
            str(value)
            for value in (
                lifestyle.get("housing"),
                lifestyle.get("sector"),
                lifestyle.get("experience"),
                *(post.get("species") for post in posts),
                *(post.get("special_needs") for post in posts),
            )
            if value
        ),
    )
    logger.info("adoption match rag sources=%s", sources)

    def load() -> dict:
        return generate_json(
            system_prompt=MATCH_SYSTEM_PROMPT,
            user_text=user_text,
            temperature=MATCH_TEMPERATURE,
            few_shot_user=MATCH_FEW_SHOT_USER,
            few_shot_model=MATCH_FEW_SHOT_MODEL,
        )

    def parse(raw: dict) -> MatchResponse:
        parsed = MatchResponse.model_validate(raw)
        unknown = [item.post_id for item in parsed.matches if item.post_id not in allowed]
        if unknown:
            logger.warning("adoption match rejected unknown post_id count=%s", len(unknown))
            raise HTTPException(status_code=502, detail=SCHEMA_MISMATCH)
        return parsed

    return _run(load, parse)


@router.post("/review-application", response_model=ReviewResponse)
def review_application(body: ReviewRequest) -> ReviewResponse:
    logger.info("adoption review")
    animal = body.animal.model_dump()
    form = body.form.model_dump()
    user_text, sources = augment_user_text(
        review_user_text(animal, form),
        " ".join(
            str(value)
            for value in (
                animal.get("species"),
                animal.get("special_needs"),
                form.get("housing"),
                form.get("motivation"),
            )
            if value
        ),
    )
    logger.info("adoption review rag sources=%s", sources)

    def load() -> dict:
        return generate_json(
            system_prompt=REVIEW_APPLICATION_SYSTEM_PROMPT,
            user_text=user_text,
            temperature=REVIEW_TEMPERATURE,
            few_shot_user=REVIEW_FEW_SHOT_USER,
            few_shot_model=REVIEW_FEW_SHOT_MODEL,
        )

    return _run(load, ReviewResponse.model_validate)
