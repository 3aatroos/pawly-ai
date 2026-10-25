"""Adoption Gemini routes. No network and no database."""

import json
import logging
from pathlib import Path

import pytest
import requests
from fastapi import FastAPI
from fastapi.testclient import TestClient
from pydantic import BaseModel

import gemini_client
from adoption_api import router
from adoption_prompts import (
    DRAFT_FEW_SHOT_OUTPUT,
    MATCH_FEW_SHOT_OUTPUT,
    REVIEW_FEW_SHOT_OUTPUT,
)
from adoption_schemas import DraftResponse, MatchResponse, ReviewResponse
from gemini_client import GeminiError, generate_json
from recommendations.api import install_exception_handlers

SECRET = "super-secret-gemini-key"
NOTE_MARKER = "note-marker-louna-tunis"
BODY_MARKER = "provider-body-marker"


def _client() -> TestClient:
    app = FastAPI()
    install_exception_handlers(app)
    app.include_router(router)

    class Widget(BaseModel):
        name: str

    @app.post("/widgets")
    def widgets(item: Widget) -> Widget:
        return item

    return TestClient(app)


def _patch_key(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(gemini_client, "GEMINI_API_KEY", SECRET)
    monkeypatch.setattr(gemini_client, "GEMINI_MODEL", "gemini-2.5-flash")
    monkeypatch.setattr(gemini_client, "GEMINI_TIMEOUT", 60)


def _response(status: int, body: dict | None = None) -> requests.Response:
    resp = requests.Response()
    resp.status_code = status
    resp._content = json.dumps(body or {}).encode()
    resp.headers["Content-Type"] = "application/json"
    return resp


def _candidate(text: str, thought: str | None = None) -> dict:
    parts: list[dict] = []
    if thought is not None:
        parts.append({"thought": True, "text": thought})
    parts.append({"text": text})
    return {"candidates": [{"content": {"parts": parts}, "finishReason": "STOP"}]}


def test_few_shots_match_the_response_schemas():
    DraftResponse.model_validate(DRAFT_FEW_SHOT_OUTPUT)
    MatchResponse.model_validate(MATCH_FEW_SHOT_OUTPUT)
    ReviewResponse.model_validate(REVIEW_FEW_SHOT_OUTPUT)


def test_missing_key_does_not_call_the_provider(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(gemini_client, "GEMINI_API_KEY", "")

    def fail(*_args, **_kwargs):
        raise AssertionError("provider was called")

    monkeypatch.setattr(gemini_client.requests, "post", fail)
    with pytest.raises(GeminiError) as caught:
        generate_json(
            system_prompt="system",
            user_text="data",
            temperature=0.4,
            few_shot_user="example user",
            few_shot_model="{}",
        )
    assert caught.value.status_code == 503
    assert caught.value.message == "AI service unavailable: GEMINI_API_KEY is missing."


def test_timeout_rate_limit_and_invalid_json(monkeypatch: pytest.MonkeyPatch, caplog):
    _patch_key(monkeypatch)
    caplog.set_level(logging.DEBUG)
    calls = {"n": 0}

    def fake_post(*_args, **_kwargs):
        calls["n"] += 1
        if calls["n"] == 1:
            raise requests.Timeout(BODY_MARKER)
        if calls["n"] == 2:
            resp = requests.Response()
            resp.status_code = 429
            resp._content = BODY_MARKER.encode()
            return resp
        return _response(200, _candidate('{"truncated": "' + BODY_MARKER))

    monkeypatch.setattr(gemini_client.requests, "post", fake_post)
    kwargs = {
        "system_prompt": "s",
        "user_text": "u",
        "temperature": 0.2,
        "few_shot_user": "a",
        "few_shot_model": "{}",
    }

    with pytest.raises(GeminiError) as timed_out:
        generate_json(**kwargs)
    assert timed_out.value.status_code == 504
    assert timed_out.value.message == "AI service timed out. Please try again."

    with pytest.raises(GeminiError) as limited:
        generate_json(**kwargs)
    assert limited.value.status_code == 429
    assert limited.value.message == (
        "AI service error: rate limit exceeded. Please wait a moment and try again."
    )

    with pytest.raises(GeminiError) as invalid:
        generate_json(**kwargs)
    assert invalid.value.status_code == 502
    assert invalid.value.message == "AI service returned invalid JSON."
    assert BODY_MARKER not in caplog.text
    assert BODY_MARKER not in timed_out.value.message
    assert BODY_MARKER not in limited.value.message
    assert BODY_MARKER not in invalid.value.message


def test_success_hides_key_notes_and_thoughts(monkeypatch: pytest.MonkeyPatch, caplog):
    _patch_key(monkeypatch)
    caplog.set_level(logging.DEBUG)
    seen: dict = {}
    draft_text = json.dumps(DRAFT_FEW_SHOT_OUTPUT, ensure_ascii=False)

    def fake_post(url, headers=None, json=None, timeout=None):  # noqa: A002
        seen["url"] = url
        seen["headers"] = headers
        seen["json"] = json
        seen["timeout"] = timeout
        return _response(200, _candidate(draft_text, thought="thought-" + NOTE_MARKER))

    monkeypatch.setattr(gemini_client.requests, "post", fake_post)
    parsed = generate_json(
        system_prompt="system-rules",
        user_text=f"Donnée utilisateur.\nNotes brutes:\n{NOTE_MARKER}",
        temperature=0.4,
        few_shot_user="example",
        few_shot_model="{}",
    )

    assert parsed["name"] == "Louna"
    assert seen["url"] == (
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    )
    assert SECRET not in seen["url"]
    assert seen["headers"]["x-goog-api-key"] == SECRET
    assert seen["timeout"] == 60
    config = seen["json"]["generationConfig"]
    assert config["temperature"] == 0.4
    assert config["topP"] == 0.8
    assert config["maxOutputTokens"] == 800
    assert config["responseMimeType"] == "application/json"
    assert config["thinkingConfig"] == {"thinkingBudget": 0}
    assert NOTE_MARKER in json.dumps(seen["json"])
    assert SECRET not in caplog.text
    assert NOTE_MARKER not in caplog.text
    assert ("thought-" + NOTE_MARKER) not in caplog.text


def test_draft_route_returns_validated_json(monkeypatch: pytest.MonkeyPatch):
    client = _client()

    def fake_generate(**kwargs):
        assert kwargs["temperature"] == 0.4
        assert "brouillon" in kwargs["system_prompt"]
        return DRAFT_FEW_SHOT_OUTPUT

    monkeypatch.setattr("adoption_api.generate_json", fake_generate)
    response = client.post(
        "/adoption/draft",
        json={
            "notes": "Louna, chatte rousse recueillie à Tunis.",
            "photo_description": "Chat roux dans un box du refuge.",
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["name"] == "Louna"
    assert body["breed"] is None
    assert "vaccin" not in body["description"].lower()
    assert "statut vaccinal non indiqué" in body["uncertainties"]


def test_match_and_review_routes(monkeypatch: pytest.MonkeyPatch):
    client = _client()
    temperatures: list[float] = []

    def fake_generate(**kwargs):
        temperatures.append(kwargs["temperature"])
        if "correspondance" in kwargs["system_prompt"]:
            return MATCH_FEW_SHOT_OUTPUT
        return REVIEW_FEW_SHOT_OUTPUT

    monkeypatch.setattr("adoption_api.generate_json", fake_generate)
    matched = client.post(
        "/adoption/match",
        json={
            "lifestyle": {
                "housing": "appartement",
                "has_garden": False,
                "hours_away_per_day": 8,
                "other_pets": False,
                "children": False,
                "experience": "débutant",
                "sector": "Tunis",
            },
            "posts": [
                {"id": 12, "species": "chat", "age": "environ 2 ans", "location": "Tunis"},
                {"id": 27, "species": "chien", "age": "4 mois", "location": "Tunis"},
            ],
        },
    )
    assert matched.status_code == 200
    assert [item["post_id"] for item in matched.json()["matches"]] == [12, 27]

    reviewed = client.post(
        "/adoption/review-application",
        json={
            "animal": {"name": "Louna", "species": "chat", "location": "Tunis"},
            "form": {
                "housing": "appartement",
                "experience": "débutant",
                "motivation": "Je rentre le midi.",
            },
        },
    )
    assert reviewed.status_code == 200
    assert reviewed.json()["suggested_attention"] == "medium"
    assert temperatures == [0.2, 0.2]


def test_invalid_model_json_is_a_clear_error(monkeypatch: pytest.MonkeyPatch):
    client = _client()

    def fake_generate(**_kwargs):
        return {
            "summary": "La candidature est refusée pour ce foyer.",
            "points_to_verify": [],
            "suggested_attention": "high",
        }

    monkeypatch.setattr("adoption_api.generate_json", fake_generate)
    response = client.post(
        "/adoption/review-application",
        json={"animal": {"species": "chat"}, "form": {"housing": "appartement"}},
    )
    assert response.status_code == 502
    assert response.json()["detail"] == (
        "AI service returned a JSON payload that does not match the expected schema."
    )
    assert "refus" not in response.text


def test_provider_errors_do_not_leak(monkeypatch: pytest.MonkeyPatch):
    client = _client()

    def unknown(**_kwargs):
        return {
            "matches": [
                {"post_id": 99, "score": 10, "reason": "Hors liste.", "warning": None}
            ]
        }

    monkeypatch.setattr("adoption_api.generate_json", unknown)
    response = client.post(
        "/adoption/match",
        json={"lifestyle": {"housing": "appartement"}, "posts": [{"id": 12, "species": "chat"}]},
    )
    assert response.status_code == 502
    assert "99" not in response.text

    def exploded(**_kwargs):
        raise RuntimeError(BODY_MARKER)

    monkeypatch.setattr("adoption_api.generate_json", exploded)
    failed = client.post("/adoption/draft", json={"notes": "Chat trouvé à Tunis."})
    assert failed.status_code == 500
    assert failed.json()["detail"] == "AI service returned an unexpected error."
    assert BODY_MARKER not in failed.text

    def limited(**_kwargs):
        raise GeminiError(
            "AI service error: rate limit exceeded. Please wait a moment and try again.",
            429,
        )

    monkeypatch.setattr("adoption_api.generate_json", limited)
    blocked = client.post("/adoption/draft", json={"notes": "Chat trouvé à Tunis."})
    assert blocked.status_code == 429
    assert "rate limit" in blocked.json()["detail"]


def test_personal_data_is_rejected_without_echo():
    client = _client()
    response = client.post(
        "/adoption/draft",
        json={"notes": "Écrire à ada@example.com pour Louna."},
    )
    assert response.status_code == 422
    assert response.json()["detail"] == "personal data is not accepted"
    assert "ada@example.com" not in response.text

    other = client.post("/widgets", json={})
    assert other.status_code == 422
    assert "name" in other.text


def test_triage_route_is_unchanged():
    source = (Path(__file__).resolve().parents[1] / "main.py").read_text(encoding="utf-8")
    triage = source.split("def perform_triage", 1)[1].split("def seed_kb", 1)[0]
    assert "call_mistral" in triage
    assert "generate_json" not in triage
    assert "app.include_router(adoption_router)" in source
