# Pawly Pet Store

The Pet Store is the shop module of Pawly. Screens, labels, and API messages are in English, the same language as the Users module (`Welcome back`, `Sign in`, product and order pages).

It reuses the existing account system. There is no second login. A pet owner, a veterinarian, and an administrator sign in through `/api/auth`, then call `/api/store/**` with the same JWT.

## Presentation

A signed-in user can browse products, search and filter them, open a product, add it to a personal cart, and place an order in EUR. Payment is either cash on delivery or a Stripe test-mode card. The buyer can open their order history and a status tracker. Veterinarians manage the products they created. Administrators manage every product, category, stock flag, and order.

Recommendations are computed from the catalogue and a short, non-identifying profile. Spring Boot asks the FastAPI service. That service uses a QLoRA adapter when one is present, and a content-based ranker otherwise.

EasyPost is not part of this repository. Shipping is a flat fee in `CartDTO`, and delivery progress is the order status stored in MySQL.

## Architecture

```
Angular 17  (port 4200)
    JWT via the existing interceptor
        |
Spring Boot 3.2  (port 8081, Java 17)
    auth, cart, orders, Stripe, MySQL
        |
FastAPI  (port 8000)
    POST /recommendations/score
    triage and knowledge routes
        |
QLoRA adapter  (optional, loaded on first score)
        |
MySQL  pawly_db  (port 3306)
```

Spring Boot owns user history and the product catalogue. FastAPI does not read user tables and does not write orders. MySQL is the source for pets, purchases, views, reviews, and the cart, and the sink for the `recommendations` table.

Main packages:

| Area | Path |
|---|---|
| Store API | `pawly-backend/src/main/java/com/pawly/controller/store` |
| Store services | `pawly-backend/src/main/java/com/pawly/service/store` |
| Store entities | `pawly-backend/src/main/java/com/pawly/entity` |
| Angular shop | `pawly-frontend/src/app/features/store` |
| HTTP services | `pawly-frontend/src/app/core/services` |
| FastAPI app | `ai-pawly/main.py` |
| Scoring API | `ai-pawly/recommendations` |
| QLoRA training | `ai-pawly/qlora` |
| Synthetic dataset | `ai-pawly/dataset` |

## Installation

From the repository root, three processes are installed separately.

Backend:

```bash
cd pawly-backend
mvn -q -DskipTests package
```

The parent POM is Spring Boot `3.2.0` and `java.version` is `17`.

Frontend:

```bash
cd pawly-frontend
npm install
```

Angular packages are `^17.3.0`. Tests use Jasmine and Karma (`npm test` runs `ng test --watch=false`).

FastAPI:

```bash
cd ai-pawly
pip install -r requirements.txt
```

That file pins FastAPI `0.110.0` and `httpx==0.27.2`. QLoRA training has its own file, `ai-pawly/qlora/requirements.txt` (PyTorch, Transformers `>= 4.46,< 5`, PEFT, bitsandbytes). Install it only on Python 3.10–3.12 when you intend to train. The service process does not import PyTorch until a score request actually loads an adapter.

## MySQL

`pawly-backend/src/main/resources/application.properties` points at:

- URL host `localhost`, port `3306`, database `pawly_db`
- `createDatabaseIfNotExist=true`
- user property `spring.datasource.username` (checked-in value `root`)
- password property `spring.datasource.password`

Create a local database user and set the password outside the committed file. The checked-in password property is blank. `useSSL=false` and `allowPublicKeyRetrieval=true` are local-development settings. Do not reuse them on a shared server.

Hibernate `ddl-auto` is `update`, so the first backend start creates or updates tables from the JPA entities. `spring.sql.init.mode` is `never`. SQL logging is on (`spring.jpa.show-sql=true`).

Tests do not use MySQL. `src/test/resources/application-test.properties` uses H2:

`jdbc:h2:mem:pawly_test` with `ddl-auto=create-drop`.

