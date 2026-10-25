"""
test_clean_and_anonymise.py
Unit tests for the cleaning and anonymisation pipeline.
Run: python -m pytest tests/ -v
"""

import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from clean_and_anonymise import (
    normalise_text,
    detect_pii,
    anonymise_free_text,
    validate_example,
    check_label_consistency,
    fingerprint,
    detect_leaks,
    clean_example,
)


# ── normalise_text ────────────────────────────────────────────────────────────

class TestNormaliseText:
    def test_collapses_whitespace(self):
        assert normalise_text("a  b   c") == "a b c"

    def test_strips_leading_trailing(self):
        assert normalise_text("  hello  ") == "hello"

    def test_empty_string_returns_empty(self):
        assert normalise_text("") == ""

    def test_none_handled(self):
        assert normalise_text(None) == ""

    def test_nfc_normalisation(self):
        # Decomposed é → composed é
        composed    = "caf\u00e9"
        decomposed  = "cafe\u0301"
        assert normalise_text(decomposed) == composed


# ── detect_pii ────────────────────────────────────────────────────────────────

class TestDetectPII:
    def test_detects_email(self):
        assert detect_pii("contact: user@example.com") != []

    def test_detects_ip(self):
        assert detect_pii("from 192.168.1.1") != []

    def test_clean_text_returns_empty(self):
        assert detect_pii("Great product for dogs") == []

    def test_synthetic_id_not_flagged(self):
        assert detect_pii("product_id: PROD_0042") == []


# ── anonymise_free_text ───────────────────────────────────────────────────────

class TestAnonymiseFreeText:
    def test_replaces_email(self):
        result = anonymise_free_text("contact user@test.com for info")
        assert "@" not in result
        assert "[EMAIL]" in result

    def test_replaces_ip(self):
        result = anonymise_free_text("IP: 192.168.0.1")
        assert "192.168" not in result

    def test_clean_text_unchanged(self):
        text   = "Great product for adult dogs"
        assert anonymise_free_text(text) == text


# ── validate_example ──────────────────────────────────────────────────────────

def _valid_example() -> dict:
    return {
        "id":    "abc-123",
        "split": "train",
        "user_profile": {
            "pet_species":          ["dog"],
            "pet_ages":             ["adult"],
            "pet_sizes":            ["large"],
            "budget_tier":          "mid",
            "preferred_categories": ["Food"],
            "health_interests":     ["joint-support"],
        },
        "interaction_history": [
            {"product_id": "PROD_001", "product_name": "Dog Food",
             "signal": "purchase", "weight": 1.0}
        ],
        "candidate_product": {
            "product_id":     "PROD_042",
            "name":           "Royal Canin Adult",
            "category":       "Food",
            "species":        "dog",
            "price_eur":      22.50,
            "price_tier":     "mid",
            "description":    "Complete nutrition for adult dogs.",
            "tags":           ["kibble","adult"],
            "age_target":     "adult",
            "size_target":    "large",
            "health_benefits": ["joint-support"],
            "avg_rating":     4.5,
            "review_count":   150,
        },
        "label": {
            "relevance_score": 0.82,
            "relevance_class": "highly_relevant",
            "justification":   "Perfect species, age, and budget match with joint health benefit.",
        },
        "meta": {
            "source":          "synthetic",
            "generation_rule": "heuristic_v1",
            "created_at":      "2026-10-25T12:00:00+00:00",
        }
    }


