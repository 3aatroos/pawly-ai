"""Score candidates with QLoRA, then the content-based fallback."""

from __future__ import annotations

import logging
import time

from recommendations import content_based
from recommendations.qlora_scorer import QloraBackend
from recommendations.schemas import ScoreRequest, ScoreResponse

logger = logging.getLogger("pawly.recommendations")


def score_request(
    request: ScoreRequest,
    backend: QloraBackend | None = None,
    timeout_seconds: float = 6.0,
) -> ScoreResponse:
    started = time.monotonic()
    source = "content_based"
    rows = None
    if backend is not None and backend.enabled():
        try:
            rows = backend.score(request, timeout_seconds)
            if rows:
                allowed = {candidate.product_id for candidate in request.candidates}
                rows = [row for row in rows if row.product_id in allowed]
            if rows:
                source = "qlora"
        except TimeoutError:
            logger.warning("recommendation_model_failed error_type=TimeoutError")
            rows = None
        except Exception as exc:
            if backend.warn_once():
                logger.warning(
                    "recommendation_model_failed error_type=%s",
                    type(exc).__name__,
                )
            backend.mark_failed()
            rows = None
    if not rows:
        rows = content_based.rank(request)
        source = "content_based"
    elapsed_ms = int((time.monotonic() - started) * 1000)
    logger.info(
        "recommendation_scored candidates=%s history=%s source=%s elapsed_ms=%s",
        len(request.candidates),
        len(request.history),
        source,
        elapsed_ms,
    )
    return ScoreResponse(source=source, recommendations=rows[: request.limit])
