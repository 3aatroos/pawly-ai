"""Optional QLoRA scorer. Importing this module does not load PyTorch."""

from __future__ import annotations

import sys
import threading
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FuturesTimeout
from pathlib import Path

from recommendations.content_based import score_to_class
from recommendations.schemas import RecommendationOut, ScoreRequest
from recommendations.settings import Settings

_QLORA_DIR = Path(__file__).resolve().parents[1] / "qlora"
_POSITIVE = {"view", "cart", "purchase", "review_pos"}


def _price_tier(price: float) -> str:
    if price < 15:
        return "budget"
    if price < 40:
        return "mid"
    return "premium"


def _example(request: ScoreRequest, candidate) -> dict:
    categories = []
    for item in request.history:
        if item.category and item.category not in categories and item.signal in _POSITIVE:
            categories.append(item.category)
    history = [
        {
            "product_id": item.product_id,
            "product_name": item.product_name,
            "signal": item.signal,
            "weight": item.weight,
        }
        for item in request.history
    ]
    budget = request.profile.budget_tier if request.profile.budget_tier != "unknown" else "mid"
    return {
        "id": candidate.product_id,
        "user_profile": {
            "pet_species": request.profile.pet_species or ["unknown"],
            "pet_ages": request.profile.pet_ages or ["adult"],
            "pet_sizes": ["all"],
            "budget_tier": budget,
            "preferred_categories": categories or ["General"],
            "health_interests": [],
        },
        "interaction_history": history,
        "candidate_product": {
            "product_id": candidate.product_id,
            "name": candidate.name,
            "category": candidate.category or "General",
            "species": candidate.species or "all",
            "price_eur": candidate.price_eur,
            "price_tier": _price_tier(candidate.price_eur),
            "description": candidate.description or candidate.name,
            "tags": [],
            "age_target": "all",
            "size_target": "all",
            "health_benefits": [],
            "avg_rating": candidate.avg_rating,
            "review_count": candidate.review_count,
        },
    }


class QloraBackend:
    """Loads the adapter on first use and scores candidates inside a deadline."""

    def __init__(self, settings: Settings):
        self.settings = settings
        self._lock = threading.Lock()
        self._runtime = None
        self._failed = False
        self._warned = False

    def enabled(self) -> bool:
        if self._failed or self.settings.qlora_mode == "off":
            return False
        if self.settings.qlora_mode == "on":
            return True
        return self.settings.adapter_dir.is_dir()

    def score(self, request: ScoreRequest, timeout_seconds: float) -> list[RecommendationOut] | None:
        if timeout_seconds <= 0:
            raise TimeoutError("qlora deadline already elapsed")
        pool = ThreadPoolExecutor(max_workers=1)
        future = pool.submit(self._score_all, request)
        try:
            return future.result(timeout=timeout_seconds)
        except FuturesTimeout as exc:
            raise TimeoutError("qlora scoring timed out") from exc
        finally:
            pool.shutdown(wait=False, cancel_futures=True)

    def _score_all(self, request: ScoreRequest) -> list[RecommendationOut]:
        model, tokenizer, generate, parse = self._load()
        allowed = {candidate.product_id for candidate in request.candidates}
        ranked: list[RecommendationOut] = []
        for candidate in request.candidates:
            text = generate(model, tokenizer, _example(request, candidate), 96)
            parsed = parse(text)
            if parsed.score is None or candidate.product_id not in allowed:
                continue
            justification = " ".join((parsed.justification or "Model score.").split())
            if not justification:
                justification = "Model score."
            ranked.append(
                RecommendationOut(
                    product_id=candidate.product_id,
                    score=round(float(parsed.score), 4),
                    relevance_class=score_to_class(float(parsed.score)),
                    justification=justification[:400],
                )
            )
        if not ranked:
            raise RuntimeError("qlora returned no parseable scores")
        ranked.sort(key=lambda item: (-item.score, item.product_id))
        return ranked

    def _load(self):
        if self._runtime is not None:
            return self._runtime
        with self._lock:
            if self._runtime is not None:
                return self._runtime
            if not self.settings.adapter_dir.is_dir():
                raise FileNotFoundError("qlora adapter directory is missing")
            if str(_QLORA_DIR) not in sys.path:
                sys.path.insert(0, str(_QLORA_DIR))
            from config import QLoRAConfig
            from device import decide_backend, probe_memory
            from metrics import parse_model_response
            from modeling import generate_completion, load_for_inference

            cfg = QLoRAConfig()
            decision = decide_backend(
                probe_memory(),
                cfg,
                force_cpu=self.settings.force_cpu,
            )
            if decision.blocked:
                raise RuntimeError("qlora runtime blocked by the memory check")
            model, tokenizer = load_for_inference(cfg, decision, str(self.settings.adapter_dir))
            self._runtime = (model, tokenizer, generate_completion, parse_model_response)
            return self._runtime

    def mark_failed(self) -> None:
        self._failed = True

    def warn_once(self) -> bool:
        if self._warned:
            return False
        self._warned = True
        return True
