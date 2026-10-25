"""
config.py
=========
Reproducible hyperparameters for Pawly recommendation QLoRA.

The model choice is verified against the public Hugging Face files
(config.json, tokenizer_config.json, LICENSE) before any training run.
"""

from __future__ import annotations

import random
from dataclasses import asdict, dataclass


# Verified on the model card and raw files of Qwen/Qwen2.5-0.5B-Instruct.
# transformers < 4.37.0 raises KeyError: 'qwen2'. This project additionally
# requires >= 4.46.0 so TrainingArguments.eval_strategy is available.
MODEL_ID = "Qwen/Qwen2.5-0.5B-Instruct"
MODEL_LICENSE = "Apache-2.0"
MODEL_ARCHITECTURE = "Qwen2ForCausalLM"
MODEL_TYPE = "qwen2"
TOKENIZER_CLASS = "Qwen2Tokenizer"
PAD_TOKEN = "<|endoftext|>"
EOS_TOKEN = "<|im_end|>"
VOCAB_SIZE = 151936
PARAMETER_COUNT_BILLIONS = 0.49
MIN_TRANSFORMERS = (4, 46, 0)
QWEN2_MIN_TRANSFORMERS = (4, 37, 0)

# NF4 + double quantization is about 0.5 byte per parameter (4 bits + scales).
ESTIMATED_4BIT_WEIGHT_GB = PARAMETER_COUNT_BILLIONS * 0.5

SEED = 42

SYSTEM_PROMPT = (
    "You are Pawly, a pet-store recommendation assistant. "
    "Given a user profile, interaction history, and one candidate product, "
    "reply with a relevance score from 0.0 to 1.0 and a short justification. "
    "Use only the supplied attributes."
)


def parse_version(value: str) -> tuple[int, int, int]:
    """Parse '4.46.1' or '4.46.0.dev0' into a three-integer tuple."""
    numbers: list[int] = []
    for part in value.split(".")[:3]:
        digits = ""
        for char in part:
            if char.isdigit():
                digits += char
            else:
                break
        numbers.append(int(digits or "0"))
    while len(numbers) < 3:
        numbers.append(0)
    return (numbers[0], numbers[1], numbers[2])


def set_seed(seed: int) -> None:
    """Seed the libraries that are installed. Missing optional libs are skipped."""
    random.seed(seed)
    try:
        import numpy as np

        np.random.seed(seed)
    except ImportError:
        pass
    try:
        import torch

        torch.manual_seed(seed)
        if torch.cuda.is_available():
            torch.cuda.manual_seed_all(seed)
    except ImportError:
        pass


@dataclass
class QLoRAConfig:
    """Logged as JSON at the start of every training run."""

    model_id: str = MODEL_ID
    seed: int = SEED
    lora_r: int = 8
    lora_alpha: int = 16
    lora_dropout: float = 0.05
    lora_target_modules: tuple[str, ...] = (
        "q_proj",
        "k_proj",
        "v_proj",
        "o_proj",
        "gate_proj",
        "up_proj",
        "down_proj",
    )
    learning_rate: float = 2e-4
    weight_decay: float = 0.01
    num_train_epochs: float = 2.0
    per_device_train_batch_size: int = 1
    gradient_accumulation_steps: int = 8
    max_seq_length: int = 512
    warmup_ratio: float = 0.05
    max_grad_norm: float = 0.3
    logging_steps: int = 10
    gradient_checkpointing: bool = True
    max_steps: int | None = None
    # 4-bit weights are ~0.25 GB. Public QLoRA peaks for this model at
    # sequence 512 are about 1.7-2.4 GiB, which fits a 4 GB laptop GPU.
    min_free_vram_gb: float = 3.0
    min_free_ram_gb: float = 6.0
    min_host_ram_for_gpu_gb: float = 3.0
    quant_bits: int = 4
    quant_type: str = "nf4"
    double_quant: bool = True
    max_new_tokens: int = 128
    lr_scheduler_type: str = "cosine"

    def to_dict(self) -> dict:
        payload = asdict(self)
        payload["lora_target_modules"] = list(self.lora_target_modules)
        return payload


def hyperparameter_record(
    cfg: QLoRAConfig,
    decision,
    split_counts: dict,
    versions: dict | None = None,
) -> dict:
    """Flat record written to outputs/hyperparameters.json before training."""
    return {
        "seed": cfg.seed,
        "model_id": cfg.model_id,
        "license": MODEL_LICENSE,
        "architecture": MODEL_ARCHITECTURE,
        "model_type": MODEL_TYPE,
        "tokenizer_class": TOKENIZER_CLASS,
        "pad_token": PAD_TOKEN,
        "eos_token": EOS_TOKEN,
        "vocab_size": VOCAB_SIZE,
        "estimated_4bit_weight_gb": round(ESTIMATED_4BIT_WEIGHT_GB, 3),
        "learning_rate": cfg.learning_rate,
        "weight_decay": cfg.weight_decay,
        "num_train_epochs": cfg.num_train_epochs,
        "max_steps": cfg.max_steps,
        "per_device_train_batch_size": cfg.per_device_train_batch_size,
        "gradient_accumulation_steps": cfg.gradient_accumulation_steps,
        "max_seq_length": cfg.max_seq_length,
        "warmup_ratio": cfg.warmup_ratio,
        "max_grad_norm": cfg.max_grad_norm,
        "lr_scheduler_type": cfg.lr_scheduler_type,
        "logging_steps": cfg.logging_steps,
        "gradient_checkpointing": cfg.gradient_checkpointing,
        "lora_r": cfg.lora_r,
        "lora_alpha": cfg.lora_alpha,
        "lora_dropout": cfg.lora_dropout,
        "lora_target_modules": list(cfg.lora_target_modules),
        "quantization": {
            "load_in_4bit": decision.load_in_4bit,
            "bits": cfg.quant_bits if decision.load_in_4bit else 0,
            "quant_type": cfg.quant_type if decision.load_in_4bit else None,
            "double_quant": cfg.double_quant if decision.load_in_4bit else False,
        },
        "optim": decision.optim,
        "fp16": decision.fp16,
        "bf16": decision.bf16,
        "device": decision.to_dict(),
        "split_counts": split_counts,
        "library_versions": versions or {},
    }