FastAPI reads the same database name from `ai-pawly/.env` (see `.env.example`): `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`. The recommendation scorer does not query those tables. Triage and PDF ingestion do.

## Stripe test mode

Currency is EUR. Stripe amounts are sent in cents.

Backend properties, with empty defaults:

```properties
stripe.secret.key=${STRIPE_SECRET_KEY:}
stripe.webhook.secret=${STRIPE_WEBHOOK_SECRET:}
```

Put the test values in `pawly-backend/src/main/resources/application-local.properties`. That path is listed in `.gitignore`. Use a secret key that starts with `sk_test_` and a webhook secret that starts with `whsec_`. Never commit either value.

The Angular publishable key is the field `stripePublishableKey` in `pawly-frontend/src/environments/environment.ts`. Use a `pk_test_` key from the Stripe test dashboard. This document does not copy the value that may already be in that file.

Local webhook forwarding, from `pawly-backend/STRIPE_SETUP.md`:

```bash
stripe listen --forward-to http://localhost:8081/api/store/payments/webhook
```

Copy the signing secret printed by the CLI into `stripe.webhook.secret`.

Official Stripe test cards (not account secrets):

| Case | Number | Date | CVC |
|---|---|---|---|
| Success | 4242 4242 4242 4242 | any future date | any 3 digits |
| Insufficient funds | 4000 0000 0000 9995 | any future date | any 3 digits |
| 3-D Secure | 4000 0025 0000 3155 | any future date | any 3 digits |

Flow:

1. `POST /api/store/orders` with `paymentMethod` `STRIPE` creates the order and a `PENDING` payment.
2. `POST /api/store/payments/{orderId}/intent` creates or reuses a PaymentIntent and returns `clientSecret`.
3. Stripe.js `confirmCardPayment` runs in the browser.
4. Stripe calls `POST /api/store/payments/webhook`. The handler checks the `Stripe-Signature` header with `Webhook.constructEvent`. A missing header is HTTP 400.

Cash on delivery uses `paymentMethod` `CASH_ON_DELIVERY`. The same pending payment row is stored. Delivery marks it succeeded when an administrator sets the order to `DELIVERED`.

## EasyPost

There is no EasyPost client, API key, or shipment call in the backend, the frontend, or FastAPI.

Shipping in `CartDTO`:

| Subtotal | Fee |
|---|---|
| 0 | 0 EUR |
| greater than 0 and below 50 EUR | 5 EUR |
| 50 EUR or more | 0 EUR |

The order copies `shippingFee` and `total` from that calculation. The tracker (`TrackingComponent` and the order detail page) walks the statuses `CONFIRMED`, `PREPARING`, `SHIPPED`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `DELIVERED`. `CANCELLED` replaces the stepper with a cancelled message. Refresh reloads the order from Spring Boot. It does not call a carrier.

## Run Spring Boot, Angular, and FastAPI

Start MySQL first, then the three apps.

```bash
cd pawly-backend
mvn spring-boot:run
```

The server listens on port **8081**. Optional local profile, if `application-local.properties` exists:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

```bash
cd pawly-frontend
npm start
```

`ng serve` uses the development configuration and serves the UI on port **4200**. `environment.ts` calls `http://localhost:8081/api`.

```bash
cd ai-pawly
uvicorn main:app --host 127.0.0.1 --port 8000
```

`main.py` defaults to host `0.0.0.0` and port `AI_SERVICE_PORT` (8000). Bind `127.0.0.1` unless another machine must call it. Interactive docs are `http://127.0.0.1:8000/docs`.

Spring calls `http://localhost:8000` for recommendations (`pawly.ai.base-url`, overridable with `AI_SERVICE_URL`) and `http://localhost:8000/triage` for triage.

## Environment variables

Do not put real values in git. Names only:

