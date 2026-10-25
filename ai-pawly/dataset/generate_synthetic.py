"""
generate_synthetic.py
=====================
Generates a fully synthetic JSONL dataset for Pawly recommendation fine-tuning.

Usage:
    python generate_synthetic.py --output raw/synthetic.jsonl --count 2000 --seed 42
"""

import argparse
import json
import random
import uuid
from datetime import datetime, timezone
from itertools import product as iterproduct
from typing import Any


# ── Vocabulary ────────────────────────────────────────────────────────────────

SPECIES     = ["dog", "cat", "rabbit", "bird", "hamster"]
AGE_TARGETS = ["puppy", "adult", "senior", "all"]
SIZES       = ["small", "medium", "large", "all"]
BUDGETS     = ["budget", "mid", "premium"]

CATEGORIES = {
    "Food":        {"species": ["dog", "cat", "rabbit", "bird", "hamster", "all"],
                    "tags_pool": ["grain-free","kibble","wet-food","raw","organic","high-protein",
                                  "digestive","hypoallergenic","urinary","weight-control"]},
    "Toys":        {"species": ["dog", "cat", "rabbit", "bird", "hamster", "all"],
                    "tags_pool": ["interactive","chew","puzzle","squeaky","feather","plush",
                                  "rope","laser","tunnel","ball"]},
    "Accessories": {"species": ["dog", "cat", "all"],
                    "tags_pool": ["collar","harness","leash","carrier","bed","clothing",
                                  "reflective","adjustable","lightweight"]},
    "Hygiene":     {"species": ["dog", "cat", "all"],
                    "tags_pool": ["shampoo","grooming","dental","nail","brush","deodorisant",
                                  "moisturising","shedding","wipes","ear-care"]},
    "Health":      {"species": ["dog", "cat", "rabbit", "all"],
                    "tags_pool": ["joint","omega3","vitamin","probiotic","calming","senior",
                                  "immune","coat","anti-inflammatory","supplement"]},
    "Bedding":     {"species": ["dog", "cat", "rabbit", "hamster", "all"],
                    "tags_pool": ["orthopedic","washable","anti-slip","memory-foam",
                                  "fleece","waterproof","nest","cave"]},
}

HEALTH_BENEFITS_POOL = [
    "joint-support", "digestive-health", "dental-care", "coat-shine",
    "immune-boost", "weight-management", "calming", "kidney-support",
    "urinary-health", "anti-inflammatory", "hypoallergenic", "mobility",
    "muscle-development", "eye-health", "skin-health"
]

PRODUCT_NAMES = {
    "Food":        [("Royal Canin {age} {species}", 18.50),
                    ("Hill's Science {age} {species}", 22.00),
                    ("Purina Pro Plan {species} {size}", 28.00),
                    ("Acana {species} {age}", 45.00),
                    ("Generic Kibble {species}", 8.00)],
    "Toys":        [("Kong Classic {size}", 12.00),
                    ("Interactive Puzzle {level}", 19.99),
                    ("Rope Chew {size}", 6.50),
                    ("Feather Wand Deluxe", 9.99),
                    ("Ball Launcher {size}", 14.00)],
    "Accessories": [("Leather Harness {size}", 24.99),
                    ("Reflective Collar {size}", 11.00),
                    ("Soft Carrier {size}", 35.00),
                    ("Memory Foam Bed {size}", 42.00),
                    ("Adjustable Lead {size}", 8.99)],
    "Hygiene":     [("Pro Groom Shampoo {coat}", 14.00),
                    ("Dental Stick Pack {size}", 9.50),
                    ("Shedding Brush {size}", 17.00),
                    ("Ear Wipes 30pk", 7.99),
                    ("Whitening Toothpaste Kit", 12.50)],
    "Health":      [("JointFlex Senior {species}", 24.99),
                    ("Omega-3 Oil 250ml", 18.00),
                    ("Calming Spray 100ml", 16.50),
                    ("Probiotic Powder 60g", 21.00),
                    ("Multivitamin Tabs 60ct {species}", 19.99)],
    "Bedding":     [("Ortho Mattress {size}", 49.00),
                    ("Fleece Nest {size}", 22.00),
                    ("Waterproof Cover {size}", 15.99),
                    ("Cave Bed {size}", 31.00),
                    ("Memory Foam Crate Pad {size}", 38.00)],
}

