"""
test_generate_synthetic.py
Unit tests for the synthetic data generation pipeline.
Run: python -m pytest tests/ -v
"""

import json
import sys
import os
import random

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from generate_synthetic import (
    make_product,
    make_user_profile,
    make_interactions,
    compute_label,
    build_example,
    SIGNAL_WEIGHTS,
    CATEGORIES,
)


def _rng(seed: int = 0) -> random.Random:
    return random.Random(seed)


def _make_cat(rng, idx=1):
    return make_product("Food", "dog", "adult", "large", rng, idx)


def _make_user(rng):
    return {
        "pet_species":          ["dog"],
        "pet_ages":             ["adult"],
        "pet_sizes":            ["large"],
        "budget_tier":          "mid",
        "preferred_categories": ["Food"],
        "health_interests":     ["joint-support"],
    }


# ── make_product ──────────────────────────────────────────────────────────────

class TestMakeProduct:
    def test_returns_dict_with_required_keys(self):
        rng  = _rng()
        prod = _make_cat(rng)
        required = {
            "product_id","name","category","species","price_eur","price_tier",
            "description","tags","age_target","size_target","health_benefits",
            "avg_rating","review_count",
        }
        assert required.issubset(set(prod.keys()))

    def test_price_tier_consistent_with_price(self):
        rng  = _rng()
        prod = _make_cat(rng)
        price = prod["price_eur"]
        tier  = prod["price_tier"]
        if price < 15:
            assert tier == "budget"
        elif price < 40:
            assert tier == "mid"
        else:
            assert tier == "premium"

    def test_avg_rating_in_range(self):
        rng = _rng()
        for _ in range(20):
            prod = _make_cat(rng, idx=1)
            assert 0.0 <= prod["avg_rating"] <= 5.0

    def test_tags_list_not_empty(self):
        rng  = _rng()
        prod = _make_cat(rng)
        assert isinstance(prod["tags"], list)
        assert len(prod["tags"]) >= 1

    def test_product_id_is_string(self):
        rng  = _rng()
        prod = _make_cat(rng)
        assert isinstance(prod["product_id"], str)


# ── make_user_profile ─────────────────────────────────────────────────────────

class TestMakeUserProfile:
    def test_returns_required_keys(self):
        rng  = _rng()
        user = make_user_profile(rng)
        required = {
            "pet_species","pet_ages","pet_sizes","budget_tier",
            "preferred_categories","health_interests"
        }
        assert required.issubset(set(user.keys()))

    def test_pet_species_non_empty_list(self):
        rng  = _rng()
        user = make_user_profile(rng)
        assert isinstance(user["pet_species"], list)
        assert len(user["pet_species"]) >= 1

    def test_budget_tier_valid(self):
        rng  = _rng()
        user = make_user_profile(rng)
        assert user["budget_tier"] in {"budget", "mid", "premium"}


# ── make_interactions ─────────────────────────────────────────────────────────

class TestMakeInteractions:
    def test_interactions_have_valid_signals(self):
        rng      = _rng()
        user     = _make_user(rng)
        products = [_make_cat(rng, i) for i in range(10)]
        history  = make_interactions(user, products, rng)
        valid_signals = set(SIGNAL_WEIGHTS.keys())
        for item in history:
            assert item["signal"] in valid_signals

    def test_weights_match_signals(self):
        rng      = _rng()
        user     = _make_user(rng)
        products = [_make_cat(rng, i) for i in range(10)]
        history  = make_interactions(user, products, rng)
        for item in history:
            expected = SIGNAL_WEIGHTS[item["signal"]]
            assert abs(item["weight"] - expected) < 1e-6

    def test_interactions_list_length(self):
        rng      = _rng()
        user     = _make_user(rng)
        products = [_make_cat(rng, i) for i in range(20)]
        history  = make_interactions(user, products, rng)
        assert 1 <= len(history) <= 10


# ── compute_label ─────────────────────────────────────────────────────────────

