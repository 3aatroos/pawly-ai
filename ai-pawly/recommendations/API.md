# API recommandations Pawly

Angular appelle Spring Boot. Spring Boot authentifie l'utilisateur, lit l'historique dans MySQL, envoie un contexte minimal à FastAPI, valide la réponse, enregistre les lignes dans `recommendations`, puis renvoie `ProductRecommendationDTO`.

```
Angular  GET /api/store/recommendations
Spring   JWT, historique, validation, MySQL
FastAPI  POST /recommendations/score
QLoRA    adaptateur si présent, sinon content-based
MySQL    table recommendations
```

Aucun secret n'est écrit dans le code. Le jeton interne, s'il est utilisé, vient de la variable d'environnement `AI_SERVICE_TOKEN` des deux processus.

## Spring Boot

`GET /api/store/recommendations?limit=8`

Authentification JWT obligatoire. Une requête sans jeton reçoit 403, comme le reste des routes privées de la chaîne stateless. `limit` est borné de 1 à 12.

Le corps est une liste de `ProductRecommendationDTO` :

| Champ | Contenu |
|---|---|
| `productId`, `categoryId`, `categoryName`, `name`, `price` | produit du catalogue |
| `isAvailable`, `averageRating`, `reviewCount`, `targetSpecies`, `primaryImageUrl` | affichage |
| `score` | 0.0000 à 1.0000 |
| `reason` | justification tronquée à 200 caractères, sans e-mail ni longue suite de chiffres |
| `source` | `qlora` ou `content_based` |

Absents de la réponse : e-mail, nom, téléphone, nom de l'animal, puce, notes de santé, stock, auteur du produit.

Spring envoie à Python uniquement :

- espèces et tranches d'âge (`puppy` < 1 an, `adult` < 8 ans, `senior` ensuite)
- palier de budget déduit des prix d'achat (`budget` < 15, `mid` < 40, sinon `premium`, `unknown` sans achat)
- au plus 10 interactions (`purchase` 1.0, `review_pos` 0.9, `cart` 0.7, `view` 0.3, `review_neg` -0.5)
- au plus 8 produits candidats disponibles, hors achats déjà faits

Les commentaires d'avis ne sont pas transmis.

### Appel Python

| Réglage | Défaut | Rôle |
|---|---|---|
| `pawly.ai.base-url` | `http://localhost:8000` | `AI_SERVICE_URL` |
| `pawly.ai.connect-timeout-ms` | 2000 | timeout de connexion |
| `pawly.ai.read-timeout-ms` | 8000 | timeout de lecture |
| `pawly.ai.max-retries` | 1 | un seul nouvel essai, plafonné à 2 |
| `pawly.ai.internal-token` | vide | `AI_SERVICE_TOKEN` |

Les essais supplémentaires ne concernent que les réponses 5xx et les erreurs d'accès (connexion refusée ou timeout). Un 4xx ou un JSON invalide bascule tout de suite sur le repli content-based Java. Les journaux Spring indiquent le statut HTTP, le nombre d'essais et le type d'erreur. Ils ne contiennent ni le corps, ni le jeton, ni le profil.

Les scores acceptés sont ceux dont `product_id` faisait partie des candidats et dont la valeur est dans [0, 1]. Les autres sont écartés. Si plus rien ne reste, le repli content-based est utilisé. Les lignes retenues sont écrites dans `recommendations` (score, raison).

## FastAPI

Documentation interactive : `GET /docs` une fois le service lancé (`uvicorn main:app --port 8000` depuis `ai-pawly`).

### `GET /recommendations/health`

```json
{"status":"ok","qlora_mode":"auto","adapter_present":false,"model_enabled":false}
```

Pas de chemin de fichier, pas de jeton.

### `POST /recommendations/score`

JSON strict (`extra` interdit). Une adresse e-mail ou une suite de 8 chiffres ou plus dans un texte est refusée. La réponse 422 est `{"detail":"invalid recommendation request"}` et ne répète pas le corps reçu.

Si `AI_SERVICE_TOKEN` est défini, l'en-tête `X-Pawly-Internal-Token` est obligatoire. Sinon l'appel local reste ouvert.

Exemple minimal :

```json
{
  "profile": {"pet_species": ["dog"], "pet_ages": ["senior"], "budget_tier": "mid"},
  "history": [
    {"product_id": "12", "product_name": "Senior kibble", "signal": "purchase", "weight": 1.0, "category": "Food", "species": "dog"}
  ],
  "candidates": [
    {"product_id": "44", "name": "Joint chews", "category": "Health", "species": "dog", "price_eur": 24.9, "description": "Chew for senior dogs", "avg_rating": 4.6, "review_count": 12}
  ],
  "limit": 8
}
```

Réponse :

```json
{
  "source": "content_based",
  "recommendations": [
    {"product_id": "44", "score": 0.82, "relevance_class": "highly_relevant", "justification": "Species match (dog)."}
  ]
}
```

`source` vaut `qlora` quand l'adaptateur répond dans le délai. Il vaut `content_based` si `QLORA_ENABLED=off`, si le dossier d'adaptateur est absent en mode `auto`, si le chargement échoue, si le délai `QLORA_TIMEOUT_SECONDS` (défaut 6 s, plafond 30 s) est dépassé, ou si aucun score n'est lisible.

Le repli content-based reprend les poids du fine-tuning : espèce +0.35 ou -0.25, catégorie +0.20, similarité de tokens jusqu'à +0.25, note jusqu'à +0.10, interaction positive +0.10, avis négatif -0.20, base +0.15. Le même calcul est dans `ContentBasedRanker` côté Java.

Les journaux FastAPI ont la forme `recommendation_scored candidates=8 history=3 source=content_based elapsed_ms=12`. Ils ne contiennent ni le profil, ni les noms de produits, ni la justification.

| Variable | Défaut | Rôle |
|---|---|---|
| `QLORA_ENABLED` | `auto` | `on`, `off`, ou `auto` si l'adaptateur existe |
| `QLORA_ADAPTER_DIR` | `qlora/outputs/qwen25-0.5b-qlora/adapter` | adaptateur LoRA seul |
| `QLORA_TIMEOUT_SECONDS` | 6 | budget du modèle pour la requête |
| `QLORA_FORCE_CPU` | vide | force le repli CPU du runtime QLoRA |
| `AI_SERVICE_TOKEN` | vide | jeton partagé avec Spring |
