"""Adapter-only checkpoints and the trainable-parameter guard."""

import json
from pathlib import Path

import pytest

from adapters import assert_adapters_only, assert_only_lora_trainable, save_adapters_only
from infer import load_input


class _Param:
    def __init__(self, count: int, requires_grad: bool):
        self._count = count
        self.requires_grad = requires_grad

    def numel(self) -> int:
        return self._count


class _Model:
    def __init__(self, params, files):
        self._params = params
        self._files = files
        self.peft_config = {"default": {}}

    def named_parameters(self):
        return iter(self._params)

    def save_pretrained(self, path):
        root = Path(path)
        root.mkdir(parents=True, exist_ok=True)
        for name, content in self._files.items():
            (root / name).write_text(content, encoding="utf-8")


class _Tokenizer:
    def save_pretrained(self, path):
        (Path(path) / "tokenizer_config.json").write_text("{}", encoding="utf-8")


def test_lora_guard_accepts_frozen_base_weights():
    model = _Model(
        [
            ("model.layers.0.weight", _Param(1000, False)),
            ("model.layers.0.lora_A.default.weight", _Param(8, True)),
            ("model.layers.0.lora_B.default.weight", _Param(8, True)),
        ],
        {},
    )
    stats = assert_only_lora_trainable(model)
    assert stats["trainable"] == 16
    assert stats["trainable"] < stats["total"]


def test_lora_guard_rejects_a_trainable_base_weight():
    model = _Model(
        [
            ("model.layers.0.weight", _Param(1000, True)),
            ("model.layers.0.lora_A.default.weight", _Param(8, True)),
        ],
        {},
    )
    with pytest.raises(RuntimeError, match="Non-LoRA"):
        assert_only_lora_trainable(model)


def test_save_writes_the_adapter_and_rejects_full_weights(tmp_path):
    model = _Model(
        [],
        {
            "adapter_config.json": "{}",
            "adapter_model.safetensors": "adapter",
        },
    )
    saved = save_adapters_only(model, tmp_path / "adapter", _Tokenizer())
    names = set(assert_adapters_only(saved))
    assert "adapter_config.json" in names
    assert "adapter_model.safetensors" in names
    assert "tokenizer_config.json" in names
    assert "model.safetensors" not in names


def test_full_weight_files_are_rejected(tmp_path):
    (tmp_path / "adapter_config.json").write_text("{}", encoding="utf-8")
    (tmp_path / "adapter_model.safetensors").write_text("adapter", encoding="utf-8")
    (tmp_path / "model.safetensors").write_text("full", encoding="utf-8")
    with pytest.raises(RuntimeError, match="Full model weights"):
        assert_adapters_only(tmp_path)


def test_a_model_without_peft_is_not_saved(tmp_path):
    class Bare:
        def save_pretrained(self, path):
            raise AssertionError("full weights must not be written")

    with pytest.raises(RuntimeError, match="no PEFT adapter"):
        save_adapters_only(Bare(), tmp_path)


def test_inference_input_accepts_json_and_jsonl(tmp_path):
    example = json.loads(
        (Path(__file__).resolve().parents[1] / "examples" / "example.json").read_text(encoding="utf-8")
    )
    pretty = tmp_path / "one.json"
    pretty.write_text(json.dumps(example, indent=2), encoding="utf-8")
    assert load_input(pretty)[0]["candidate_product"]["product_id"] == "PROD_0204"
    lines = tmp_path / "many.jsonl"
    lines.write_text(json.dumps(example) + "\n" + json.dumps(example) + "\n", encoding="utf-8")
    assert len(load_input(lines)) == 2