DESCRIPTIONS = {
    "Food":
        "Nutritionally complete recipe formulated for {age} {species}. "
        "Supports {benefit1} and {benefit2}. Vet-approved formula with premium ingredients.",
    "Toys":
        "Stimulating toy designed to keep your {species} engaged for hours. "
        "Durable construction, safe materials, suitable for {size} breeds.",
    "Accessories":
        "Premium quality {category} crafted for comfort and durability. "
        "Adjustable fit for {size} breeds. Reflective stitching for night safety.",
    "Hygiene":
        "Gentle formula that cleans and conditions your {species}'s coat and skin. "
        "Free from harsh chemicals. Suitable for sensitive skin.",
    "Health":
        "Veterinarian-recommended supplement to support {benefit1} in {age} {species}. "
        "Easy-to-administer, highly palatable formula.",
    "Bedding":
        "Ergonomic design with {material} filling for maximum comfort. "
        "Machine washable cover. Perfect for {size} breeds.",
}


# ── Signal weights ────────────────────────────────────────────────────────────

SIGNAL_WEIGHTS = {
    "view":         0.3,
    "cart":         0.7,
    "purchase":     1.0,
    "review_pos":   0.9,
    "review_neg":  -0.5,
}

POSITIVE_SIGNALS = {"view", "cart", "purchase", "review_pos"}
NEGATIVE_SIGNALS = {"review_neg"}


# ── Synthetic product factory ─────────────────────────────────────────────────

def _price_tier(price: float) -> str:
    if price < 15:  return "budget"
    if price < 40:  return "mid"
    return "premium"


def _render_name(template: str, species: str, age: str, size: str) -> str:
    replmap = {
        "{species}": species.capitalize(),
        "{age}":     age.capitalize(),
        "{size}":    size.capitalize(),
        "{level}":   random.choice(["Basic", "Advanced", "Expert"]),
        "{coat}":    random.choice(["Short", "Long", "Curly"]),
        "{material}":random.choice(["foam", "wool", "cotton"]),
    }
    for k, v in replmap.items():
        template = template.replace(k, v)
    return template


def make_product(
    category: str,
    species:  str,
    age:      str,
    size:     str,
    rng:      random.Random,
    product_idx: int
) -> dict:
    name_tpl, base_price = rng.choice(PRODUCT_NAMES[category])
    price      = round(base_price * rng.uniform(0.8, 1.2), 2)
    cat_data   = CATEGORIES[category]
    tags       = rng.sample(cat_data["tags_pool"], k=min(4, len(cat_data["tags_pool"])))
    benefits   = rng.sample(HEALTH_BENEFITS_POOL, k=rng.randint(1, 3))
    avg_rating = round(rng.uniform(2.5, 5.0), 1)
    rev_count  = int(rng.lognormvariate(3, 1))

    desc_tpl = DESCRIPTIONS.get(category, "A quality product for your {species}.")
    description = desc_tpl.format(
        species  = species,
        age      = age,
        size     = size,
        category = category.lower(),
        benefit1 = benefits[0] if benefits else "health",
        benefit2 = benefits[1] if len(benefits) > 1 else "wellbeing",
        material = rng.choice(["memory foam", "orthopedic foam", "fleece"]),
    )

    return {
        "product_id":     f"PROD_{product_idx:04d}",
        "name":           _render_name(name_tpl, species, age, size),
        "category":       category,
        "species":        species if rng.random() > 0.15 else "all",
        "price_eur":      price,
        "price_tier":     _price_tier(price),
        "description":    description,
        "tags":           tags,
        "age_target":     age if rng.random() > 0.20 else "all",
        "size_target":    size if rng.random() > 0.25 else "all",
        "health_benefits": benefits,
        "avg_rating":     avg_rating,
        "review_count":   rev_count,
    }


# ── User profile factory ──────────────────────────────────────────────────────

def make_user_profile(rng: random.Random) -> dict:
    n_pets    = rng.randint(1, 3)
    species   = rng.sample(SPECIES, k=n_pets)
    ages      = [rng.choice(AGE_TARGETS) for _ in range(n_pets)]
    sizes     = [rng.choice(["small", "medium", "large"]) for _ in range(n_pets)]
    budget    = rng.choice(BUDGETS)
    n_cats    = rng.randint(1, 3)
    cats      = rng.sample(list(CATEGORIES.keys()), k=n_cats)
    n_health  = rng.randint(0, 3)
    health    = rng.sample(HEALTH_BENEFITS_POOL, k=n_health)

    return {
        "pet_species":           list(set(species)),
        "pet_ages":              list(set(ages)),
        "pet_sizes":             list(set(sizes)),
        "budget_tier":           budget,
        "preferred_categories":  cats,
        "health_interests":      health,
    }


