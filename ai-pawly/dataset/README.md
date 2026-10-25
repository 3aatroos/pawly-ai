# Pawly Recommendation Dataset

Dataset for fine-tuning a recommendation model with QLoRA.

## Quick start

```bash
# 1. Install dependencies
pip install pytest

# 2. Run all tests first
cd ai-pawly/dataset
python -m pytest tests/ -v

# 3. Run the full pipeline (generates 2000 examples)
python run_pipeline.py

# 4. Inspect output
cat splits/reports/quality_report.json
```

## Pipeline

```
generate_synthetic.py   →  raw/synthetic.jsonl      (2000 examples)
clean_and_anonymise.py  →  processed/clean.jsonl    (cleaned + validated)
split_dataset.py        →  splits/{train,validation,test}.jsonl
quality_check.py        →  splits/reports/quality_report.json
```

## JSONL schema

See `schema.md` for the full field specification.

## Prompt template

```
### User Profile
Pets: {species}, ages: {ages}, sizes: {sizes}
Budget: {budget_tier}
Preferred categories: {categories}
Health interests: {health_interests}

### Interaction History
{history}

### Candidate Product
Name: {name} | Category: {category} | Species: {species}
Price: {price} EUR ({tier}) | Age target: {age} | Size: {size}
Tags: {tags} | Health benefits: {benefits}
Rating: {rating}/5 ({review_count} reviews)
Description: {description}

### Task
Score this product's relevance for this user (0.0 to 1.0) and provide a short justification.

### Response
Relevance score: {score}
Justification: {justification}
```

## Splits

| Split      | Ratio | Purpose |
|------------|-------|---------|
| train      | 70%   | Fine-tuning |
| validation | 15%   | Hyperparameter tuning, early stopping |
| test       | 15%   | Final evaluation (touch once) |

Stratification: `relevance_class × target_species`

## Relevance classes

| Class           | Score range | Examples % |
|-----------------|-------------|------------|
| not_relevant    | 0.00–0.25   | ~25% |
| somewhat        | 0.26–0.50   | ~25% |
| relevant        | 0.51–0.75   | ~25% |
| highly_relevant | 0.76–1.00   | ~25% |

## Anonymisation rules

1. No real user names, emails, phone numbers, or IDs in any field.
2. All product IDs are synthetic (`PROD_XXXX`).
3. PII patterns are detected and replaced with `[NAME]`, `[EMAIL]`, `[PHONE]`, `[IP]` placeholders.
4. User profile fields contain only categorical values — no free text.
5. Justifications contain product attributes only, not user-specific information.

## Data leak prevention

- Exact deduplication by `(user_profile, candidate_product_id)` SHA-256 fingerprint.
- Cross-split contamination check: same fingerprint must NOT appear in both train and test.
- `quality_check.py` raises a non-zero exit code if critical leaks are detected.
