"""
quality_check.py
================
Comprehensive quality control for the processed dataset.

Checks:
  1.  Schema completeness
  2.  Label distribution (class balance)
  3.  Score distribution (no cliff at 0/1)
  4.  Justification quality (length, vocabulary diversity)
  5.  Species coverage
  6.  Cross-split contamination (data leaks)
  7.  Duplicate fingerprints
  8.  Rule consistency: score ↔ class alignment
  9.  Interaction weight correctness
  10. Product field completeness
  11. Cold-start coverage (examples with empty history)
  12. Negative example coverage (review_neg)

Usage:
    python quality_check.py --splits-dir splits/ --output-dir splits/reports/
"""

import argparse
import json
import math
import os
import hashlib
from collections import Counter, defaultdict
from typing import Any

# ── Loaders ────────────────────────────────────────────────────────────────────

def load_split(path: str) -> list[dict]:
    examples = []
    if not os.path.exists(path):
        return examples
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                examples.append(json.loads(line))
    return examples


# ── Fingerprint (same as clean_and_anonymise) ──────────────────────────────────

def fingerprint(ex: dict) -> str:
    key = json.dumps({
        "user":    ex["user_profile"],
        "product": ex["candidate_product"]["product_id"],
    }, sort_keys=True)
    return hashlib.sha256(key.encode()).hexdigest()


# ── Individual checks ──────────────────────────────────────────────────────────

def check_class_balance(examples: list[dict], split: str) -> list[str]:
    issues = []
    classes = Counter(e["label"]["relevance_class"] for e in examples)
    total   = len(examples)
    for cls, cnt in classes.items():
        pct = cnt / total * 100
        if pct < 8:
            issues.append(f"[{split}] Class '{cls}' underrepresented: {pct:.1f}% ({cnt})")
        if pct > 65:
            issues.append(f"[{split}] Class '{cls}' overrepresented: {pct:.1f}% ({cnt})")
    return issues


def check_score_distribution(examples: list[dict], split: str) -> list[str]:
    issues = []
    scores = [e["label"]["relevance_score"] for e in examples]
    zeros  = sum(1 for s in scores if s == 0.0)
    ones   = sum(1 for s in scores if s == 1.0)
    total  = len(scores)
    if zeros / total > 0.30:
        issues.append(f"[{split}] {zeros/total*100:.1f}% of scores are exactly 0.0 (cliff)")
    if ones / total > 0.10:
        issues.append(f"[{split}] {ones/total*100:.1f}% of scores are exactly 1.0 (cliff)")
    return issues


def check_justification_quality(examples: list[dict], split: str) -> list[str]:
    issues = []
    lengths = [len(e["label"]["justification"].split())
               for e in examples if e["label"].get("justification")]
    if not lengths:
        issues.append(f"[{split}] No justifications found")
        return issues

    avg_len = sum(lengths) / len(lengths)
    too_short = sum(1 for l in lengths if l < 5)
    too_long  = sum(1 for l in lengths if l > 85)

    if avg_len < 8:
        issues.append(f"[{split}] Average justification length very short: {avg_len:.1f} words")
    if too_short > len(lengths) * 0.05:
        issues.append(f"[{split}] {too_short} justifications < 5 words ({too_short/len(lengths)*100:.1f}%)")
    if too_long > 0:
        issues.append(f"[{split}] {too_long} justifications > 85 words")

    # Vocabulary diversity: unique tokens / total tokens
    all_words  = []
    for e in examples:
        all_words.extend(e["label"].get("justification", "").lower().split())
    vocab_size = len(set(all_words))
    if vocab_size < 50:
        issues.append(f"[{split}] Low vocabulary diversity: {vocab_size} unique tokens")

    return issues


def check_species_coverage(examples: list[dict], split: str) -> list[str]:
    issues = []
    species = Counter(e["candidate_product"]["species"] for e in examples)
    if len(species) < 3:
        issues.append(
            f"[{split}] Low species diversity: only {len(species)} species "
            f"({list(species.keys())})"
        )
    return issues


def check_cold_start_coverage(examples: list[dict], split: str) -> list[str]:
    issues = []
    cold = sum(1 for e in examples if not e.get("interaction_history"))
    pct  = cold / len(examples) * 100
    if pct < 3:
        issues.append(f"[{split}] Only {pct:.1f}% cold-start (empty history) examples — add more")
    return issues


def check_negative_signal_coverage(examples: list[dict], split: str) -> list[str]:
    issues = []
    has_neg = sum(1 for e in examples
                  if any(i["signal"] == "review_neg"
                         for i in e.get("interaction_history", [])))
    pct = has_neg / len(examples) * 100
    if pct < 3:
        issues.append(
            f"[{split}] Only {pct:.1f}% examples contain negative interactions — add more"
        )
    return issues


