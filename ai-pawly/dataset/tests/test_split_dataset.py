"""
test_split_dataset.py
Unit tests for the stratified split pipeline.
Run: python -m pytest tests/ -v
"""

import sys
import os
import random

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from split_dataset import stratify_split


def _make_examples(n: int, seed: int = 42) -> list[dict]:
    rng     = random.Random(seed)
    classes = ["not_relevant", "somewhat", "relevant", "highly_relevant"]
    species = ["dog", "cat", "rabbit", "all"]
    examples = []
    for i in range(n):
        examples.append({
            "id":    f"ex_{i:04d}",
            "split": "train",
            "user_profile": {"pet_species": [rng.choice(["dog", "cat"])],
                             "pet_ages": ["adult"], "pet_sizes": ["medium"],
                             "budget_tier": "mid", "preferred_categories": ["Food"],
                             "health_interests": []},
            "interaction_history": [],
            "candidate_product": {
                "product_id": f"PROD_{i:04d}",
                "species":    rng.choice(species),
                "price_eur":  rng.uniform(5, 60),
            },
            "label": {
                "relevance_score": rng.uniform(0, 1),
                "relevance_class": rng.choice(classes),
                "justification":   "Test justification.",
            },
            "meta": {"source": "synthetic", "generation_rule": "test"},
        })
    return examples


class TestStratifySplit:
    def test_split_counts_sum_to_total(self):
        examples = _make_examples(200)
        train, val, test = stratify_split(examples)
        assert len(train) + len(val) + len(test) == len(examples)

    def test_train_ratio_approximately_correct(self):
        examples = _make_examples(500)
        train, val, test = stratify_split(examples, train_ratio=0.70, val_ratio=0.15)
        ratio = len(train) / len(examples)
        assert 0.60 <= ratio <= 0.80, f"Train ratio {ratio:.2f} out of expected range"

    def test_no_overlap_between_splits(self):
        examples = _make_examples(300)
        train, val, test = stratify_split(examples)
        train_ids = {e["id"] for e in train}
        val_ids   = {e["id"] for e in val}
        test_ids  = {e["id"] for e in test}
        assert train_ids.isdisjoint(val_ids)
        assert train_ids.isdisjoint(test_ids)
        assert val_ids.isdisjoint(test_ids)

    def test_all_examples_assigned(self):
        examples = _make_examples(300)
        train, val, test = stratify_split(examples)
        all_ids  = {e["id"] for e in train + val + test}
        orig_ids = {e["id"] for e in examples}
        assert all_ids == orig_ids

    def test_deterministic_with_same_seed(self):
        examples = _make_examples(200)
        t1, v1, ts1 = stratify_split(examples, seed=42)
        t2, v2, ts2 = stratify_split(examples, seed=42)
        assert [e["id"] for e in t1]  == [e["id"] for e in t2]
        assert [e["id"] for e in ts1] == [e["id"] for e in ts2]

    def test_different_seeds_differ(self):
        examples = _make_examples(200)
        t1, _, _ = stratify_split(examples, seed=1)
        t2, _, _ = stratify_split(examples, seed=99)
        # Very unlikely to be identical
        assert [e["id"] for e in t1] != [e["id"] for e in t2]

    def test_small_dataset_handled(self):
        examples = _make_examples(10)
        train, val, test = stratify_split(examples)
        total = len(train) + len(val) + len(test)
        assert total == len(examples)

    def test_single_example_handled(self):
        examples = _make_examples(1)
        train, val, test = stratify_split(examples)
        assert len(train) + len(val) + len(test) == 1

    def test_each_split_non_empty_with_large_dataset(self):
        examples = _make_examples(200)
        train, val, test = stratify_split(examples)
        assert len(train) > 0
        assert len(val)   > 0
        assert len(test)  > 0