| Variable | Process | Role | Default in the repo |
|---|---|---|---|
| `STRIPE_SECRET_KEY` | Spring | Stripe secret, test key locally | empty |
| `STRIPE_WEBHOOK_SECRET` | Spring | Webhook signature | empty |
| `AI_SERVICE_URL` | Spring | FastAPI base URL | `http://localhost:8000` |
| `AI_SERVICE_TOKEN` | Spring and FastAPI | Shared header `X-Pawly-Internal-Token` | empty, which leaves the score route open |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | FastAPI | MySQL for triage knowledge | see `.env.example` |
| `MISTRAL_API_KEY` | FastAPI | Triage model. Not used by recommendations | placeholder in `.env.example` |
| `MISTRAL_MODEL` | FastAPI | Default `open-mistral-7b` | |
| `MISTRAL_TEMPERATURE` | FastAPI | Default `0.3` | |
| `MISTRAL_TIMEOUT` | FastAPI | Default 60 seconds | |
| `AI_SERVICE_PORT` | FastAPI | Default 8000 | |
| `QLORA_ENABLED` | FastAPI | `auto`, `on`, or `off` | `auto` |
| `QLORA_ADAPTER_DIR` | FastAPI | Adapter directory | `ai-pawly/qlora/outputs/qwen25-0.5b-qlora/adapter` |
| `QLORA_TIMEOUT_SECONDS` | FastAPI | Model budget, clamped to 0.2–30 | 6 |
| `QLORA_FORCE_CPU` | FastAPI | Force the CPU QLoRA path | empty |

Spring recommendation client, in `application.properties`:

| Property | Default |
|---|---|
| `pawly.ai.connect-timeout-ms` | 2000 |
| `pawly.ai.read-timeout-ms` | 8000 |
| `pawly.ai.max-retries` | 1 extra attempt, hard cap 2 |
| `pawly.ai.max-candidates` | 8 |
| `pawly.ai.score-path` | `/recommendations/score` |

`jwt.expiration` is `86400000` (24 hours). `jwt.secret` is currently a literal in `application.properties`. Replace it with a private value before any shared deployment. This document does not repeat that literal.

## Endpoints

Base URL: `http://localhost:8081`. The browser sends `Authorization: Bearer <token>` except for the public rows.

Catalogue:

| Method | Path | Who |
|---|---|---|
| GET | `/api/store/categories` | public |
| GET | `/api/store/categories/{id}` | public |
| GET | `/api/store/categories/all` | administrator |
| POST, PUT, DELETE | `/api/store/categories` | administrator |
| GET | `/api/store/products` | public. Stock counts are included for veterinarian and administrator |
| GET | `/api/store/products/featured` | public |
| GET | `/api/store/products/{id}` | public |
| GET | `/api/store/products/{id}/reviews` | public |
| POST | `/api/store/products` | veterinarian or administrator |
| PUT, DELETE | `/api/store/products/{id}` | owner veterinarian, or any administrator |
| PATCH | `/api/store/products/{id}/stock` | same write rule |
| PATCH | `/api/store/products/{id}/availability?available=` | same write rule |
| POST | `/api/store/products/{id}/reviews` | any signed-in user, one review per product |
| PUT, DELETE | `/api/store/products/{id}/reviews/{reviewId}` | review author, or administrator |

Cart, all under `/api/store/cart`, signed in. The service loads the row with `findByIdAndUserId`.

| Method | Path |
|---|---|
| GET | `/api/store/cart` |
| POST | `/api/store/cart` body `{ "productId", "quantity" }` |
| PUT | `/api/store/cart/{itemId}` body `{ "quantity" }` |
| DELETE | `/api/store/cart/{itemId}` |
| DELETE | `/api/store/cart` |

Orders:

| Method | Path | Who |
|---|---|---|
| POST | `/api/store/orders` | signed in |
| GET | `/api/store/orders/my` | signed in, own page |
| GET | `/api/store/orders/my/{orderId}` | owner only, otherwise 404 |
| GET | `/api/store/orders` | administrator. Optional `status`, `orderId` |
| GET | `/api/store/orders/{orderId}` | administrator |
| PATCH | `/api/store/orders/{orderId}/status` | administrator. Body `{ "status" }` |

