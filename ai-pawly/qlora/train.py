"""
train.py
========
QLoRA fine-tuning of the Pawly recommendation model.

The Trainer optimises the completion tokens only (prompt labels are -100).
Checkpoints written by the Trainer stay in the run directory; the deliverable
is outputs/.../adapter, which contains the LoRA adapter and not the base weights.

Usage:
    python train.py
    python train.py --cpu
    python train.py --max-steps 2 --overwrite
"""

from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

from adapters import save_adapters_only
from config import QLoRAConfig, SEED, hyperparameter_record, set_seed
from device import (
    apply_bitsandbytes_availability,
    decide_backend,
    memory_after_load,
    probe_memory,
)
from modeling import (
    assert_runtime_versions,
    build_training_arguments,
    load_for_training,
)
from prepare_data import (
    DEFAULT_SPLITS,
    make_trainer_collator,
    prepare,
    summarize_splits,
    tokenize_split,
)


QLORA_DIR = Path(__file__).resolve().parent
DEFAULT_OUTPUT = QLORA_DIR / "outputs" / "qwen25-0.5b-qlora"
logger = logging.getLogger("pawly.qlora.train")


def _json_safe(value):
    if isinstance(value, dict):
        return {str(key): _json_safe(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_json_safe(item) for item in value]
    if hasattr(value, "item") and callable(value.item):
        try:
            return _json_safe(value.item())
        except (ValueError, TypeError):
            pass
    if isinstance(value, (str, int, float, bool)) or value is None:
        return value
    return str(value)


def _write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(_json_safe(payload), handle, indent=2)


def train(cfg: QLoRAConfig, splits_dir: Path, output_dir: Path, force_cpu: bool, overwrite: bool) -> dict:
    set_seed(cfg.seed)
    splits, summary = prepare(splits_dir, cfg.seed)
    split_counts = summarize_splits(splits, summary)["counts"]

    snapshot = probe_memory()
    decision = decide_backend(snapshot, cfg, force_cpu=force_cpu)
    decision = apply_bitsandbytes_availability(decision, cfg)
    logger.info(decision.reason)
    logger.info(
        "Memory before load: free RAM %s GB, free VRAM %s GB",
        snapshot.free_ram_gb,
        snapshot.free_vram_gb,
    )

    versions = {}
    record = hyperparameter_record(cfg, decision, split_counts, versions)
    _write_json(output_dir / "hyperparameters.json", record)
    _write_json(output_dir / "device_report.json", {"before_load": decision.to_dict()})
    if decision.blocked:
        raise RuntimeError(decision.reason)

    versions = assert_runtime_versions()
    record = hyperparameter_record(cfg, decision, split_counts, versions)
    _write_json(output_dir / "hyperparameters.json", record)

    model, tokenizer, trainable = load_for_training(cfg, decision)
    logger.info(
        "Trainable parameters: %s / %s (%.4f%%)",
        trainable["trainable"],
        trainable["total"],
        100.0 * trainable["trainable_ratio"],
    )
    after = memory_after_load()
    logger.info("Memory after load: %s", json.dumps(after))
    _write_json(
        output_dir / "device_report.json",
        {"before_load": decision.to_dict(), "after_load": after, "trainable": trainable},
    )

    train_rows, train_skipped = tokenize_split(splits["train"], tokenizer, cfg.max_seq_length)
    val_rows, val_skipped = tokenize_split(splits["validation"], tokenizer, cfg.max_seq_length)
    logger.info(
        "Tokenized train=%s (skipped %s), validation=%s (skipped %s)",
        len(train_rows),
        train_skipped,
        len(val_rows),
        val_skipped,
    )

    import inspect
    from torch.utils.data import Dataset
    from transformers import Trainer

    class Rows(Dataset):
        def __init__(self, rows: list[dict]):
            self.rows = rows

        def __len__(self) -> int:
            return len(self.rows)

        def __getitem__(self, index: int) -> dict:
            return self.rows[index]

    args = build_training_arguments(cfg, decision, str(output_dir / "checkpoints"), overwrite)
    trainer_kwargs = {
        "model": model,
        "args": args,
        "train_dataset": Rows(train_rows),
        "eval_dataset": Rows(val_rows),
        "data_collator": make_trainer_collator(tokenizer.pad_token_id),
    }
    parameters = inspect.signature(Trainer.__init__).parameters
    if "processing_class" in parameters:
        trainer_kwargs["processing_class"] = tokenizer
    else:
        trainer_kwargs["tokenizer"] = tokenizer
    trainer = Trainer(**trainer_kwargs)
    train_result = trainer.train()
    adapter_dir = save_adapters_only(trainer.model, output_dir / "adapter", tokenizer)

    metrics = {
        "train_runtime_metrics": train_result.metrics,
        "log_history": trainer.state.log_history,
        "skipped": {"train": train_skipped, "validation": val_skipped},
        "adapter_dir": str(adapter_dir),
        "seed": cfg.seed,
    }
    _write_json(output_dir / "metrics_train.json", metrics)
    logger.info("LoRA adapter saved to %s", adapter_dir)
    return metrics


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    parser = argparse.ArgumentParser(description="QLoRA fine-tune the Pawly recommendation model.")
    parser.add_argument("--splits-dir", default=str(DEFAULT_SPLITS))
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT))
    parser.add_argument("--seed", type=int, default=SEED)
    parser.add_argument("--epochs", type=float, default=None)
    parser.add_argument("--max-steps", type=int, default=None)
    parser.add_argument("--learning-rate", type=float, default=None)
    parser.add_argument("--cpu", action="store_true", help="Force the explicit CPU LoRA fallback.")
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args()

    cfg = QLoRAConfig(seed=args.seed)
    if args.epochs is not None:
        cfg.num_train_epochs = args.epochs
    if args.max_steps is not None:
        cfg.max_steps = args.max_steps
    if args.learning_rate is not None:
        cfg.learning_rate = args.learning_rate

    try:
        train(cfg, Path(args.splits_dir), Path(args.output_dir), args.cpu, args.overwrite)
    except (RuntimeError, FileNotFoundError, ValueError) as exc:
        logger.error("%s", exc)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
