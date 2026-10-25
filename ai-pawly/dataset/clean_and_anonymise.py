"""
clean_and_anonymise.py
======================
Cleans, validates, and anonymises a raw JSONL dataset.

Pipeline:
  1. Schema validation
  2. Text cleaning (whitespace, length, encoding)
  3. Anonymisation (no real user data in synthetic set — guards added for derived data)
  4. Label sanity checks
  5. Duplicate detection
  6. Leak detection (train/test contamination)
  7. Class rebalancing (optional)
  8. Output: processed/clean.jsonl

Usage:
    python clean_and_anonymise.py --input raw/synthetic.jsonl --output processed/clean.jsonl
"""

import argparse
import hashlib
import json
import re
import unicodedata
from collections import Counter, defaultdict
from typing import Any

import uuid

# ── PII patterns (guards for derived data ingestion) ──────────────────────────

_PII_PATTERNS = [
    # Full name: two capitalized words — exclude known product name prefixes
    re.compile(r'(?<!\bRoyal )(?<!\bHill\'s )(?<!\bPurina )(?<!\bInteractive )'
               r'(?<!\bMemory )'
               r'\b[A-Z][a-z]{2,} [A-Z][a-z]{2,}\b'),
    re.compile(r'\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[A-Z|a-z]{2,}\b'),  # email
    re.compile(r'\b\+?[\d\s\-\(\)]{9,15}\b'),              # phone
    re.compile(r'\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b'),# IP address
    re.compile(r'\b[A-Z]{2}\d{6,10}\b'),                   # passport-style ID
]

_REQUIRED_TOP_KEYS = {
    "id", "split", "user_profile", "interaction_history",
    "candidate_product", "label", "meta"
}
_REQUIRED_USER_KEYS = {
    "pet_species", "pet_ages", "pet_sizes",
    "budget_tier", "preferred_categories", "health_interests"
}
_REQUIRED_PRODUCT_KEYS = {
    "product_id", "name", "category", "species",
    "price_eur", "price_tier", "description", "tags",
    "age_target", "size_target", "health_benefits",
    "avg_rating", "review_count"
}
_REQUIRED_LABEL_KEYS = {"relevance_score", "relevance_class", "justification"}

VALID_SPLITS   = {"train", "validation", "test"}
VALID_CLASSES  = {"not_relevant", "somewhat", "relevant", "highly_relevant"}
VALID_BUDGETS  = {"budget", "mid", "premium"}
VALID_SIGNALS  = {"view", "cart", "purchase", "review_pos", "review_neg"}


# ── Text utilities ─────────────────────────────────────────────────────────────

def normalise_text(text: str) -> str:
    """NFC normalisation, collapse whitespace, strip."""
    if not text:
        return ""
    text = unicodedata.normalize("NFC", text)
    text = re.sub(r'\s+', ' ', text).strip()
    return text


def detect_pii(text: str) -> list[str]:
    """Return list of PII pattern names found in text."""
    found = []
    for pattern in _PII_PATTERNS:
        if pattern.search(text):
            found.append(pattern.pattern[:30])
    return found


def anonymise_free_text(text: str) -> str:
    """Replace detected PII with placeholders."""
    text = _PII_PATTERNS[0].sub("[NAME]",  text)
    text = _PII_PATTERNS[1].sub("[EMAIL]", text)
    text = _PII_PATTERNS[2].sub("[PHONE]", text)
    text = _PII_PATTERNS[3].sub("[IP]",    text)
    text = _PII_PATTERNS[4].sub("[ID]",    text)
    return text


# ── Schema validation ──────────────────────────────────────────────────────────

class ValidationError(Exception):
    pass