class TestValidateExample:
    def test_valid_example_has_no_errors(self):
        ex = _valid_example()
        assert validate_example(ex) == []

    def test_missing_top_level_key(self):
        ex = _valid_example()
        del ex["label"]
        errors = validate_example(ex)
        assert any("label" in e.lower() for e in errors)

    def test_invalid_split(self):
        ex = _valid_example()
        ex["split"] = "holdout"
        errors = validate_example(ex)
        assert any("split" in e.lower() for e in errors)

    def test_invalid_budget_tier(self):
        ex = _valid_example()
        ex["user_profile"]["budget_tier"] = "ultra"
        errors = validate_example(ex)
        assert any("budget" in e.lower() for e in errors)

    def test_invalid_signal(self):
        ex = _valid_example()
        ex["interaction_history"][0]["signal"] = "wishlist"
        errors = validate_example(ex)
        assert any("signal" in e.lower() for e in errors)

    def test_negative_price(self):
        ex = _valid_example()
        ex["candidate_product"]["price_eur"] = -5.0
        errors = validate_example(ex)
        assert any("price" in e.lower() for e in errors)

    def test_rating_out_of_range(self):
        ex = _valid_example()
        ex["candidate_product"]["avg_rating"] = 6.0
        errors = validate_example(ex)
        assert any("rating" in e.lower() for e in errors)

    def test_score_out_of_range(self):
        ex = _valid_example()
        ex["label"]["relevance_score"] = 1.5
        errors = validate_example(ex)
        assert any("score" in e.lower() for e in errors)

    def test_empty_pet_species(self):
        ex = _valid_example()
        ex["user_profile"]["pet_species"] = []
        errors = validate_example(ex)
        assert any("species" in e.lower() for e in errors)

    def test_justification_too_long(self):
        ex = _valid_example()
        ex["label"]["justification"] = " ".join(["word"] * 100)
        errors = validate_example(ex)
        assert any("justification" in e.lower() or "word" in e.lower() for e in errors)


# ── check_label_consistency ───────────────────────────────────────────────────

class TestCheckLabelConsistency:
    def test_consistent_label_has_no_errors(self):
        ex = _valid_example()
        assert check_label_consistency(ex) == []

    def test_score_too_high_for_class(self):
        ex = _valid_example()
        ex["label"]["relevance_class"] = "not_relevant"
        ex["label"]["relevance_score"] = 0.80  # should be 0–0.25
        errors = check_label_consistency(ex)
        assert len(errors) > 0

    def test_score_too_low_for_class(self):
        ex = _valid_example()
        ex["label"]["relevance_class"] = "highly_relevant"
        ex["label"]["relevance_score"] = 0.20   # should be 0.76–1.0
        errors = check_label_consistency(ex)
        assert len(errors) > 0


# ── fingerprint & detect_leaks ────────────────────────────────────────────────

class TestFingerprintAndLeaks:
    def test_same_user_product_has_same_fingerprint(self):
        ex1 = _valid_example()
        ex2 = _valid_example()
        ex2["split"] = "test"
        assert fingerprint(ex1) == fingerprint(ex2)

    def test_different_product_different_fingerprint(self):
        ex1 = _valid_example()
        ex2 = _valid_example()
        ex2["candidate_product"]["product_id"] = "PROD_999"
        assert fingerprint(ex1) != fingerprint(ex2)

    def test_detect_train_test_leak(self):
        train_ex = _valid_example()
        train_ex["split"] = "train"
        test_ex  = _valid_example()
        test_ex["split"] = "test"
        leaks = detect_leaks([train_ex, test_ex])
        assert len(leaks) > 0

    def test_no_leak_when_different_examples(self):
        train_ex = _valid_example()
        train_ex["split"] = "train"
        test_ex  = _valid_example()
        test_ex["split"]  = "test"
        test_ex["candidate_product"]["product_id"] = "PROD_999"
        leaks = detect_leaks([train_ex, test_ex])
        assert len(leaks) == 0


# ── clean_example ─────────────────────────────────────────────────────────────

class TestCleanExample:
    def test_truncates_long_description(self):
        ex = _valid_example()
        ex["candidate_product"]["description"] = "x" * 600
        cleaned = clean_example(ex)
        assert len(cleaned["candidate_product"]["description"]) <= 503  # 500 + "…"

    def test_truncates_tags_to_8(self):
        ex = _valid_example()
        ex["candidate_product"]["tags"] = [f"tag{i}" for i in range(15)]
        cleaned = clean_example(ex)
        assert len(cleaned["candidate_product"]["tags"]) <= 8

    def test_truncates_history_to_10(self):
        ex      = _valid_example()
        item    = ex["interaction_history"][0]
        ex["interaction_history"] = [item] * 15
        cleaned = clean_example(ex)
        assert len(cleaned["interaction_history"]) <= 10

    def test_price_rounded_to_2dp(self):
        ex = _valid_example()
        ex["candidate_product"]["price_eur"] = 22.123456
        cleaned = clean_example(ex)
        assert cleaned["candidate_product"]["price_eur"] == 22.12

    def test_does_not_mutate_original(self):
        ex      = _valid_example()
        orig_id = ex["candidate_product"]["product_id"]
        cleaned = clean_example(ex)
        assert ex["candidate_product"]["product_id"] == orig_id
