"""
split_dataset.py
================
Produces stratified train/validation/test splits from a clean JSONL file.

Stratification is done on (relevance_class, species) to ensure balanced
class and species coverage in every split.

Ratios: 70 / 15 / 15 (configurable)

Usage:
    python split_dataset.py --input processed/clean.jsonl --output-dir splits/
"""

import argparse
import json
import math
import os
import random
from collections import defaultdict


def load_jsonl(path: str) -> list[dict]:
    examples = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                examples.append(json.loads(line))
    return examples


def write_jsonl(examples: list[dict], path: str, split_name: str) -> None:
    os.makedirs(os.path.dirname(path) if os.path.dirname(path) else ".", exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        for ex in examples:
            ex["split"] = split_name
            f.write(json.dumps(ex, ensure_ascii=False) + "\n")


def stratify_split(
    examples:       list[dict],
    train_ratio:    float = 0.70,
    val_ratio:      float = 0.15,
    seed:           int   = 42,
) -> tuple[list[dict], list[dict], list[dict]]:
    """
    Stratified split by (relevance_class, target_species).
    Falls back to random if a stratum has < 3 examples.
    """
    rng = random.Random(seed)

    # Group by stratum key
    strata: dict[str, list[dict]] = defaultdict(list)
    for ex in examples:
        cls     = ex["label"]["relevance_class"]
        species = ex["candidate_product"]["species"]
        key     = f"{cls}__{species}"
        strata[key].append(ex)

    train_list, val_list, test_list = [], [], []

    for key, group in strata.items():
        rng.shuffle(group)
        n       = len(group)
        n_train = max(1, math.floor(n * train_ratio))
        n_val   = max(1, math.floor(n * val_ratio)) if n > 2 else 0
        n_test  = n - n_train - n_val

        if n_test < 0:
            n_test  = 0
            n_val   = max(0, n - n_train)

        train_list.extend(group[:n_train])
        val_list.extend(group[n_train:n_train + n_val])
        test_list.extend(group[n_train + n_val:])

    rng.shuffle(train_list)
    rng.shuffle(val_list)
    rng.shuffle(test_list)

    return train_list, val_list, test_list


def print_stats(name: str, examples: list[dict]) -> None:
    from collections import Counter
    classes = Counter(e["label"]["relevance_class"] for e in examples)
    species = Counter(e["candidate_product"]["species"] for e in examples)
    print(f"\n{name}: {len(examples)} examples")
    print(f"  Classes:  {dict(classes)}")
    print(f"  Species:  {dict(species)}")

    # Check minimum class count
    for cls, cnt in classes.items():
        if cnt < 5:
            print(f"  [WARN] Very few examples for class '{cls}': {cnt}")


def run(
    input_path:  str,
    output_dir:  str,
    train_ratio: float,
    val_ratio:   float,
    seed:        int,
) -> None:
    print(f"Loading {input_path}…")
    examples = load_jsonl(input_path)
    print(f"Total examples: {len(examples)}")

    if len(examples) < 50:
        print("[WARN] Very small dataset — results may not be statistically meaningful")

    train, val, test = stratify_split(examples, train_ratio, val_ratio, seed)

    # Write splits
    write_jsonl(train, os.path.join(output_dir, "train.jsonl"),      "train")
    write_jsonl(val,   os.path.join(output_dir, "validation.jsonl"), "validation")
    write_jsonl(test,  os.path.join(output_dir, "test.jsonl"),       "test")

    # Stats
    print_stats("Train",      train)
    print_stats("Validation", val)
    print_stats("Test",       test)

    total = len(train) + len(val) + len(test)
    print(f"\nTotal after split: {total}")
    print(f"  train:      {len(train):>5} ({len(train)/total*100:.1f}%)")
    print(f"  validation: {len(val):>5}   ({len(val)/total*100:.1f}%)")
    print(f"  test:       {len(test):>5}   ({len(test)/total*100:.1f}%)")

    # Write a summary JSON
    summary = {
        "total":      total,
        "train":      len(train),
        "validation": len(val),
        "test":       len(test),
        "train_ratio":      train_ratio,
        "val_ratio":        val_ratio,
        "test_ratio":       round(1 - train_ratio - val_ratio, 4),
        "seed":             seed,
        "stratification":   "relevance_class × species",
        "train_classes":    {k: v for k, v in sorted(
            __import__('collections').Counter(
                e["label"]["relevance_class"] for e in train
            ).items()
        )},
    }
    with open(os.path.join(output_dir, "split_summary.json"), "w") as f:
        json.dump(summary, f, indent=2)
    print(f"\nSplit summary → {os.path.join(output_dir, 'split_summary.json')}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input",       default="processed/clean.jsonl")
    parser.add_argument("--output-dir",  default="splits")
    parser.add_argument("--train-ratio", type=float, default=0.70)
    parser.add_argument("--val-ratio",   type=float, default=0.15)
    parser.add_argument("--seed",        type=int,   default=42)
    args = parser.parse_args()
    run(args.input, args.output_dir, args.train_ratio, args.val_ratio, args.seed)