def make_interactions(user_profile: dict, products: list[dict], rng: random.Random) -> list[dict]:
    """Generate 1–10 past interactions consistent with the user profile."""
    n = rng.randint(1, 10)
    # Bias towards products matching user species
    user_species = set(user_profile["pet_species"])

    compatible = [
        p for p in products
        if p["species"] in user_species or p["species"] == "all"
    ]
    all_candidates = compatible if len(compatible) >= n else products

    chosen_products = rng.sample(all_candidates, k=min(n, len(all_candidates)))
    interactions    = []

    for p in chosen_products:
        signal = rng.choices(
            list(SIGNAL_WEIGHTS.keys()),
            weights=[0.45, 0.25, 0.15, 0.10, 0.05],
            k=1
        )[0]
        interactions.append({
            "product_id":   p["product_id"],
            "product_name": p["name"],
            "signal":       signal,
            "weight":       SIGNAL_WEIGHTS[signal],
        })

    return interactions


# ── Label computation ─────────────────────────────────────────────────────────

def compute_label(
    user:       dict,
    product:    dict,
    history:    list[dict],
    rng:        random.Random,
) -> dict:
    """
    Deterministic heuristic rules → relevance score.
    Rules are transparent and auditable.
    """
    score = 0.0
    rules_fired = []

    # R1 — species match (most important signal, weight 0.35)
    user_species = set(user["pet_species"])
    prod_species = product["species"]
    if prod_species == "all" or prod_species in user_species:
        score += 0.35
        rules_fired.append("species_match")
    else:
        score -= 0.40
        rules_fired.append("species_mismatch")

    # R2 — budget match (weight 0.20)
    if product["price_tier"] == user["budget_tier"]:
        score += 0.20
        rules_fired.append("budget_match")
    elif abs(["budget","mid","premium"].index(product["price_tier"])
             - ["budget","mid","premium"].index(user["budget_tier"])) == 1:
        score += 0.08
        rules_fired.append("budget_adjacent")
    else:
        score -= 0.10
        rules_fired.append("budget_mismatch")

    # R3 — category match (weight 0.15)
    if product["category"] in user["preferred_categories"]:
        score += 0.15
        rules_fired.append("category_match")

    # R4 — health interests (weight 0.15)
    user_health  = set(user["health_interests"])
    prod_health  = set(product["health_benefits"])
    overlap      = len(user_health & prod_health)
    if overlap > 0:
        score += min(0.15, 0.07 * overlap)
        rules_fired.append(f"health_overlap_{overlap}")

    # R5 — age target match (weight 0.10)
    user_ages  = set(user["pet_ages"])
    if product["age_target"] == "all" or product["age_target"] in user_ages:
        score += 0.10
        rules_fired.append("age_match")

    # R6 — positive interaction history (weight 0.10)
    history_ids = {i["product_id"] for i in history if i["signal"] in POSITIVE_SIGNALS}
    if product["product_id"] in history_ids:
        score += 0.10
        rules_fired.append("prior_positive_interaction")

    # R7 — negative interaction history (weight -0.20)
    negative_ids = {i["product_id"] for i in history if i["signal"] in NEGATIVE_SIGNALS}
    if product["product_id"] in negative_ids:
        score -= 0.20
        rules_fired.append("prior_negative_interaction")

    # R8 — rating quality signal (weight up to 0.05)
    if product["avg_rating"] >= 4.5:
        score += 0.05
        rules_fired.append("high_rating")
    elif product["avg_rating"] < 3.0:
        score -= 0.05
        rules_fired.append("low_rating")

    # Clamp to [0, 1]
    score = max(0.0, min(1.0, round(score, 4)))

    # Discretise
    if score <= 0.25:
        rel_class = "not_relevant"
    elif score <= 0.50:
        rel_class = "somewhat"
    elif score <= 0.75:
        rel_class = "relevant"
    else:
        rel_class = "highly_relevant"

    justification = _build_justification(user, product, rules_fired, score)

    return {
        "relevance_score":  score,
        "relevance_class":  rel_class,
        "justification":    justification,
    }


