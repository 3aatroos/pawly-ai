"""Config, hyperparameter log, tokenizer contract, and stack references."""

import json
import re
from pathlib import Path

from config import (
    EOS_TOKEN,
    ESTIMATED_4BIT_WEIGHT_GB,
    MIN_TRANSFORMERS,
    MODEL_ID,
    PAD_TOKEN,
    QLoRAConfig,
    SEED,
    VOCAB_SIZE,
    hyperparameter_record,
    parse_version,
    set_seed,
)
from device import MemorySnapshot, decide_backend
from modeling import assert_tokenizer_compatible


ROOT = Path(__file__).resolve().parents[1]
REQUIRED_KEYS = [
    "seed",
    "model_id",
    "license",
    "learning_rate",
    "num_train_epochs",
    "per_device_train_batch_size",
    "gradient_accumulation_steps",
    "max_seq_length",
    "weight_decay",
    "warmup_ratio",
    "max_grad_norm",
    "lora_r",
    "lora_alpha",
    "lora_dropout",
    "lora_target_modules",
    "quantization",
    "optim",
    "device",
    "split_counts",
]


class Qwen2TokenizerFake:
    def __init__(self, pad=PAD_TOKEN, eos=EOS_TOKEN, vocab=VOCAB_SIZE, fast=True):
        self.pad_token = pad
        self.eos_token = eos
        self.pad_token_id = 151643
        self.is_fast = fast
        self.padding_side = "left"
        self._vocab = vocab

    def __len__(self):
        return self._vocab


def _snapshot(**overrides) -> MemorySnapshot:
    payload = dict(
        cuda_available=True,
        gpu_name="Test GPU",
        total_vram_gb=8.0,
        free_vram_gb=6.0,
        bf16_supported=True,
        total_ram_gb=32.0,
        free_ram_gb=16.0,
    )
    payload.update(overrides)
    return MemorySnapshot(**payload)


def test_version_parser_matches_the_qwen2_floor():
    assert parse_version("4.46.0") == (4, 46, 0)
    assert parse_version("4.46.0.dev0") >= MIN_TRANSFORMERS
    assert parse_version("4.37.0") < MIN_TRANSFORMERS
    assert parse_version("4.45.9") < MIN_TRANSFORMERS


def test_seed_and_model_identity():
    cfg = QLoRAConfig()
    assert cfg.seed == SEED == 42
    assert cfg.model_id == MODEL_ID
    assert cfg.quant_bits == 4
    assert cfg.quant_type == "nf4"
    assert cfg.double_quant is True
    assert 0.2 < ESTIMATED_4BIT_WEIGHT_GB < 1.0
    assert cfg.max_seq_length == 512
    assert cfg.min_free_vram_gb == 3.0
    set_seed(cfg.seed)


def test_hyperparameters_are_logged_and_jsonable():
    cfg = QLoRAConfig()
    decision = decide_backend(_snapshot(), cfg)
    record = hyperparameter_record(
        cfg,
        decision,
        {"train": 341, "validation": 68, "test": 91},
        {"transformers": "4.46.0"},
    )
    json.dumps(record)
    for key in REQUIRED_KEYS:
        assert key in record
    assert record["seed"] == 42
    assert record["quantization"]["load_in_4bit"] is True
    assert record["quantization"]["bits"] == 4
    assert record["quantization"]["quant_type"] == "nf4"
    assert record["quantization"]["double_quant"] is True
    assert record["lora_r"] == 8
    assert "q_proj" in record["lora_target_modules"]
    assert "down_proj" in record["lora_target_modules"]


def test_tokenizer_contract_accepts_the_official_qwen2_tokens():
    tokenizer = Qwen2TokenizerFake()
    assert_tokenizer_compatible(tokenizer)
    assert tokenizer.padding_side == "right"


def test_tokenizer_contract_rejects_a_pad_token_aliased_to_eos():
    tokenizer = Qwen2TokenizerFake(pad=EOS_TOKEN)
    try:
        assert_tokenizer_compatible(tokenizer)
    except RuntimeError as exc:
        assert "endoftext" in str(exc)
    else:
        raise AssertionError("pad token aliased to eos must be rejected")


def test_source_uses_peft_bitsandbytes_and_trainer_without_merging():
    sources = "\n".join(path.read_text(encoding="utf-8") for path in ROOT.glob("*.py"))
    assert "LoraConfig" in sources
    assert "BitsAndBytesConfig" in sources
    assert "load_in_4bit=True" in sources
    assert 'bnb_4bit_quant_type="nf4"' in sources
    assert "bnb_4bit_use_double_quant=True" in sources
    assert "from transformers import Trainer" in sources
    assert "merge_and_unload(" not in sources


def test_sources_contain_no_hardcoded_secrets():
    patterns = [
        re.compile(r"api[_-]?key\s*=\s*['\"][A-Za-z0-9_\-]{8,}['\"]", re.I),
        re.compile(r"sk-[A-Za-z0-9]{10,}"),
        re.compile(r"hf_[A-Za-z0-9]{20,}"),
        re.compile(r"AKIA[0-9A-Z]{16}"),
        re.compile(r"BEGIN (?:RSA |OPENSSH )?PRIVATE KEY"),
    ]
    for path in ROOT.glob("*.py"):
        text = path.read_text(encoding="utf-8")
        for pattern in patterns:
            assert pattern.search(text) is None, f"{pattern.pattern} matched {path.name}"