Payments:

| Method | Path | Who |
|---|---|---|
| POST | `/api/store/payments/{orderId}/intent` | order owner |
| POST | `/api/store/payments/webhook` | Stripe, no JWT, signature required |

Recommendations:

| Method | Path | Who |
|---|---|---|
| GET | `/api/store/recommendations?limit=` | signed in. `limit` is clamped to 1–12 |

FastAPI, default port 8000. See `ai-pawly/recommendations/API.md` for the score schema.

| Method | Path | Notes |
|---|---|---|
| POST | `/recommendations/score` | minimal profile, history, candidates |
| GET | `/recommendations/health` | mode and booleans, no file path |
| POST | `/triage` | symptoms to Mistral |
| POST | `/ingest/pdf` | PDF into the knowledge base |
| POST | `/ingest/seed` | built-in triage seed |
| GET | `/chunks` | knowledge rows |

A request with no JWT to a private Spring route is **403** with the current stateless filter chain.

## Roles and permissions

Roles on `User.Role`: `PET_OWNER`, `VETERINARIAN`, `ADMINISTRATOR`.

The JWT carries an email. `JwtFilter` reloads the user and sets `ROLE_<role>` from MySQL. Method security (`@PreAuthorize`) and `SecurityConfig` both apply.

| Capability | Pet owner | Veterinarian | Administrator |
|---|---|---|---|
| Browse, search, filter | yes | yes | yes |
| Cart, checkout, own orders | yes | yes | yes |
| Reviews | yes | yes | yes |
| Recommendations | yes | yes | yes |
| Create a product | no | yes | yes |
| Edit or delete a product | no | only products they created | any product |
| Stock and availability | no | own products | any product |
| Categories | no | no | yes |
| Every order, status changes | no | no | yes |
| `/api/admin/**` | no | no | yes |

Angular mirrors this with `authGuard`, `vetOrAdminGuard`, and `adminGuard` in `app.routes.ts`. The guards are not a substitute for the API checks.

A pet owner who calls `GET /api/store/orders/{id}` or `PATCH .../status` receives 403. `GET /api/store/orders/my/{id}` for someone else's order returns 404.

## Data model

Hibernate table names:

| Table | Holds |
|---|---|
| `product_categories` | name, description, image URL, `is_active` |
| `products` | category, creator, name, description, price, `stock_quantity`, `is_available`, rating, `review_count`, `target_species` |
| `product_images` | URL, primary flag, sort order |
| `product_reviews` | one row per user and product, rating, comment |
| `cart_items` | one row per user and product, quantity |
| `orders` | buyer, status, payment method, totals, shipping address, city, country, notes |
| `order_items` | snapshot name, quantity, unit price |
| `payments` | one row per order, status, amount, currency `EUR`, Stripe ids |
| `product_views` | product, user, session, time |
| `product_purchase_history` | user, product, order, name, quantity, unit price |
| `recommendations` | user, product, score (precision 5 scale 4), reason (200 characters) |

Order status values: `CONFIRMED`, `PREPARING`, `SHIPPED`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `DELIVERED`, `CANCELLED`. `DELIVERED` and `CANCELLED` cannot change again.