def _build_justification(user: dict, product: dict, rules: list[str], score: float) -> str:
    parts = []

    if "species_mismatch" in rules:
        parts.append(
            f"This product targets {product['species']}s, "
            f"but user has {', '.join(user['pet_species'])}."
        )
    elif "species_match" in rules:
        parts.append(
            f"Species match ({product['species']})."
        )

    if "budget_mismatch" in rules:
        parts.append(
            f"Price tier ({product['price_tier']}) does not match "
            f"user budget ({user['budget_tier']})."
        )
    elif "budget_match" in rules:
        parts.append(f"Price fits the {user['budget_tier']} budget.")

    if "category_match" in rules:
        parts.append(f"User prefers {product['category']} products.")

    for r in rules:
        if r.startswith("health_overlap"):
            n = int(r.split("_")[-1])
            shared = list(
                set(user["health_interests"]) & set(product["health_benefits"])
            )
            parts.append(
                f"Matches {n} health interest(s): {', '.join(shared[:2])}."
            )
            break

    if "age_match" in rules and product["age_target"] != "all":
        parts.append(f"Suitable for {product['age_target']} pets.")

    if "prior_positive_interaction" in rules:
        parts.append("User has already engaged positively with this product.")

    if "prior_negative_interaction" in rules:
        parts.append("User previously left a negative review.")

    if "high_rating" in rules:
        parts.append(f"Highly rated ({product['avg_rating']}/5).")

    # Ensure max 80 words
    text = " ".join(parts)
    words = text.split()
    if len(words) > 80:
        text = " ".join(words[:80]) + "…"

    return text or f"Relevance score {score:.2f} based on user profile analysis."


# ── Example builder ───────────────────────────────────────────────────────────

def build_example(
    user:       dict,
    candidate:  dict,
    history:    list[dict],
    rng:        random.Random,
    split:      str,
    product_idx_offset: int,
) -> dict:
    label = compute_label(user, candidate, history, rng)

    return {
        "id":                  str(uuid.uuid4()),
        "split":               split,
        "user_profile":        user,
        "interaction_history": history,
        "candidate_product":   candidate,
        "label":               label,
        "meta": {
            "source":           "synthetic",
            "generation_rule":  "heuristic_v1",
            "created_at":       datetime.now(timezone.utc).isoformat(),
        }
    }


# ── Main ──────────────────────────────────────────────────────────────────────

def generate(count: int, seed: int, output: str) -> None:
    rng     = random.Random(seed)
    examples: list[dict] = []

    # Generate a shared product catalogue (reused across examples)
    catalogue: list[dict] = []
    prod_idx = 1
    for cat, size, species, age in iterproduct(
        CATEGORIES.keys(), SIZES, SPECIES, AGE_TARGETS
    ):
        if prod_idx > 500:
            break
        catalogue.append(make_product(cat, species, age, size, rng, prod_idx))
        prod_idx += 1

    # Extend catalogue with "all" species products
    for cat in CATEGORIES.keys():
        for _ in range(10):
            catalogue.append(make_product(cat, "all", "all", "all", rng, prod_idx))
            prod_idx += 1

    print(f"Catalogue: {len(catalogue)} synthetic products")

    for i in range(count):
        user        = make_user_profile(rng)
        history     = make_interactions(user, catalogue, rng)
        # Candidate: 60% compatible, 40% random (to balance classes)
        user_species = set(user["pet_species"])
        if rng.random() < 0.60:
            compatible = [p for p in catalogue
                          if p["species"] in user_species or p["species"] == "all"]
            candidate  = rng.choice(compatible) if compatible else rng.choice(catalogue)
        else:
            candidate = rng.choice(catalogue)

        # Split assignment
        r = rng.random()
        if r < 0.70:
            split = "train"
        elif r < 0.85:
            split = "validation"
        else:
            split = "test"

        example = build_example(user, candidate, history, rng, split, prod_idx)
        examples.append(example)

        if (i + 1) % 500 == 0:
            print(f"  Generated {i + 1}/{count}")

    # Write JSONL
    import os
    os.makedirs(os.path.dirname(output) if os.path.dirname(output) else ".", exist_ok=True)
    with open(output, "w", encoding="utf-8") as f:
        for ex in examples:
            f.write(json.dumps(ex, ensure_ascii=False) + "\n")

    # Stats
    classes = {}
    splits  = {}
    for ex in examples:
        c = ex["label"]["relevance_class"]
        s = ex["split"]
        classes[c] = classes.get(c, 0) + 1
        splits[s]  = splits.get(s, 0) + 1

    print(f"\nWrote {len(examples)} examples → {output}")
    print(f"Class distribution: {classes}")
    print(f"Split distribution: {splits}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="raw/synthetic.jsonl")
    parser.add_argument("--count",  type=int, default=2000)
    parser.add_argument("--seed",   type=int, default=42)
    args = parser.parse_args()
    generate(args.count, args.seed, args.output)
