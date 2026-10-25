"""
evaluate.py
===========
Generate on the held-out test split and write recommendation metrics.

train.py never reads the test split. Generation is greedy, so a given adapter
and seed produce the same strings on the same machine.

Usage:
    python evaluate.py --adapter outputs/qwen25-0.5b-qlora/adapter
    python evaluate.py --adapter outputs/qwen25-0.5b-qlora/adapter --limit 20
"""

from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

from config import QLoRAConfig, SEED, set_seed
from device import apply_bitsandbytes_availability, decide_backend, probe_memory
from metrics import evaluate_predictions, parse_model_response, score_to_class
from modeling import assert_runtime_versions, generate_completion, load_for_inference
from prepare_data import DEFAULT_SPLITS, prepare


logger = logging.getLogger("pawly.qlora.evaluate")


def evaluate_split(
    examples: list[dict],
    model,
    tokenizer,
    max_new_tokens: int,
    limit: int | None = None,
) -> tuple[list[dict], dict]:
    chosen = examples if limit is None else examples[:limit]
    rows = []
    predictions = []
    for index, example in enumerate(chosen, start=1):
        text = generate_completion(model, tokenizer, example, max_new_tokens)
        parsed = parse_model_response(text)
        gold_score = float(example["label"]["relevance_score"])
        gold_class = example["label"]["relevance_class"]
        rows.append(
            {
                "gold_score": gold_score,
                "gold_class": gold_class,
                "prediction": text,
            }
        )
        predictions.append(
            {
                "id": example.get("id"),
                "gold_score": gold_score,
                "gold_class": gold_class,
                "prediction": text,
                "pred_score": parsed.score,
                "pred_class": None if parsed.score is None else score_to_class(parsed.score),
                "justification": parsed.justification,
                "clamped": parsed.clamped,
            }
        )
        if index % 10 == 0 or index == len(chosen):
            logger.info("Generated %s / %s", index, len(chosen))
    return predictions, evaluate_predictions(rows)


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    parser = argparse.ArgumentParser(description="Evaluate a Pawly LoRA adapter on the test split.")
    parser.add_argument("--adapter", required=True)
    parser.add_argument("--splits-dir", default=str(DEFAULT_SPLITS))
    parser.add_argument("--output-dir", default=None)
    parser.add_argument("--seed", type=int, default=SEED)
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--cpu", action="store_true")
    args = parser.parse_args()

    adapter = Path(args.adapter)
    output_dir = Path(args.output_dir) if args.output_dir else adapter.parent
    cfg = QLoRAConfig(seed=args.seed)
    set_seed(cfg.seed)
    try:
        assert_runtime_versions()
        splits, _summary = prepare(args.splits_dir, cfg.seed)
        decision = decide_backend(probe_memory(), cfg, force_cpu=args.cpu)
        decision = apply_bitsandbytes_availability(decision, cfg)
        logger.info(decision.reason)
        if decision.blocked:
            raise RuntimeError(decision.reason)
        model, tokenizer = load_for_inference(cfg, decision, str(adapter))
        predictions, metrics = evaluate_split(
            splits["test"], model, tokenizer, cfg.max_new_tokens, args.limit
        )
    except (RuntimeError, FileNotFoundError, ValueError) as exc:
        logger.error("%s", exc)
        return 2

    metrics["seed"] = cfg.seed
    metrics["model_id"] = cfg.model_id
    metrics["adapter"] = str(adapter)
    metrics["split"] = "test"
    output_dir.mkdir(parents=True, exist_ok=True)
    predictions_path = output_dir / "predictions.jsonl"
    with open(predictions_path, "w", encoding="utf-8") as handle:
        for row in predictions:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")
    metrics_path = output_dir / "metrics.json"
    with open(metrics_path, "w", encoding="utf-8") as handle:
        json.dump(metrics, handle, indent=2)
    logger.info("Wrote %s", metrics_path)
    print(json.dumps(metrics, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