Payment status values: `PENDING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `REFUNDED`.

The line price on an order is `product.price` at checkout. The client does not send a unit price. Stock decrement is `UPDATE ... WHERE stock_quantity >= :qty`.

Users and pets stay in the Users and Pets tables. Recommendations read species and date of birth only, and only to build an age band.

## AI recommendations

`GET /api/store/recommendations` loads the signed-in user, then builds a small JSON document:

- species, lowercased
- age band: `puppy` under 1 year, `adult` under 8, otherwise `senior`
- budget from the median purchase price: `budget` under 15, `mid` under 40, otherwise `premium`, or `unknown` when there is no purchase
- at most 10 latest signals: purchase 1.0, review of 4 or 5 stars 0.9, cart 0.7, view 0.3, review of 1 or 2 stars -0.5
- up to 8 available products, purchased ids removed, matching species (and `all`) first

Pet name, health notes, microchip, email, and phone are not copied into that document.

Spring `POST`s it to FastAPI `/recommendations/score`. Timeouts are 2 s connect and 8 s read, with one retry on HTTP 5xx and connection failures. HTTP 4xx and invalid JSON stop immediately. If the call fails or every score is rejected, `ContentBasedRanker` scores on the JVM with the same weights as `ai-pawly/recommendations/content_based.py`:

| Signal | Weight |
|---|---|
| base | 0.15 |
| species match | +0.35 |
| species mismatch | -0.25 |
| category match | +0.20 |
| token cosine | up to +0.25 |
| rating | up to +0.10 |
| prior positive | +0.10 |
| negative review | -0.20 |

Scores are clamped to 0–1. Unknown product ids and scores outside 0–1 are dropped. Reasons are stripped of email-shaped text and digit runs of length 8 or more, then cut to 200 characters.

The Angular page calls `StoreService.getRecommendations(12)` and prints `reason` under each card. The subtitle says the model was used when `source` is `qlora`.

`QLORA_ENABLED=auto` loads the adapter only when the directory exists. `off` never loads it. The model is not loaded at process start. `/recommendations/health` returns `status`, `qlora_mode`, `adapter_present`, and `model_enabled`.

## QLoRA training and evaluation

Details and the model check are in `ai-pawly/qlora/README.md`.

The base model is the public `Qwen/Qwen2.5-0.5B-Instruct` (Apache 2.0, about 0.49B parameters). Training saves LoRA adapters only. It does not rewrite or merge the base weights.

Data: `ai-pawly/dataset` JSONL, seed 42, about 341 train, 68 validation, and 91 test rows. The prompt is a user profile, an interaction history, one candidate product, and a score from 0 to 1 with a short justification.

Defaults used by the trainer: rank 8, alpha 16, dropout 0.05, max sequence 512, learning rate 2e-4, 2 epochs, batch 1, gradient accumulation 8. GPU path uses 4-bit NF4 when bitsandbytes and CUDA are available and at least 3 GB of VRAM are free. Otherwise the code falls back to CPU and disables quantization. That fallback is explicit in the logs.

```bash
cd ai-pawly/qlora
pip install -r requirements.txt
python -m pytest tests -q
python train.py
python evaluate.py --adapter outputs/qwen25-0.5b-qlora/adapter
python infer.py --adapter outputs/qwen25-0.5b-qlora/adapter --input examples/example.json
```

`evaluate.py` reads the test split only and writes `predictions.jsonl` and `metrics.json`. Reported metrics include parse success, MAE, RMSE, Pearson r, class accuracy, macro-F1, and strict class accuracy. Class bands match the dataset: ≤ 0.25 `not_relevant`, ≤ 0.50 `somewhat`, ≤ 0.75 `relevant`, otherwise `highly_relevant`.

The unit tests do not download the model. A full GPU training run was not executed in the environment where this module was built, because the default interpreter had no PyTorch. Use Python 3.10–3.12 and a CUDA wheel for the 4-bit path.

## Limits

- EasyPost is absent. There is no label, tracking number, or carrier failure to handle.
- A Stripe order is stored as `CONFIRMED` and stock is decremented before the card payment succeeds. A failed payment updates the payment row. It does not by itself cancel the order or restore stock.
- `GET /api/store/products/{id}` returns `stockQuantity` and the creator name to anonymous callers. The list endpoint hides the stock count from them.
- FastAPI score, triage, PDF ingest, and chunk listing do not require a token while `AI_SERVICE_TOKEN` is empty. `main.py` listens on `0.0.0.0` unless you override the host.
- Deactivating a user does not revoke a JWT that is still inside the 24-hour window. The filter does not consult the enabled flag.
- Reviews do not require a prior purchase.
- QLoRA quality depends on the synthetic set (341 training rows). A missing adapter is a normal case: the content-based ranker answers.
- Angular tests need a `node` executable on `PATH`. The test target is `pawly-frontend` in `angular.json`.

## Security

What the store enforces today:

- Cart and own-order reads are scoped by the caller email from the JWT.
- Product writes by a veterinarian check `createdBy`.
- Order totals use the database price.
- The Stripe webhook rejects a missing secret and a bad signature.
- Recommendation logs in FastAPI record counts, source, and elapsed time. They do not record the profile, product names, or the justification. Spring logs status and error type, not the body or the internal token.
- FastAPI returns `{"detail":"invalid recommendation request"}` on a bad score payload and does not echo the body.
- CORS on Spring allows `http://localhost:4200` and `http://127.0.0.1:4200` with credentials. FastAPI allows `http://localhost:8081` and `http://localhost:4200`.

