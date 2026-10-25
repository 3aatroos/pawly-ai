"""
metrics.py
==========
Parse generated recommendation answers and score them against the gold labels.

Class boundaries follow generate_synthetic.compute_label:
    <= 0.25 not_relevant
    <= 0.50 somewhat
    <= 0.75 relevant
    else    highly_relevant
"""

from __future__ import annotations

import math
import re
from dataclasses import dataclass


RELEVANCE_CLASSES = ("not_relevant", "somewhat", "relevant", "highly_relevant")

_SCORE_RE = re.compile(r"Relevance score:\s*([+-]?\d+(?:\.\d+)?)", re.IGNORECASE)
_JUST_RE = re.compile(r"Justification:\s*(.+)", re.IGNORECASE | re.DOTALL)


def score_to_class(score: float) -> str:
    if score <= 0.25:
        return "not_relevant"
    if score <= 0.50:
        return "somewhat"
    if score <= 0.75:
        return "relevant"
    return "highly_relevant"


@dataclass
class ParsedResponse:
    score: float | None
    justification: str
    clamped: bool
    raw_score: float | None


def parse_model_response(text: str | None) -> ParsedResponse:
    if not text:
        return ParsedResponse(None, "", False, None)
    score_match = _SCORE_RE.search(text)
    just_match = _JUST_RE.search(text)
    justification = ""
    if just_match:
        justification = just_match.group(1).strip()
        justification = re.split(r"\n### |\n<\|", justification)[0].strip()
    if not score_match:
        return ParsedResponse(None, justification, False, None)
    raw = float(score_match.group(1))
    clamped = raw < 0.0 or raw > 1.0
    score = min(1.0, max(0.0, raw))
    return ParsedResponse(score, justification, clamped, raw)


def _pearson(xs: list[float], ys: list[float]) -> float | None:
    count = len(xs)
    if count < 2 or count != len(ys):
        return None
    mean_x = sum(xs) / count
    mean_y = sum(ys) / count
    numerator = sum((x - mean_x) * (y - mean_y) for x, y in zip(xs, ys))
    denom_x = math.sqrt(sum((x - mean_x) ** 2 for x in xs))
    denom_y = math.sqrt(sum((y - mean_y) ** 2 for y in ys))
    if denom_x == 0.0 or denom_y == 0.0:
        return None
    return numerator / (denom_x * denom_y)


def _class_scores(golds: list[str], preds: list[str]) -> dict:
    per_class = {}
    f1_values = []
    for name in RELEVANCE_CLASSES:
        tp = sum(1 for gold, pred in zip(golds, preds) if gold == name and pred == name)
        fp = sum(1 for gold, pred in zip(golds, preds) if gold != name and pred == name)
        fn = sum(1 for gold, pred in zip(golds, preds) if gold == name and pred != name)
        support = tp + fn
        precision = tp / (tp + fp) if (tp + fp) else 0.0
        recall = tp / support if support else 0.0
        f1 = (2 * precision * recall / (precision + recall)) if (precision + recall) else 0.0
        per_class[name] = {
            "support": support,
            "precision": precision,
            "recall": recall,
            "f1": f1,
        }
        if support:
            f1_values.append(f1)
    macro = sum(f1_values) / len(f1_values) if f1_values else 0.0
    return {"per_class": per_class, "macro_f1": macro}


def _round(value: float | None, digits: int = 6) -> float | None:
    if value is None:
        return None
    return round(float(value), digits)


def evaluate_predictions(rows: list[dict]) -> dict:
    """
    Each row needs gold_score and either prediction text or pred_score.
    gold_class is derived from gold_score when omitted.
    Unparsed generations count toward parse_success_rate and strict accuracy,
    and they are excluded from MAE / RMSE / Pearson.
    """
    parsed_gold: list[float] = []
    parsed_pred: list[float] = []
    parsed_gold_class: list[str] = []
    parsed_pred_class: list[str] = []
    strict_correct = 0
    unparsed = 0
    clamped = 0
    justification_words = []

    for row in rows:
        gold_score = float(row["gold_score"])
        gold_class = row.get("gold_class") or score_to_class(gold_score)
        if "pred_score" in row and row.get("prediction") is None:
            parsed = ParsedResponse(
                None if row["pred_score"] is None else float(row["pred_score"]),
                row.get("justification") or "",
                bool(row.get("clamped", False)),
                row.get("raw_score"),
            )
        else:
            parsed = parse_model_response(row.get("prediction") or "")
        if parsed.score is None:
            unparsed += 1
            continue
        if parsed.clamped:
            clamped += 1
        pred_class = score_to_class(parsed.score)
        parsed_gold.append(gold_score)
        parsed_pred.append(parsed.score)
        parsed_gold_class.append(gold_class)
        parsed_pred_class.append(pred_class)
        if pred_class == gold_class:
            strict_correct += 1
        justification_words.append(len(parsed.justification.split()))

    total = len(rows)
    n_parsed = len(parsed_gold)
    errors = [abs(pred - gold) for gold, pred in zip(parsed_gold, parsed_pred)]
    mae = sum(errors) / n_parsed if n_parsed else None
    rmse = math.sqrt(sum(err ** 2 for err in errors) / n_parsed) if n_parsed else None
    class_report = _class_scores(parsed_gold_class, parsed_pred_class) if n_parsed else {
        "per_class": {},
        "macro_f1": 0.0,
    }
    accuracy = (
        sum(gold == pred for gold, pred in zip(parsed_gold_class, parsed_pred_class)) / n_parsed
        if n_parsed
        else None
    )
    return {
        "n": total,
        "n_parsed": n_parsed,
        "n_unparsed": unparsed,
        "parse_success_rate": (n_parsed / total) if total else 0.0,
        "n_clamped": clamped,
        "mae": _round(mae),
        "rmse": _round(rmse),
        "pearson_r": _round(_pearson(parsed_gold, parsed_pred)),
        "class_accuracy": _round(accuracy),
        "strict_class_accuracy": _round(strict_correct / total) if total else None,
        "macro_f1": _round(class_report["macro_f1"]),
        "per_class": class_report["per_class"],
        "mean_justification_words": _round(
            (sum(justification_words) / len(justification_words)) if justification_words else None
        ),
    }