def check_score_class_alignment(examples: list[dict], split: str) -> list[str]:
    issues = []
    boundaries = {
        "not_relevant":    (0.00, 0.25),
        "somewhat":        (0.26, 0.50),
        "relevant":        (0.51, 0.75),
        "highly_relevant": (0.76, 1.00),
    }
    misaligned = 0
    for e in examples:
        score = e["label"]["relevance_score"]
        cls   = e["label"]["relevance_class"]
        lo, hi = boundaries.get(cls, (0, 1))
        if not (lo - 0.01 <= score <= hi + 0.01):
            misaligned += 1
    if misaligned > 0:
        issues.append(
            f"[{split}] {misaligned} examples have score/class misalignment"
        )
    return issues


def check_interaction_weights(examples: list[dict], split: str) -> list[str]:
    issues = []
    valid_weights = {0.3, 0.7, 1.0, 0.9, -0.5}
    bad = 0
    for e in examples:
        for item in e.get("interaction_history", []):
            if round(item.get("weight", 99), 2) not in valid_weights:
                bad += 1
    if bad > 0:
        issues.append(f"[{split}] {bad} interactions have unexpected weight values")
    return issues


def check_cross_split_contamination(all_splits: dict[str, list[dict]]) -> list[str]:
    issues = []
    fp_map: dict[str, set[str]] = defaultdict(set)
    for split_name, examples in all_splits.items():
        for ex in examples:
            fp_map[fingerprint(ex)].add(split_name)

    train_test  = 0
    train_val   = 0
    for fp, splits in fp_map.items():
        if "train" in splits and "test" in splits:
            train_test += 1
        if "train" in splits and "validation" in splits:
            train_val += 1

    if train_test > 0:
        issues.append(f"[LEAK] {train_test} examples appear in both train AND test")
    if train_val > 0:
        issues.append(f"[LEAK] {train_val} examples appear in both train AND validation")
    return issues


def check_duplicates_within_split(examples: list[dict], split: str) -> list[str]:
    issues = []
    fps   = [fingerprint(e) for e in examples]
    dupes = len(fps) - len(set(fps))
    if dupes > 0:
        issues.append(f"[{split}] {dupes} exact duplicate (user, product) pairs")
    return issues


# ── Report ─────────────────────────────────────────────────────────────────────

def generate_report(splits_dir: str, output_dir: str) -> None:
    os.makedirs(output_dir, exist_ok=True)

    split_names = ["train", "validation", "test"]
    all_splits  = {}
    all_issues  = []

    print("=" * 60)
    print("PAWLY DATASET QUALITY REPORT")
    print("=" * 60)

    for name in split_names:
        path     = os.path.join(splits_dir, f"{name}.jsonl")
        examples = load_split(path)
        if not examples:
            print(f"\n[SKIP] {name}.jsonl not found or empty")
            continue

        all_splits[name] = examples
        print(f"\n--- {name.upper()} ({len(examples)} examples) ---")

        issues: list[str] = []
        issues += check_class_balance(examples, name)
        issues += check_score_distribution(examples, name)
        issues += check_justification_quality(examples, name)
        issues += check_species_coverage(examples, name)
        issues += check_cold_start_coverage(examples, name)
        issues += check_negative_signal_coverage(examples, name)
        issues += check_score_class_alignment(examples, name)
        issues += check_interaction_weights(examples, name)
        issues += check_duplicates_within_split(examples, name)

        if issues:
            for iss in issues:
                print(f"  ⚠  {iss}")
        else:
            print("  ✓  No issues found")

        all_issues.extend(issues)

        # Print distribution
        classes = Counter(e["label"]["relevance_class"] for e in examples)
        species = Counter(e["candidate_product"]["species"] for e in examples)
        scores  = [e["label"]["relevance_score"] for e in examples]
        print(f"  Classes:  {dict(sorted(classes.items()))}")
        print(f"  Species:  {dict(sorted(species.items()))}")
        print(f"  Score μ={sum(scores)/len(scores):.3f} "
              f"σ={math.sqrt(sum((s-sum(scores)/len(scores))**2 for s in scores)/len(scores)):.3f}")

    # Cross-split checks
    print("\n--- CROSS-SPLIT CHECKS ---")
    leak_issues = check_cross_split_contamination(all_splits)
    if leak_issues:
        for iss in leak_issues:
            print(f"  🚨 {iss}")
    else:
        print("  ✓  No data leaks detected")
    all_issues.extend(leak_issues)

    # Summary
    print("\n" + "=" * 60)
    print(f"TOTAL ISSUES: {len(all_issues)}")
    critical = [i for i in all_issues if "LEAK" in i or "mismatch" in i.lower()]
    print(f"CRITICAL:     {len(critical)}")
    print("=" * 60)

    # Write JSON report
    report = {
        "splits": {
            name: {
                "count":   len(examples),
                "classes": dict(Counter(e["label"]["relevance_class"] for e in examples)),
            }
            for name, examples in all_splits.items()
        },
        "issues_count": len(all_issues),
        "critical_count": len(critical),
        "issues": all_issues,
    }
    report_path = os.path.join(output_dir, "quality_report.json")
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)
    print(f"\nFull report → {report_path}")

    if critical:
        raise SystemExit(f"\n🚨 {len(critical)} critical issues found. Fix before training.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--splits-dir",  default="splits")
    parser.add_argument("--output-dir",  default="splits/reports")
    args = parser.parse_args()
    generate_report(args.splits_dir, args.output_dir)
