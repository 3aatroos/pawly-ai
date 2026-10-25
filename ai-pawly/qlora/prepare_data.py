"""
prepare_data.py
===============
Load the stratified train / validation / test JSONL splits, check that the
dataset seed matches, reject cross-split leakage, and render the recommendation
prompt. The completion is kept out of the user turn so the loss can mask it.

Usage:
    python prepare_data.py
    python prepare_data.py --splits-dir ../dataset/splits --output-dir outputs/prepared
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path

from config import SEED, SYSTEM_PROMPT


SPLIT_NAMES = ("train", "validation", "test")
QLORA_DIR = Path(__file__).resolve().parent
DEFAULT_SPLITS = QLORA_DIR.parent / "dataset" / "splits"
DEFAULT_OUTPUT = QLORA_DIR / "outputs" / "prepared"


def load_jsonl(path: str | os.PathLike) -> list[dict]:
    examples: list[dict] = []
    with open(path, "r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                examples.append(json.loads(line))
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_number} is not valid JSON") from exc
    return examples


def load_splits(splits_dir: str | os.PathLike) -> dict[str, list[dict]]:
    root = Path(splits_dir)
    splits: dict[str, list[dict]] = {}
    for name in SPLIT_NAMES:
        path = root / f"{name}.jsonl"
        if not path.is_file():
            raise FileNotFoundError(
                f"Missing {path}. Run ai-pawly/dataset/run_pipeline.py first."
            )
        splits[name] = load_jsonl(path)
        if not splits[name]:
            raise ValueError(f"{path} is empty.")
    return splits


def load_split_summary(splits_dir: str | os.PathLike) -> dict:
    path = Path(splits_dir) / "split_summary.json"
    if not path.is_file():
        raise FileNotFoundError(f"Missing {path}. The dataset seed cannot be verified.")
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def example_fingerprint(example: dict) -> str:
    """Same identity the dataset pipeline uses to detect leakage."""
    payload = {
        "user_profile": example["user_profile"],
        "product_id": example["candidate_product"]["product_id"],
    }
    raw = json.dumps(payload, sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def assert_no_leakage(splits: dict[str, list[dict]]) -> None:
    ids = {name: [ex["id"] for ex in rows] for name, rows in splits.items()}
    fingerprints = {
        name: [example_fingerprint(ex) for ex in rows] for name, rows in splits.items()
    }
    for name in SPLIT_NAMES:
        if len(ids[name]) != len(set(ids[name])):
            raise ValueError(f"Duplicate ids inside the {name} split.")
    pairs = (("train", "validation"), ("train", "test"), ("validation", "test"))
    for left, right in pairs:
        shared_ids = set(ids[left]) & set(ids[right])
        if shared_ids:
            raise ValueError(f"Id leakage between {left} and {right}: {len(shared_ids)} ids.")
        shared_fp = set(fingerprints[left]) & set(fingerprints[right])
        if shared_fp:
            raise ValueError(
                f"Profile/product leakage between {left} and {right}: "
                f"{len(shared_fp)} fingerprints."
            )


def assert_dataset_seed(summary: dict, expected_seed: int = SEED) -> None:
    actual = summary.get("seed")
    if actual != expected_seed:
        raise ValueError(
            f"Dataset split seed is {actual}, expected {expected_seed}. "
            "Refusing to train on a split that is not reproducible with this config."
        )


def assert_summary_counts(splits: dict[str, list[dict]], summary: dict) -> None:
    for name in SPLIT_NAMES:
        expected = summary.get(name)
        actual = len(splits[name])
        if expected != actual:
            raise ValueError(
                f"split_summary.json says {name}={expected}, file has {actual} rows."
            )


def _join(values) -> str:
    if not values:
        return "(none)"
    return ", ".join(str(value) for value in values)


def format_history(history: list[dict]) -> str:
    if not history:
        return "(no prior interactions)"
    lines = []
    for item in history:
        lines.append(
            f"- {item['product_name']} ({item['product_id']}): "
            f"{item['signal']} (weight {item['weight']})"
        )
    return "\n".join(lines)


def validate_example(example: dict, require_label: bool = True) -> None:
    missing: list[str] = []
    profile = example.get("user_profile")
    product = example.get("candidate_product")
    history = example.get("interaction_history")
    if not isinstance(profile, dict):
        missing.append("user_profile")
        profile = {}
    if not isinstance(product, dict):
        missing.append("candidate_product")
        product = {}
    if not isinstance(history, list):
        missing.append("interaction_history")
    for key in (
        "pet_species",
        "pet_ages",
        "pet_sizes",
        "budget_tier",
        "preferred_categories",
        "health_interests",
    ):
        if key not in profile:
            missing.append(f"user_profile.{key}")
    for key in (
        "product_id",
        "name",
        "category",
        "species",
        "price_eur",
        "price_tier",
        "description",
        "tags",
        "age_target",
        "size_target",
        "health_benefits",
        "avg_rating",
        "review_count",
    ):
        if key not in product:
            missing.append(f"candidate_product.{key}")
    if require_label:
        label = example.get("label")
        if not isinstance(label, dict):
            missing.append("label")
        else:
            for key in ("relevance_score", "relevance_class", "justification"):
                if key not in label:
                    missing.append(f"label.{key}")
    if missing:
        example_id = example.get("id", "?")
        raise ValueError(f"Example {example_id} is missing: {', '.join(missing)}")


def build_user_prompt(example: dict) -> str:
    """Schema prompt. The gold justification is not included."""
    validate_example(example, require_label=False)
    profile = example["user_profile"]
    product = example["candidate_product"]
    return (
        "### User Profile\n"
        f"Pets: {_join(profile['pet_species'])}, ages: {_join(profile['pet_ages'])}, "
        f"sizes: {_join(profile['pet_sizes'])}\n"
        f"Budget: {profile['budget_tier']}\n"
        f"Preferred categories: {_join(profile['preferred_categories'])}\n"
        f"Health interests: {_join(profile['health_interests'])}\n"
        "\n"
        "### Interaction History\n"
        f"{format_history(example['interaction_history'])}\n"
        "\n"
        "### Candidate Product\n"
        f"Name: {product['name']} ({product['product_id']})\n"
        f"Category: {product['category']} | Species: {product['species']} | "
        f"Age target: {product['age_target']} | Size target: {product['size_target']}\n"
        f"Price: {float(product['price_eur']):.2f} EUR ({product['price_tier']})\n"
        f"Tags: {_join(product['tags'])}\n"
        f"Health benefits: {_join(product['health_benefits'])}\n"
        f"Rating: {float(product['avg_rating']):.1f}/5 ({product['review_count']} reviews)\n"
        f"Description: {product['description']}\n"
        "\n"
        "### Task\n"
        "Score this product's relevance for this user (0.0 to 1.0) and provide a short justification.\n"
        "\n"
        "### Response\n"
    )


def build_completion(example: dict) -> str:
    validate_example(example, require_label=True)
    label = example["label"]
    score = float(label["relevance_score"])
    justification = " ".join(str(label["justification"]).split())
    return f"Relevance score: {score:.2f}\nJustification: {justification}"


def build_messages(example: dict, include_completion: bool) -> list[dict]:
    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": build_user_prompt(example)},
    ]
    if include_completion:
        messages.append({"role": "assistant", "content": build_completion(example)})
    return messages


def render_qwen_chat(messages: list[dict], add_generation_prompt: bool) -> str:
    """
    Official Qwen2 chat markers for a system/user/assistant conversation.
    The installed tokenizer's apply_chat_template is preferred at train time;
    this renderer is the reference used when no tokenizer is loaded yet.
    """
    if not messages or messages[0].get("role") != "system":
        raise ValueError("The first message must be the system prompt.")
    parts = []
    for message in messages:
        role = message["role"]
        content = message["content"]
        parts.append(f"<|im_start|>{role}\n{content}<|im_end|>\n")
    if add_generation_prompt:
        parts.append("<|im_start|>assistant\n")
    return "".join(parts)


def _render_messages(messages: list[dict], tokenizer, add_generation_prompt: bool) -> str:
    if tokenizer is not None and hasattr(tokenizer, "apply_chat_template"):
        text = tokenizer.apply_chat_template(
            messages,
            tokenize=False,
            add_generation_prompt=add_generation_prompt,
        )
    else:
        text = render_qwen_chat(messages, add_generation_prompt=add_generation_prompt)
    if not isinstance(text, str):
        raise TypeError("Chat rendering must return a string.")
    return text


def prompt_text(example: dict, tokenizer=None) -> str:
    """User-visible prompt only. Safe to call on an example that has no label."""
    return _render_messages(
        build_messages(example, include_completion=False),
        tokenizer,
        add_generation_prompt=True,
    )


def texts_for_example(example: dict, tokenizer=None) -> tuple[str, str]:
    prompt = prompt_text(example, tokenizer)
    full = _render_messages(
        build_messages(example, include_completion=True),
        tokenizer,
        add_generation_prompt=False,
    )
    if not full.startswith(prompt):
        raise ValueError(
            "The chat template did not keep the prompt as a prefix, "
            "so completion tokens cannot be masked safely."
        )
    return prompt, full


def _as_list(value) -> list:
    if hasattr(value, "tolist"):
        value = value.tolist()
    return list(value)


def tokenize_example(tokenizer, example: dict, max_length: int) -> dict | None:
    """
    Tokenize one example and set prompt labels to -100.
    Returns None when truncation removes every completion token.
    """
    prompt, full = texts_for_example(example, tokenizer)
    encoded = tokenizer(
        full,
        add_special_tokens=False,
        truncation=True,
        max_length=max_length,
        return_offsets_mapping=True,
        padding=False,
    )
    input_ids = _as_list(encoded["input_ids"])
    offsets = _as_list(encoded["offset_mapping"])
    if len(input_ids) != len(offsets):
        raise ValueError("Tokenizer offset mapping does not match input ids.")
    prompt_chars = len(prompt)
    labels = []
    for token_id, (start, _end) in zip(input_ids, offsets):
        if start < prompt_chars:
            labels.append(-100)
        else:
            labels.append(int(token_id))
    if not any(label != -100 for label in labels):
        return None
    return {
        "input_ids": [int(token) for token in input_ids],
        "attention_mask": [1] * len(input_ids),
        "labels": labels,
    }


def tokenize_split(examples: list[dict], tokenizer, max_length: int) -> tuple[list[dict], int]:
    rows = []
    skipped = 0
    for example in examples:
        row = tokenize_example(tokenizer, example, max_length)
        if row is None:
            skipped += 1
            continue
        rows.append(row)
    if not rows:
        raise ValueError("Every example was truncated before the completion.")
    return rows, skipped


def collate_tokenized(features: list[dict], pad_token_id: int, label_pad: int = -100) -> dict:
    width = max(len(feature["input_ids"]) for feature in features)
    input_ids = []
    attention_mask = []
    labels = []
    for feature in features:
        pad = width - len(feature["input_ids"])
        input_ids.append(list(feature["input_ids"]) + [pad_token_id] * pad)
        attention_mask.append(list(feature["attention_mask"]) + [0] * pad)
        labels.append(list(feature["labels"]) + [label_pad] * pad)
    return {
        "input_ids": input_ids,
        "attention_mask": attention_mask,
        "labels": labels,
    }


def make_trainer_collator(pad_token_id: int):
    def collate(features: list[dict]) -> dict:
        import torch

        batch = collate_tokenized(features, pad_token_id)
        return {key: torch.tensor(value, dtype=torch.long) for key, value in batch.items()}

    return collate


def summarize_splits(splits: dict[str, list[dict]], summary: dict) -> dict:
    return {
        "seed": summary.get("seed"),
        "counts": {name: len(rows) for name, rows in splits.items()},
        "summary_counts": {name: summary.get(name) for name in SPLIT_NAMES},
        "stratification": summary.get("stratification"),
    }


def write_preview(splits: dict[str, list[dict]], output_dir: str | os.PathLike) -> Path:
    root = Path(output_dir)
    root.mkdir(parents=True, exist_ok=True)
    preview = root / "preview.jsonl"
    with open(preview, "w", encoding="utf-8") as handle:
        for name in SPLIT_NAMES:
            example = splits[name][0]
            prompt, full = texts_for_example(example, tokenizer=None)
            handle.write(
                json.dumps(
                    {
                        "id": example.get("id"),
                        "split": name,
                        "prompt": prompt,
                        "completion": build_completion(example),
                        "full_text": full,
                    },
                    ensure_ascii=False,
                )
                + "\n"
            )
    return preview


def prepare(splits_dir: str | os.PathLike, expected_seed: int = SEED) -> tuple[dict, dict]:
    splits = load_splits(splits_dir)
    summary = load_split_summary(splits_dir)
    assert_dataset_seed(summary, expected_seed)
    assert_summary_counts(splits, summary)
    assert_no_leakage(splits)
    return splits, summary


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate Pawly QLoRA splits and preview prompts.")
    parser.add_argument("--splits-dir", default=str(DEFAULT_SPLITS))
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT))
    parser.add_argument("--seed", type=int, default=SEED)
    args = parser.parse_args()

    splits, summary = prepare(args.splits_dir, args.seed)
    report = summarize_splits(splits, summary)
    preview = write_preview(splits, args.output_dir)
    report_path = Path(args.output_dir) / "prepare_report.json"
    report["preview"] = str(preview)
    with open(report_path, "w", encoding="utf-8") as handle:
        json.dump(report, handle, indent=2)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
