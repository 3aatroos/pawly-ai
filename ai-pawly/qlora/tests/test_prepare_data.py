"""Prompt rendering, split integrity, and completion masking."""

import json
from pathlib import Path

from prepare_data import (
    DEFAULT_SPLITS,
    build_completion,
    build_user_prompt,
    collate_tokenized,
    prepare,
    prompt_text,
    render_qwen_chat,
    tokenize_example,
    texts_for_example,
)


def _example(with_label=True) -> dict:
    example = {
        "id": "ex-1",
        "user_profile": {
            "pet_species": ["dog"],
            "pet_ages": ["senior"],
            "pet_sizes": ["large"],
            "budget_tier": "mid",
            "preferred_categories": ["Health"],
            "health_interests": [],
        },
        "interaction_history": [],
        "candidate_product": {
            "product_id": "PROD_0204",
            "name": "Glucosamine Chews Large",
            "category": "Health",
            "species": "dog",
            "price_eur": 24.9,
            "price_tier": "mid",
            "description": "Daily chew for senior dogs.",
            "tags": ["joint"],
            "age_target": "senior",
            "size_target": "large",
            "health_benefits": ["joint-support"],
            "avg_rating": 4.6,
            "review_count": 12,
        },
    }
    if with_label:
        example["label"] = {
            "relevance_score": 0.82,
            "relevance_class": "highly_relevant",
            "justification": "JUSTIFICATION_MARKER species match for a senior dog.",
        }
    return example


class _CharTokenizer:
    """One character per token, with the Qwen chat markers applied in text."""

    def apply_chat_template(self, messages, tokenize=False, add_generation_prompt=False):
        assert tokenize is False
        return render_qwen_chat(messages, add_generation_prompt=add_generation_prompt)

    def __call__(self, text, add_special_tokens=False, truncation=True, max_length=None,
                 return_offsets_mapping=True, padding=False):
        assert add_special_tokens is False
        limit = len(text) if max_length is None else min(len(text), max_length)
        piece = text[:limit]
        return {
            "input_ids": [ord(char) for char in piece],
            "offset_mapping": [(index, index + 1) for index in range(len(piece))],
        }


def test_prompt_follows_the_schema_and_hides_the_label():
    example = _example()
    prompt = build_user_prompt(example)
    assert "### User Profile" in prompt
    assert "### Interaction History" in prompt
    assert "### Candidate Product" in prompt
    assert "### Task" in prompt
    assert "### Response" in prompt
    assert "(no prior interactions)" in prompt
    assert "(none)" in prompt
    assert "PROD_0204" in prompt
    assert "Size target: large" in prompt
    assert "JUSTIFICATION_MARKER" not in prompt
    completion = build_completion(example)
    assert completion.startswith("Relevance score: 0.82")
    assert "JUSTIFICATION_MARKER" in completion
    assert completion not in prompt


def test_prompt_does_not_require_a_label():
    text = prompt_text(_example(with_label=False), tokenizer=None)
    assert "<|im_start|>system\n" in text
    assert text.endswith("<|im_start|>assistant\n")
    assert "JUSTIFICATION_MARKER" not in text


def test_chat_template_keeps_the_prompt_as_a_prefix():
    prompt, full = texts_for_example(_example(), tokenizer=None)
    assert full.startswith(prompt)
    assert "Relevance score: 0.82" in full[len(prompt):]


def test_completion_tokens_are_trained_and_prompt_tokens_are_masked():
    example = _example()
    tokenizer = _CharTokenizer()
    prompt, _full = texts_for_example(example, tokenizer)
    row = tokenize_example(tokenizer, example, max_length=10000)
    assert row is not None
    assert all(label == -100 for label in row["labels"][: len(prompt)])
    assert row["labels"][len(prompt)] != -100
    assert any(label != -100 for label in row["labels"][len(prompt):])


def test_truncated_completion_is_dropped():
    assert tokenize_example(_CharTokenizer(), _example(), max_length=8) is None


def test_collate_pads_labels_with_ignore_index():
    batch = collate_tokenized(
        [
            {"input_ids": [1, 2], "attention_mask": [1, 1], "labels": [-100, 2]},
            {"input_ids": [3], "attention_mask": [1], "labels": [3]},
        ],
        pad_token_id=0,
    )
    assert batch["input_ids"][1] == [3, 0]
    assert batch["attention_mask"][1] == [1, 0]
    assert batch["labels"][1] == [3, -100]


def test_real_splits_match_the_seeded_summary_and_do_not_leak():
    splits, summary = prepare(DEFAULT_SPLITS, expected_seed=42)
    assert summary["seed"] == 42
    assert set(splits) == {"train", "validation", "test"}
    assert len(splits["train"]) == summary["train"]
    assert len(splits["validation"]) == summary["validation"]
    assert len(splits["test"]) == summary["test"]
    assert summary["train"] > summary["validation"] > 0
    ids = [{row["id"] for row in rows} for rows in splits.values()]
    assert ids[0].isdisjoint(ids[1])
    assert ids[0].isdisjoint(ids[2])
    assert ids[1].isdisjoint(ids[2])
    sample = splits["train"][0]
    user_prompt = build_user_prompt(sample)
    assert sample["label"]["justification"] not in user_prompt
    report_path = Path(DEFAULT_SPLITS) / "split_summary.json"
    assert json.loads(report_path.read_text(encoding="utf-8"))["seed"] == 42