def validate_example(ex: dict) -> list[str]:
    """Returns list of error strings (empty = valid)."""
    errors = []

    # Top-level keys
    missing_top = _REQUIRED_TOP_KEYS - set(ex.keys())
    if missing_top:
        errors.append(f"Missing top-level keys: {missing_top}")
        return errors  # can't continue without structure

    # Split
    if ex["split"] not in VALID_SPLITS:
        errors.append(f"Invalid split: {ex['split']}")

    # User profile
    up = ex.get("user_profile", {})
    missing_u = _REQUIRED_USER_KEYS - set(up.keys())
    if missing_u:
        errors.append(f"Missing user_profile keys: {missing_u}")
    else:
        if not isinstance(up["pet_species"], list) or not up["pet_species"]:
            errors.append("user_profile.pet_species must be a non-empty list")
        if up.get("budget_tier") not in VALID_BUDGETS:
            errors.append(f"Invalid budget_tier: {up.get('budget_tier')}")

    # Interaction history
    history = ex.get("interaction_history", [])
    if not isinstance(history, list):
        errors.append("interaction_history must be a list")
    else:
        for i, item in enumerate(history):
            if item.get("signal") not in VALID_SIGNALS:
                errors.append(f"history[{i}].signal invalid: {item.get('signal')}")
            if not isinstance(item.get("weight"), (int, float)):
                errors.append(f"history[{i}].weight must be numeric")

    # Candidate product
    cp = ex.get("candidate_product", {})
    missing_p = _REQUIRED_PRODUCT_KEYS - set(cp.keys())
    if missing_p:
        errors.append(f"Missing candidate_product keys: {missing_p}")
    else:
        if not isinstance(cp["price_eur"], (int, float)) or cp["price_eur"] <= 0:
            errors.append("price_eur must be a positive number")
        if not (0.0 <= cp.get("avg_rating", -1) <= 5.0):
            errors.append("avg_rating must be in [0, 5]")
        if not isinstance(cp.get("review_count"), int) or cp["review_count"] < 0:
            errors.append("review_count must be a non-negative integer")

    # Label
    lb = ex.get("label", {})
    missing_l = _REQUIRED_LABEL_KEYS - set(lb.keys())
    if missing_l:
        errors.append(f"Missing label keys: {missing_l}")
    else:
        score = lb.get("relevance_score")
        if not isinstance(score, (int, float)) or not (0.0 <= score <= 1.0):
            errors.append(f"relevance_score must be in [0, 1], got: {score}")
        if lb.get("relevance_class") not in VALID_CLASSES:
            errors.append(f"Invalid relevance_class: {lb.get('relevance_class')}")
        just = lb.get("justification", "")
        if not just or len(just.split()) > 90:
            errors.append(f"Justification empty or > 90 words ({len(just.split())} words)")

    return errors


# ── Label consistency check ────────────────────────────────────────────────────

def check_label_consistency(ex: dict) -> list[str]:
    """Verify score ↔ class alignment."""
    errors = []
    lb = ex.get("label", {})
    score = lb.get("relevance_score", -1)
    cls   = lb.get("relevance_class", "")

    expected = {
        "not_relevant":   (0.00, 0.25),
        "somewhat":       (0.26, 0.50),
        "relevant":       (0.51, 0.75),
        "highly_relevant":(0.76, 1.00),
    }
    if cls in expected:
        lo, hi = expected[cls]
        if not (lo <= score <= hi):
            errors.append(
                f"Score {score} inconsistent with class '{cls}' (expected [{lo}, {hi}])"
            )
    return errors


# ── Deduplication ──────────────────────────────────────────────────────────────

def fingerprint(ex: dict) -> str:
    """Canonical hash of (user_profile, candidate_product_id) for dedup."""
    key = json.dumps({
        "user":    ex["user_profile"],
        "product": ex["candidate_product"]["product_id"],
    }, sort_keys=True)
    return hashlib.sha256(key.encode()).hexdigest()


# ── Data leak detection ────────────────────────────────────────────────────────

def detect_leaks(examples: list[dict]) -> list[str]:
    """
    Detect cases where the same (user_profile, candidate_product) pair
    appears in both train and test splits.
    """
    fp_splits: dict[str, set[str]] = defaultdict(set)
    for ex in examples:
        fp = fingerprint(ex)
        fp_splits[fp].add(ex["split"])

    leaks = []
    for fp, splits in fp_splits.items():
        if "train" in splits and "test" in splits:
            leaks.append(f"Fingerprint {fp[:16]}… appears in both train and test")
        if "train" in splits and "validation" in splits:
            leaks.append(f"Fingerprint {fp[:16]}… appears in both train and validation")
    return leaks


# ── Clean + anonymise single example ──────────────────────────────────────────