class TestComputeLabel:
    def test_highly_relevant_for_perfect_match(self):
        rng     = _rng()
        user    = _make_user(rng)
        product = {
            "product_id":     "PROD_001",
            "name":           "JointFlex Senior Dog",
            "category":       "Food",
            "species":        "dog",
            "price_eur":      22.0,
            "price_tier":     "mid",
            "description":    "...",
            "tags":           ["joint"],
            "age_target":     "adult",
            "size_target":    "large",
            "health_benefits": ["joint-support"],
            "avg_rating":     4.8,
            "review_count":   200,
        }
        label = compute_label(user, product, [], rng)
        assert label["relevance_score"] >= 0.70
        assert label["relevance_class"] in {"relevant", "highly_relevant"}

    def test_not_relevant_for_species_mismatch(self):
        rng     = _rng()
        user    = _make_user(rng)           # has dog
        product = {
            "product_id":     "PROD_002",
            "name":           "Cat Food Premium",
            "category":       "Food",
            "species":        "cat",         # cat only
            "price_eur":      20.0,
            "price_tier":     "mid",
            "description":    "...",
            "tags":           [],
            "age_target":     "adult",
            "size_target":    "all",
            "health_benefits": [],
            "avg_rating":     4.0,
            "review_count":   50,
        }
        label = compute_label(user, product, [], rng)
        assert label["relevance_score"] <= 0.30
        assert label["relevance_class"] in {"not_relevant", "somewhat"}

    def test_negative_review_reduces_score(self):
        rng     = _rng()
        user    = _make_user(rng)
        product = {
            "product_id":     "PROD_003",
            "category":       "Food", "species": "dog",
            "name":           "Dog Food", "price_eur": 20.0, "price_tier": "mid",
            "description":    "...", "tags": [],
            "age_target":     "adult", "size_target": "large",
            "health_benefits": ["joint-support"],
            "avg_rating":      4.0, "review_count": 10,
        }
        history_neg = [{"product_id": "PROD_003", "signal": "review_neg", "weight": -0.5}]
        history_pos = []

        label_neg = compute_label(user, product, history_neg, rng)
        label_pos = compute_label(user, product, history_pos, rng)
        assert label_neg["relevance_score"] < label_pos["relevance_score"]

    def test_score_always_in_range(self):
        rng      = _rng()
        user     = _make_user(rng)
        products = [_make_cat(rng, i) for i in range(50)]
        for p in products:
            label = compute_label(user, p, [], rng)
            assert 0.0 <= label["relevance_score"] <= 1.0

    def test_label_has_required_keys(self):
        rng     = _rng()
        user    = _make_user(rng)
        product = _make_cat(rng)
        label   = compute_label(user, product, [], rng)
        assert {"relevance_score", "relevance_class", "justification"}.issubset(label.keys())

    def test_justification_non_empty_string(self):
        rng     = _rng()
        user    = _make_user(rng)
        product = _make_cat(rng)
        label   = compute_label(user, product, [], rng)
        assert isinstance(label["justification"], str)
        assert len(label["justification"]) > 0

    def test_score_class_alignment(self):
        boundaries = {
            "not_relevant":    (0.00, 0.25),
            "somewhat":        (0.26, 0.50),
            "relevant":        (0.51, 0.75),
            "highly_relevant": (0.76, 1.00),
        }
        rng      = _rng(99)
        user     = _make_user(rng)
        products = [_make_cat(rng, i) for i in range(100)]
        for p in products:
            label = compute_label(user, p, [], rng)
            score = label["relevance_score"]
            cls   = label["relevance_class"]
            lo, hi = boundaries[cls]
            assert lo - 0.01 <= score <= hi + 0.01, \
                f"score={score} class={cls} bounds=[{lo},{hi}]"


# ── build_example ─────────────────────────────────────────────────────────────

class TestBuildExample:
    def test_example_has_all_top_level_keys(self):
        rng     = _rng()
        user    = _make_user(rng)
        product = _make_cat(rng)
        example = build_example(user, product, [], rng, "train", 0)
        required = {"id","split","user_profile","interaction_history",
                    "candidate_product","label","meta"}
        assert required.issubset(set(example.keys()))

    def test_split_value_correct(self):
        rng     = _rng()
        user    = _make_user(rng)
        product = _make_cat(rng)
        for split in ["train","validation","test"]:
            ex = build_example(user, product, [], rng, split, 0)
            assert ex["split"] == split

    def test_id_is_uuid(self):
        import uuid
        rng     = _rng()
        user    = _make_user(rng)
        product = _make_cat(rng)
        ex      = build_example(user, product, [], rng, "train", 0)
        uuid.UUID(ex["id"])  # raises if invalid

    def test_meta_has_source_and_rule(self):
        rng     = _rng()
        user    = _make_user(rng)
        product = _make_cat(rng)
        ex      = build_example(user, product, [], rng, "train", 0)
        assert ex["meta"]["source"]          == "synthetic"
        assert ex["meta"]["generation_rule"] == "heuristic_v1"
