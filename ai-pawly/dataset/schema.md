# Dataset Schema — Pawly Recommendation Fine-Tuning

## Format

One JSON object per line (JSONL). Each example is self-contained.

## Fields

| Field                        | Type    | Required | Description |
|------------------------------|---------|----------|-------------|
| `id`                         | string  | ✓        | Unique example ID (UUID4) |
| `split`                      | string  | ✓        | "train" / "validation" / "test" |
| `user_profile`               | object  | ✓        | Anonymised user context |
| `user_profile.pet_species`   | list    | ✓        | ["dog"], ["cat","rabbit"], etc. |
| `user_profile.pet_ages`      | list    | ✓        | ["puppy"], ["adult","senior"] |
| `user_profile.pet_sizes`     | list    | ✓        | ["small"], ["large"] |
| `user_profile.budget_tier`   | string  | ✓        | "budget" / "mid" / "premium" |
| `user_profile.preferred_categories` | list | ✓  | ["Food","Toys"] |
| `user_profile.health_interests` | list | ✓      | ["joint","digestive","hypoallergenic"] |
| `interaction_history`        | list    | ✓        | Last ≤10 interactions |
| `interaction_history[].product_id`  | string | ✓ | Synthetic ID ("PROD_042") |
| `interaction_history[].product_name`| string | ✓ | Product name |
| `interaction_history[].signal`      | string | ✓ | "view"/"cart"/"purchase"/"review_pos"/"review_neg" |
| `interaction_history[].weight`      | float  | ✓ | Signal weight (0.3/0.7/1.0/0.9/-0.5) |
| `candidate_product`          | object  | ✓        | Product to score |
| `candidate_product.product_id`      | string | ✓ | Synthetic ID |
| `candidate_product.name`    | string  | ✓        | Product name |
| `candidate_product.category`| string  | ✓        | Category |
| `candidate_product.species` | string  | ✓        | Target species or "all" |
| `candidate_product.price_eur`| float  | ✓        | Price in EUR |
| `candidate_product.price_tier`| string| ✓        | "budget"/"mid"/"premium" |
| `candidate_product.description`| string| ✓       | Short description |
| `candidate_product.tags`    | list    | ✓        | ["grain-free","senior","joint"] |
| `candidate_product.age_target`| string| ✓        | "puppy"/"adult"/"senior"/"all" |
| `candidate_product.size_target`| string| ✓       | "small"/"medium"/"large"/"all" |
| `candidate_product.health_benefits`| list| ✓    | ["joint-support","digestive"] |
| `candidate_product.avg_rating`| float | ✓        | 0.0–5.0 |
| `candidate_product.review_count`| int | ✓        | ≥ 0 |
| `label`                      | object  | ✓        | Ground truth |
| `label.relevance_score`      | float   | ✓        | 0.0–1.0 (continuous) |
| `label.relevance_class`      | string  | ✓        | "not_relevant"/"somewhat"/"relevant"/"highly_relevant" |
| `label.justification`        | string  | ✓        | ≤80 words, English |
| `label.rank_in_session`      | int     | ✗        | Position in actual purchase session (if known) |
| `meta`                       | object  | ✓        | Dataset metadata |
| `meta.source`                | string  | ✓        | "synthetic"/"derived" |
| `meta.generation_rule`       | string  | ✓        | Rule that generated the label |
| `meta.created_at`            | string  | ✓        | ISO 8601 UTC |

## Relevance classes

| Class            | Score range | Meaning |
|------------------|-------------|---------|
| `not_relevant`   | 0.00–0.25   | Species mismatch, wrong budget, opposing health needs |
| `somewhat`       | 0.26–0.50   | Partial match (right species, wrong age or size) |
| `relevant`       | 0.51–0.75   | Good match on main dimensions |
| `highly_relevant`| 0.76–1.00   | Exact match on species, age, health, budget + prior interaction |

## Prompt template (used during fine-tuning)

```
### User Profile
Pets: {pet_species}, ages: {pet_ages}, sizes: {pet_sizes}
Budget: {budget_tier}
Preferred categories: {preferred_categories}
Health interests: {health_interests}

### Interaction History
{interaction_history_text}

### Candidate Product
Name: {name}
Category: {category} | Species: {species} | Age target: {age_target}
Price: {price_eur} EUR ({price_tier})
Tags: {tags}
Health benefits: {health_benefits}
Rating: {avg_rating}/5 ({review_count} reviews)
Description: {description}

### Task
Score this product's relevance for this user (0.0 to 1.0) and provide a short justification.

### Response
Relevance score: {relevance_score}
Justification: {justification}
```
