"""Recommendation metrics and response parsing."""

import pytest

from metrics import evaluate_predictions, parse_model_response, score_to_class


def test_score_bands_follow_the_dataset_generator():
    assert score_to_class(0.0) == "not_relevant"
    assert score_to_class(0.25) == "not_relevant"
    assert score_to_class(0.2501) == "somewhat"
    assert score_to_class(0.50) == "somewhat"
    assert score_to_class(0.51) == "relevant"
    assert score_to_class(0.75) == "relevant"
    assert score_to_class(0.7501) == "highly_relevant"
    assert score_to_class(1.0) == "highly_relevant"


def test_parser_clamps_and_stops_the_justification():
    parsed = parse_model_response(
        "Note.\nRelevance score: 1.40\nJustification: Good match.\n### Extra\nignored"
    )
    assert parsed.raw_score == 1.4
    assert parsed.clamped is True
    assert parsed.score == 1.0
    assert parsed.justification == "Good match."
    missing = parse_model_response("I am not sure.")
    assert missing.score is None


def test_regression_and_class_metrics():
    rows = [
        {
            "gold_score": 0.20,
            "prediction": "Relevance score: 0.40\nJustification: Partial.",
        },
        {
            "gold_score": 0.80,
            "prediction": "Relevance score: 0.60\nJustification: Close.",
        },
        {
            "gold_score": 0.10,
            "prediction": "no score here",
        },
    ]
    report = evaluate_predictions(rows)
    assert report["n"] == 3
    assert report["n_parsed"] == 2
    assert report["n_unparsed"] == 1
    assert report["parse_success_rate"] == pytest.approx(2 / 3)
    assert report["mae"] == pytest.approx(0.2)
    assert report["rmse"] == pytest.approx(0.2)
    assert report["class_accuracy"] == pytest.approx(0.0)
    assert report["strict_class_accuracy"] == pytest.approx(0.0)


def test_perfect_class_predictions_score_one():
    pairs = [
        (0.10, "not_relevant"),
        (0.40, "somewhat"),
        (0.60, "relevant"),
        (0.90, "highly_relevant"),
    ]
    rows = [
        {
            "gold_score": score,
            "gold_class": name,
            "prediction": f"Relevance score: {score:.2f}\nJustification: Exact.",
        }
        for score, name in pairs
    ]
    report = evaluate_predictions(rows)
    assert report["mae"] == pytest.approx(0.0)
    assert report["pearson_r"] == pytest.approx(1.0)
    assert report["class_accuracy"] == pytest.approx(1.0)
    assert report["macro_f1"] == pytest.approx(1.0)
    assert report["per_class"]["relevant"]["support"] == 1


def test_constant_predictions_have_no_pearson():
    rows = [
        {"gold_score": 0.2, "prediction": "Relevance score: 0.50\nJustification: Same."},
        {"gold_score": 0.8, "prediction": "Relevance score: 0.50\nJustification: Same."},
    ]
    assert evaluate_predictions(rows)["pearson_r"] is None
