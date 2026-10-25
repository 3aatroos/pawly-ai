"""Integration tests for the recommendation API. No model download."""

import logging

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from recommendations.api import (
    get_backend,
    get_settings,
    install_exception_handlers,
    reset_for_tests,
    router,
)
from recommendations.content_based import rank
from recommendations.qlora_scorer import QloraBackend
from recommendations.schemas import RecommendationOut, ScoreRequest
from recommendations.settings import Settings


def _body():
    return {
        "profile": {
            "pet_species": ["dog"],
            "pet_ages": ["senior"],
            "budget_tier": "budget",
        },
        "history": [],
        "candidates": [
            {
                "product_id": "5",
                "name": "Dog Kibble",
                "category": "Food",
                "species": "dog",
                "price_eur": 19.99,
                "description": "Food for dogs",
                "avg_rating": 5,
                "review_count": 4,
            },
            {
                "product_id": "7",
                "name": "Cat Litter",
                "category": "Hygiene",
                "species": "cat",
                "price_eur": 12,
                "description": "Litter for cats",
                "avg_rating": 0,
                "review_count": 0,
            },
        ],
        "limit": 8,
    }


class _TimeoutBackend(QloraBackend):
    def __init__(self):
        super().__init__(Settings(qlora_mode="on", internal_token=""))

    def enabled(self) -> bool:
        return True

    def score(self, request, timeout_seconds):
        raise TimeoutError("deadline")


class _ModelBackend(QloraBackend):
    def __init__(self):
        super().__init__(Settings(qlora_mode="on", internal_token=""))

    def enabled(self) -> bool:
        return True

    def score(self, request, timeout_seconds):
        return [
            RecommendationOut(
                product_id="5",
                score=0.91,
                relevance_class="highly_relevant",
                justification="Species match.",
            ),
            RecommendationOut(
                product_id="not-a-candidate",
                score=0.99,
                relevance_class="highly_relevant",
                justification="Dropped.",
            ),
        ]


def _client(backend, settings=None):
    reset_for_tests()
    app = FastAPI()
    install_exception_handlers(app)
    app.include_router(router)
    app.dependency_overrides[get_backend] = lambda: backend
    app.dependency_overrides[get_settings] = lambda: settings or Settings(qlora_mode="off", internal_token="")
    return TestClient(app)


def test_content_ranker_prefers_the_matching_species():
    ranked = rank(ScoreRequest.model_validate(_body()))
    assert ranked[0].product_id == "5"
    assert ranked[0].score > ranked[1].score
    assert "@" not in ranked[0].justification


def test_score_endpoint_uses_content_fallback_and_does_not_log_the_profile(caplog):
    caplog.set_level(logging.INFO, logger="pawly.recommendations")
    client = _client(QloraBackend(Settings(qlora_mode="off")))
    response = client.post("/recommendations/score", json=_body())
    assert response.status_code == 200
    payload = response.json()
    assert payload["source"] == "content_based"
    assert payload["recommendations"][0]["product_id"] == "5"
    assert "dog" not in caplog.text
    assert "Dog Kibble" not in caplog.text


def test_model_timeout_falls_back_to_content_based():
    response = _client(_TimeoutBackend()).post("/recommendations/score", json=_body())
    assert response.status_code == 200
    assert response.json()["source"] == "content_based"


def test_model_scores_drop_unknown_product_ids():
    response = _client(_ModelBackend()).post("/recommendations/score", json=_body())
    assert response.status_code == 200
    body = response.json()
    assert body["source"] == "qlora"
    assert [item["product_id"] for item in body["recommendations"]] == ["5"]


def test_personal_data_is_rejected_without_being_echoed():
    leaked = "owner@store.test"
    body = _body()
    body["profile"]["pet_species"] = [leaked]
    response = _client(QloraBackend(Settings(qlora_mode="off"))).post("/recommendations/score", json=body)
    assert response.status_code == 422
    assert leaked not in response.text
    extra = _body()
    extra["profile"]["email"] = leaked
    response = _client(QloraBackend(Settings(qlora_mode="off"))).post("/recommendations/score", json=extra)
    assert response.status_code == 422
    assert leaked not in response.text


def test_internal_token_is_required_when_configured():
    settings = Settings(qlora_mode="off", internal_token="local-test-token")
    client = _client(QloraBackend(settings), settings)
    assert client.post("/recommendations/score", json=_body()).status_code == 401
    ok = client.post(
        "/recommendations/score",
        json=_body(),
        headers={"X-Pawly-Internal-Token": "local-test-token"},
    )
    assert ok.status_code == 200


def test_health_reports_mode_without_a_filesystem_path():
    response = _client(QloraBackend(Settings(qlora_mode="off"))).get("/recommendations/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["qlora_mode"] == "off"
    assert "adapter_dir" not in body
    assert "Users" not in response.text
