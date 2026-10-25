# Configuration Stripe — Pet Store (Test Mode)

## 1. Créer un compte Stripe

Rendez-vous sur https://dashboard.stripe.com et créez un compte gratuit.
**Restez en mode Test** (bascule en haut à gauche du dashboard).

## 2. Récupérer vos clés Test

Dans le dashboard Stripe : **Developers → API keys**

| Clé | Format | Usage |
|-----|--------|-------|
| Secret key | `sk_test_...` | Backend uniquement — **jamais dans Git** |
| Publishable key | `pk_test_...` | Frontend — sûre à exposer |

## 3. Configurer le backend

Créez le fichier **`pawly-backend/src/main/resources/application-local.properties`**
(ce fichier est dans `.gitignore` — ne jamais le committer) :

```properties
stripe.secret.key=sk_test_VOTRE_CLE_SECRETE
stripe.webhook.secret=whsec_VOTRE_SECRET_WEBHOOK
```

Lancez le backend avec le profil local :
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## 4. Configurer le frontend

Dans **`pawly-frontend/src/environments/environment.ts`**, remplacez :
```typescript
stripePublishableKey: 'STRIPE_PUBLISHABLE_KEY'
```
par votre vraie clé publiable :
```typescript
stripePublishableKey: 'pk_test_VOTRE_CLE_PUBLIABLE'
```

> La clé publiable n'est **pas secrète** et peut rester dans le code source.

## 5. Configurer le webhook (développement local)

Installez la CLI Stripe :
```bash
# Windows — via Scoop
scoop install stripe

# ou télécharger sur https://github.com/stripe/stripe-cli/releases
```

Écoutez les événements et transmettez-les au backend :
```bash
stripe listen --forward-to http://localhost:8081/api/store/payments/webhook
```

La CLI affiche un **webhook signing secret** (`whsec_...`) — copiez-le dans
`application-local.properties` comme valeur de `stripe.webhook.secret`.

## 6. Tester un paiement

Utilisez ces cartes de test Stripe :

| Scénario | Numéro | Date | CVC |
|----------|--------|------|-----|
| Paiement réussi | `4242 4242 4242 4242` | n'importe quelle date future | n'importe quel CVC |
| Fonds insuffisants | `4000 0000 0000 9995` | n'importe quelle date future | n'importe quel CVC |
| Authentification 3D Secure | `4000 0025 0000 3155` | n'importe quelle date future | n'importe quel CVC |

## 7. Flux de paiement

```
Angular (Checkout)
  → POST /api/store/orders          (crée la commande, panier vidé)
  → POST /api/store/payments/{id}/intent  (crée le PaymentIntent, retourne clientSecret)
  → Stripe.js confirmCardPayment()  (saisie carte côté client — PCI compliant)

Stripe
  → POST /api/store/payments/webhook  (payment_intent.succeeded)
  → StripeService.handleWebhook()   (signature vérifiée, Payment mis à jour)
```

## 8. Variables d'environnement en production

En production, n'utilisez **jamais** `application-local.properties`.
Passez les clés via des variables d'environnement système :

```bash
export STRIPE_SECRET_KEY=sk_live_...
export STRIPE_WEBHOOK_SECRET=whsec_...
```

Spring Boot résout `${STRIPE_SECRET_KEY:}` automatiquement depuis l'environnement.