def clean_example(ex: dict) -> dict:
    """In-place cleaning and anonymisation. Returns cleaned copy."""
    ex = json.loads(json.dumps(ex))  # deep copy

    # Ensure ID
    if not ex.get("id"):
        ex["id"] = str(uuid.uuid4())

    # Clean free text fields
    cp = ex["candidate_product"]
    cp["name"]        = normalise_text(anonymise_free_text(cp.get("name", "")))
    cp["description"] = normalise_text(anonymise_free_text(cp.get("description", "")))

    lb = ex["label"]
    lb["justification"] = normalise_text(anonymise_free_text(lb.get("justification", "")))

    # Truncate description to 500 chars
    if len(cp["description"]) > 500:
        cp["description"] = cp["description"][:497] + "…"

    # Truncate tags list to 8
    if isinstance(cp.get("tags"), list):
        cp["tags"] = cp["tags"][:8]

    # Truncate interaction history to 10
    if isinstance(ex.get("interaction_history"), list):
        ex["interaction_history"] = ex["interaction_history"][:10]

    # Round numeric fields
    cp["price_eur"]            = round(float(cp.get("price_eur", 0)), 2)
    cp["avg_rating"]           = round(float(cp.get("avg_rating", 0)), 1)
    lb["relevance_score"]      = round(float(lb.get("relevance_score", 0)), 4)

    # Ensure product_id is a string
    cp["product_id"] = str(cp["product_id"])

    return ex


# ── Main pipeline ──────────────────────────────────────────────────────────────

def run(input_path: str, output_path: str, strict: bool = False) -> None:
    raw_examples   = []
    parse_errors   = 0

    print(f"Reading {input_path}…")
    with open(input_path, "r", encoding="utf-8") as f:
        for lineno, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            try:
                raw_examples.append(json.loads(line))
            except json.JSONDecodeError as e:
                parse_errors += 1
                print(f"  Line {lineno}: JSON parse error — {e}")

    print(f"Loaded {len(raw_examples)} raw examples ({parse_errors} parse errors)")

    cleaned    = []
    skipped    = 0
    pii_alerts = 0
    warn_count = 0

    for i, ex in enumerate(raw_examples):
        # Schema validation
        errors = validate_example(ex)
        if errors:
            if strict:
                skipped += 1
                continue
            else:
                for e in errors:
                    print(f"  [WARN] example {i}: {e}")
                warn_count += 1

        # Label consistency
        lc_errors = check_label_consistency(ex)
        for e in lc_errors:
            print(f"  [WARN] example {i} label: {e}")
            warn_count += 1

        # PII detection on all string fields
        all_text = json.dumps(ex)
        if detect_pii(all_text):
            pii_alerts += 1
            print(f"  [PII ALERT] example {i}: potential PII found")

        cleaned.append(clean_example(ex))

    # Deduplication
    seen_fps  = {}
    deduped   = []
    dup_count = 0
    for ex in cleaned:
        fp = fingerprint(ex)
        if fp in seen_fps:
            dup_count += 1
        else:
            seen_fps[fp] = True
            deduped.append(ex)

    print(f"Removed {dup_count} exact duplicates")

    # Leak detection
    leaks = detect_leaks(deduped)
    if leaks:
        print(f"  [LEAK DETECTED] {len(leaks)} train/test contaminations:")
        for leak in leaks[:10]:
            print(f"    {leak}")
    else:
        print("  No data leaks detected - OK")

    # Write output
    import os
    os.makedirs(os.path.dirname(output_path) if os.path.dirname(output_path) else ".", exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        for ex in deduped:
            f.write(json.dumps(ex, ensure_ascii=False) + "\n")

    # Final stats
    classes = Counter(ex["label"]["relevance_class"] for ex in deduped)
    splits  = Counter(ex["split"] for ex in deduped)

    print(f"\n=== Clean dataset: {len(deduped)} examples → {output_path} ===")
    print(f"Skipped (schema error): {skipped}")
    print(f"Warnings:               {warn_count}")
    print(f"PII alerts:             {pii_alerts}")
    print(f"Class distribution:     {dict(classes)}")
    print(f"Split distribution:     {dict(splits)}")

    # Imbalance warning
    total = len(deduped)
    for cls, cnt in classes.items():
        pct = cnt / total * 100
        if pct < 10:
            print(f"  [WARN] Class '{cls}' is underrepresented: {pct:.1f}%")
        if pct > 60:
            print(f"  [WARN] Class '{cls}' is overrepresented: {pct:.1f}%")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input",  default="raw/synthetic.jsonl")
    parser.add_argument("--output", default="processed/clean.jsonl")
    parser.add_argument("--strict", action="store_true",
                        help="Skip examples with schema errors instead of warning")
    args = parser.parse_args()
    run(args.input, args.output, args.strict)
