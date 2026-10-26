"""Gemini client for the adoption feed.

Mistral and POST /triage do not use this module. The API key is sent as a
header and is never written in the URL or in the logs. Request and response
bodies can echo shelter notes, so this module logs lengths and status codes only.
"""

from __future__ import annotations

import json
import logging
import re
from typing import Any

import requests

from config import GEMINI_API_KEY, GEMINI_MODEL, GEMINI_TIMEOUT

logger = logging.getLogger(__name__)

_GENERATE_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
_MODEL_NAME = re.compile(r"^[A-Za-z0-9][A-Za-z0-9.\-]{0,79}$")
_TOP_P = 0.8
_MAX_OUTPUT_TOKENS = 800
_FENCE = re.compile(r"^```(?:json)?\s*(.*?)\s*```$", re.DOTALL | re.IGNORECASE)


class GeminiError(Exception):
    """Safe, caller-facing failure. The message never includes a payload or a key."""

    def __init__(self, message: str, status_code: int) -> None:
        super().__init__(message)
        self.message = message
        self.status_code = status_code


def generate_json(
    *,
    system_prompt: str,
    user_text: str,
    temperature: float,
    few_shot_user: str,
    few_shot_model: str,
) -> dict[str, Any]:
    """Call Gemini and return the parsed JSON object.

    Raises GeminiError with a fixed message. Other exceptions are caught here.
    """
    if not GEMINI_API_KEY:
        logger.error("GEMINI_API_KEY is not set")
        raise GeminiError("AI service unavailable: GEMINI_API_KEY is missing.", 503)

    model = _model_name()
    url = _GENERATE_URL.format(model=model)
    payload = {
        "systemInstruction": {"parts": [{"text": system_prompt}]},
        "contents": [
            {"role": "user", "parts": [{"text": few_shot_user}]},
            {"role": "model", "parts": [{"text": few_shot_model}]},
            {"role": "user", "parts": [{"text": user_text}]},
        ],
        "generationConfig": {
            "temperature": temperature,
            "topP": _TOP_P,
            "maxOutputTokens": _MAX_OUTPUT_TOKENS,
            "responseMimeType": "application/json",
            # Gemini 2.5 can switch thinking off. Gemini 3 rejects that budget and uses a level.
            "thinkingConfig": _thinking_config(model),
        },
    }
    headers = {
        "Content-Type": "application/json",
        "x-goog-api-key": GEMINI_API_KEY,
    }

    try:
        logger.info(
            "Calling Gemini model=%s temperature=%s user_chars=%s",
            model,
            temperature,
            len(user_text),
        )
        resp = requests.post(url, headers=headers, json=payload, timeout=GEMINI_TIMEOUT)
        logger.info("Gemini API status=%s model=%s", resp.status_code, model)
        return _read_response(resp)
    except GeminiError:
        raise
    except requests.Timeout:
        logger.error("Gemini API request timed out model=%s", model)
        raise GeminiError("AI service timed out. Please try again.", 504) from None
    except requests.ConnectionError:
        logger.error("Gemini API connection failed model=%s", model)
        raise GeminiError("AI service unavailable: cannot reach the Gemini API.", 503) from None
    except requests.RequestException:
        logger.error("Gemini API request failed model=%s", model)
        raise GeminiError("AI service unavailable: cannot reach the Gemini API.", 503) from None
    except Exception as exc:
        logger.error("Gemini API unexpected error type=%s", type(exc).__name__)
        raise GeminiError("AI service returned an unexpected error.", 502) from None


def _thinking_config(model: str) -> dict[str, Any]:
    if model.startswith("gemini-2."):
        return {"thinkingBudget": 0}
    return {"thinkingLevel": "minimal"}


def _model_name() -> str:
    model = GEMINI_MODEL or "gemini-3.5-flash-lite"
    if not _MODEL_NAME.fullmatch(model):
        logger.error("GEMINI_MODEL is invalid")
        raise GeminiError("AI service unavailable: GEMINI_MODEL is invalid.", 503)
    return model


def _read_response(resp: requests.Response) -> dict[str, Any]:
    if resp.status_code == 429:
        logger.warning("Gemini API rate limit")
        raise GeminiError(
            "AI service error: rate limit exceeded. Please wait a moment and try again.",
            429,
        )
    if resp.status_code in (401, 403):
        logger.error("Gemini API rejected the key status=%s", resp.status_code)
        raise GeminiError(
            "AI service error: invalid API key. Check GEMINI_API_KEY in your .env file.",
            503,
        )
    if resp.status_code != 200:
        logger.error("Gemini API error status=%s", resp.status_code)
        raise GeminiError(f"AI service error (HTTP {resp.status_code}).", 502)

    try:
        body = resp.json()
    except ValueError:
        raise GeminiError("AI service returned invalid JSON.", 502) from None
    if not isinstance(body, dict):
        raise GeminiError("AI service returned invalid JSON.", 502)

    text = _candidate_text(body)
    return _parse_json_object(text)


def _candidate_text(body: dict[str, Any]) -> str:
    candidates = body.get("candidates")
    if not isinstance(candidates, list) or not candidates:
        logger.info("Gemini API returned no candidate")
        raise GeminiError("AI service returned an empty response.", 502)

    candidate = candidates[0]
    if not isinstance(candidate, dict):
        raise GeminiError("AI service returned an empty response.", 502)

    finish = candidate.get("finishReason")
    if isinstance(finish, str) and finish:
        logger.info("Gemini finish_reason=%s", finish[:40])

    content = candidate.get("content")
    parts = content.get("parts") if isinstance(content, dict) else None
    if not isinstance(parts, list):
        raise GeminiError("AI service returned an empty response.", 502)

    chunks: list[str] = []
    for part in parts:
        if not isinstance(part, dict) or part.get("thought") is True:
            continue
        text = part.get("text")
        if isinstance(text, str) and text.strip():
            chunks.append(text)
    if not chunks:
        raise GeminiError("AI service returned an empty response.", 502)
    return "\n".join(chunks)


def _parse_json_object(text: str) -> dict[str, Any]:
    cleaned = text.strip()
    fenced = _FENCE.match(cleaned)
    if fenced:
        cleaned = fenced.group(1).strip()
    try:
        data = json.loads(cleaned)
    except json.JSONDecodeError:
        raise GeminiError("AI service returned invalid JSON.", 502) from None
    if not isinstance(data, dict):
        raise GeminiError("AI service returned invalid JSON.", 502)
    return data
