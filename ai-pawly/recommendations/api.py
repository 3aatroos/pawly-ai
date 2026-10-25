"""FastAPI routes for recommendation scoring."""

from __future__ import annotations

import hmac
import logging

from fastapi import APIRouter, Depends, Header, HTTPException
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from recommendations.qlora_scorer import QloraBackend
from recommendations.schemas import ScoreRequest, ScoreResponse
from recommendations.service import score_request
from recommendations.settings import Settings

logger = logging.getLogger("pawly.recommendations")
router = APIRouter(prefix="/recommendations", tags=["recommendations"])

_backend: QloraBackend | None = None
_settings: Settings | None = None


def get_settings() -> Settings:
    global _settings
    if _settings is None:
        _settings = Settings.from_env()
    return _settings


def get_backend() -> QloraBackend:
    global _backend
    if _backend is None:
        _backend = QloraBackend(get_settings())
    return _backend


def reset_for_tests() -> None:
    global _backend, _settings
    _backend = None
    _settings = None


def install_exception_handlers(app) -> None:
    """Hide rejected recommendation and adoption payloads. Other routes keep FastAPI's usual 422."""

    @app.exception_handler(RequestValidationError)
    async def invalid_request(request, exc):
        path = request.url.path
        if path.startswith("/recommendations"):
            return JSONResponse(status_code=422, content={"detail": "invalid recommendation request"})
        if path.startswith("/adoption"):
            fields = [".".join(str(part) for part in err.get("loc", ())) for err in exc.errors()]
            logging.getLogger("pawly.adoption").info("adoption request rejected fields=%s", fields)
            detail = "invalid adoption request"
            for err in exc.errors():
                if "personal data" in str(err.get("msg", "")):
                    detail = "personal data is not accepted"
                    break
            return JSONResponse(status_code=422, content={"detail": detail})
        return JSONResponse(status_code=422, content={"detail": jsonable_encoder(exc.errors())})


def _authorized(
    settings: Settings = Depends(get_settings),
    x_pawly_internal_token: str | None = Header(default=None),
) -> None:
    expected = settings.internal_token
    if not expected:
        return
    provided = x_pawly_internal_token or ""
    if len(provided) != len(expected) or not hmac.compare_digest(provided, expected):
        raise HTTPException(status_code=401, detail="unauthorized")


@router.get("/health")
def health(settings: Settings = Depends(get_settings), backend: QloraBackend = Depends(get_backend)):
    return {
        "status": "ok",
        "qlora_mode": settings.qlora_mode,
        "adapter_present": settings.adapter_dir.is_dir(),
        "model_enabled": backend.enabled(),
    }


@router.post("/score", response_model=ScoreResponse)
def score(
    body: ScoreRequest,
    _auth: None = Depends(_authorized),
    backend: QloraBackend = Depends(get_backend),
    settings: Settings = Depends(get_settings),
):
    try:
        return score_request(body, backend, settings.model_timeout_seconds)
    except Exception as exc:
        logger.warning("recommendation_unhandled error_type=%s", type(exc).__name__)
        raise HTTPException(status_code=500, detail="recommendation scoring failed") from exc