Before a shared deployment, move `jwt.secret` and the database password out of the committed properties, set `AI_SERVICE_TOKEN` to the same non-empty value on Spring and FastAPI, bind FastAPI to localhost, and turn off SQL debug logging. No API key belongs in this document or in a commit.

## Tests

Backend, H2, JUnit, Mockito, MockMvc, Spring Security Test:

```bash
cd pawly-backend
mvn -Dtest=com.pawly.store.** test
```

That slice covers products, cart, orders, Stripe (the PaymentIntent client is mocked, including a declined charge), recommendations (the AI client is mocked), roles, validation, stock, unavailable products, and anonymous access. EasyPost is not mocked because the class does not exist.

Frontend, Jasmine and Karma:

```bash
cd pawly-frontend
npm test
```

Specs live next to the shop components and under `src/app/core`. They mock `HttpClient`, `AuthService`, and Stripe.js.

Python:

```bash
cd ai-pawly
python -m pytest recommendations/tests -q
cd qlora
python -m pytest tests -q
```

## Usage examples

Sign in with the Users login, then call the shop with the returned token. The samples use the placeholder `TOKEN`. They are not credentials.

List products:

```http
GET /api/store/products?search=kibble&species=dog&page=0&size=12
```

Add to cart:

```http
POST /api/store/cart
Authorization: Bearer TOKEN
Content-Type: application/json

{"productId": 5, "quantity": 1}
```

Place a cash order:

```http
POST /api/store/orders
Authorization: Bearer TOKEN
Content-Type: application/json

{
  "paymentMethod": "CASH_ON_DELIVERY",
  "shippingAddress": "1 Example Street",
  "shippingCity": "Tunis",
  "shippingCountry": "TN"
}
```

Open the card step only after that order exists, then:

```http
POST /api/store/payments/9/intent
Authorization: Bearer TOKEN
```

The JSON `clientSecret` is passed to Stripe.js. It is not the Stripe secret key.

Recommendations:

```http
GET /api/store/recommendations?limit=8
Authorization: Bearer TOKEN
```

A direct score call, with the internal token when you have set one:

```http
POST /recommendations/score
X-Pawly-Internal-Token: TOKEN
Content-Type: application/json

{
  "profile": {"pet_species": ["dog"], "pet_ages": ["senior"], "budget_tier": "budget"},
  "history": [],
  "candidates": [{
    "product_id": "5",
    "name": "Dog Kibble",
    "category": "Food",
    "species": "dog",
    "price_eur": 19.99,
    "description": "Food for dogs",
    "avg_rating": 5,
    "review_count": 4
  }],
  "limit": 8
}
```

In the UI, the same flows are `/store/products`, `/store/products/:id`, `/store/cart`, `/store/checkout`, `/store/orders`, `/store/orders/:id`, and `/store/recommendations`.
