"""
adapters.py
===========
Save LoRA adapters only. The base weights stay where Hugging Face loaded them
from and are never rewritten or merged into a full checkpoint.
"""

from __future__ import annotations

import re
from pathlib import Path


ADAPTER_CONFIG = "adapter_config.json"
ADAPTER_WEIGHTS = {"adapter_model.safetensors", "adapter_model.bin"}
_SHARD_RE = re.compile(r"model-\d+-of-\d+")


def _forbidden(name: str) -> bool:
    if name in ADAPTER_WEIGHTS or name == ADAPTER_CONFIG:
        return False
    if name.endswith((".safetensors", ".bin", ".pt", ".pth", ".ckpt")):
        return True
    return _SHARD_RE.match(name) is not None


def assert_adapters_only(directory: str | Path) -> list[str]:
    """Raise if the directory contains a full-model checkpoint."""
    root = Path(directory)
    files = [path for path in root.rglob("*") if path.is_file()]
    if not files:
        raise RuntimeError(f"No files saved in {root}.")
    names = [path.name for path in files]
    if ADAPTER_CONFIG not in names:
        raise RuntimeError("adapter_config.json is missing; refusing to treat this as a LoRA adapter.")
    if not any(name in ADAPTER_WEIGHTS for name in names):
        raise RuntimeError("Adapter weights are missing (expected adapter_model.safetensors).")
    for path in files:
        if _forbidden(path.name):
            raise RuntimeError(
                f"Full model weights must not be saved ({path.name}). "
                "Only LoRA adapter files are allowed."
            )
    return sorted(names)


def assert_only_lora_trainable(model) -> dict:
    """Every trainable parameter name must belong to a LoRA adapter."""
    bad = []
    trainable = 0
    total = 0
    for name, param in model.named_parameters():
        count = int(param.numel())
        total += count
        if param.requires_grad:
            trainable += count
            if "lora_" not in name:
                bad.append(name)
    if bad:
        preview = ", ".join(bad[:5])
        raise RuntimeError(f"Non-LoRA parameters are trainable: {preview}")
    if trainable <= 0 or total <= 0 or trainable >= total:
        raise RuntimeError(
            f"LoRA was not applied (trainable={trainable}, total={total}). "
            "Refusing to update the full weight set."
        )
    return {
        "trainable": trainable,
        "total": total,
        "trainable_ratio": trainable / total,
    }


def save_adapters_only(model, directory: str | Path, tokenizer=None) -> Path:
    """
    Persist adapter_config.json and adapter weights.
    Tokenizer files may be stored beside them so inference can reload the
    same vocabulary. Base checkpoints are rejected by assert_adapters_only.
    """
    if not hasattr(model, "peft_config"):
        raise RuntimeError("Model has no PEFT adapter. Refusing to save full weights.")
    root = Path(directory)
    root.mkdir(parents=True, exist_ok=True)
    model.save_pretrained(str(root))
    if tokenizer is not None:
        tokenizer.save_pretrained(str(root))
    assert_adapters_only(root)
    return root
