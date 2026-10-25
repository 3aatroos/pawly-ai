"""Environment for the recommendation endpoint. No secret has a default value."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

_DEFAULT_ADAPTER = (
    Path(__file__).resolve().parents[1] / "qlora" / "outputs" / "qwen25-0.5b-qlora" / "adapter"
)


@dataclass(frozen=True)
class Settings:
    qlora_mode: str = "auto"
    adapter_dir: Path = _DEFAULT_ADAPTER
    model_timeout_seconds: float = 6.0
    internal_token: str = ""
    force_cpu: bool = False

    @classmethod
    def from_env(cls) -> "Settings":
        mode = os.getenv("QLORA_ENABLED", "auto").strip().lower()
        if mode == "true":
            mode = "on"
        elif mode == "false":
            mode = "off"
        elif mode not in {"auto", "on", "off"}:
            mode = "auto"
        try:
            timeout = float(os.getenv("QLORA_TIMEOUT_SECONDS", "6"))
        except ValueError:
            timeout = 6.0
        timeout = min(30.0, max(0.2, timeout))
        adapter = Path(os.getenv("QLORA_ADAPTER_DIR", str(_DEFAULT_ADAPTER)))
        return cls(
            qlora_mode=mode,
            adapter_dir=adapter,
            model_timeout_seconds=timeout,
            internal_token=os.getenv("AI_SERVICE_TOKEN", "").strip(),
            force_cpu=os.getenv("QLORA_FORCE_CPU", "").strip().lower() in {"1", "true", "yes"},
        )
