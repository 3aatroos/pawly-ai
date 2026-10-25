"""
infer.py
========
Score one or more candidate products with a saved LoRA adapter.
The base model is loaded read-only and the adapter is not merged.

Usage:
    python infer.py --adapter outputs/qwen25-0.5b-qlora/adapter --input examples/example.json
"""

from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

from config import QLoRAConfig, SEED, set_seed
from device import apply_bitsandbytes_availability, decide_backend, probe_memory
from metrics import parse_model_response, score_to_class
from modeling import assert_runtime_versions, generate_completion, load_for_inference


logger = logging.getLogger("pawly.qlora.infer")


def load_input(path: str | Path) -> list[dict]:
    raw = Path(path).read_text(encoding="utf-8").strip()
    if not raw:
        raise ValueError(f"{path} is empty.")
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError:
        payload = None
    if isinstance(payload, dict):
        return [payload]
    if isinstance(payload, list):
        if not all(isinstance(item, dict) for item in payload):
            raise ValueError(f"{path} must contain JSON objects.")
        return payload
    rows = []
    for line_number, line in enumerate(raw.splitlines(), start=1):
        line = line.strip()
        if not line:
            continue
        try:
            item = json.loads(line)
        except json.JSONDecodeError as exc:
            raise ValueError(f"{path}:{line_number} is not valid JSON") from exc
        if not isinstance(item, dict):
            raise ValueError(f"{path}:{line_number} is not a JSON object.")
        rows.append(item)
    if not rows:
        raise ValueError(f"{path} does not contain any examples.")
    return rows


def score_examples(examples: list[dict], model, tokenizer, max_new_tokens: int) -> list[dict]:
    results = []
    for example in examples:
        text = generate_completion(model, tokenizer, example, max_new_tokens)
        parsed = parse_model_response(text)
        result = {
            "id": example.get("id"),
            "product_id": example.get("candidate_product", {}).get("product_id"),
            "prediction": text,
            "relevance_score": parsed.score,
            "relevance_class": None if parsed.score is None else score_to_class(parsed.score),
            "justification": parsed.justification,
        }
        label = example.get("label")
        if isinstance(label, dict) and "relevance_score" in label:
            result["gold_score"] = label["relevance_score"]
            result["gold_class"] = label.get("relevance_class")
        results.append(result)
    return results


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    parser = argparse.ArgumentParser(description="Score Pet Store candidates with a LoRA adapter.")
    parser.add_argument("--adapter", required=True)
    parser.add_argument("--input", required=True, help="JSON object, JSON array, or JSONL.")
    parser.add_argument("--seed", type=int, default=SEED)
    parser.add_argument("--cpu", action="store_true")
    args = parser.parse_args()

    cfg = QLoRAConfig(seed=args.seed)
    set_seed(cfg.seed)
    try:
        assert_runtime_versions()
        examples = load_input(args.input)
        decision = decide_backend(probe_memory(), cfg, force_cpu=args.cpu)
        decision = apply_bitsandbytes_availability(decision, cfg)
        logger.info(decision.reason)
        if decision.blocked:
            raise RuntimeError(decision.reason)
        model, tokenizer = load_for_inference(cfg, decision, args.adapter)
        results = score_examples(examples, model, tokenizer, cfg.max_new_tokens)
    except (RuntimeError, FileNotFoundError, ValueError, json.JSONDecodeError) as exc:
        logger.error("%s", exc)
        return 2
    print(json.dumps(results if len(results) > 1 else results[0], indent=2, ensure_ascii=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
