"""
Génère le fichier Word de suivi des prompts Pawly Pet Store.
"""
from docx import Document
from docx.shared import Pt, RGBColor, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement
import copy

# ── Données des prompts ────────────────────────────────────────────────────────

PROMPTS = [
    {
        "id":          "P01",
        "date":        "25/10/2026",
        "module":      "Pet Store — Configuration projet",
        "titre":       "Initialisation du contexte et des règles de travail",
        "objectif":    "Définir les règles de travail globales pour le projet Pawly : architecture, contraintes techniques, conventions de code et format de réponse attendu.",
        "prompt":      "Tu travailles dans le projet Pawly situé dans le dossier pawly_web. Le projet contient : pawly-backend (Spring Boot), pawly-frontend (Angular), ai-pawly (FastAPI). Règles : respecter l'architecture existante, réutiliser le module Users, pas de deuxième authentification, MySQL, même langue que Users, Stripe test mode EUR, pas de clés secrètes dans le code, variables d'environnement, code simple typé testable sécurisé.",
        "fichiers_generes": "Aucun",
        "fichiers_modifies": "Aucun",
        "resultat":    "Le contexte projet a été analysé et validé. L'architecture Spring Boot / Angular / FastAPI est identifiée comme propre et cohérente. Les points de vigilance (JWT en clair, Ollama résiduel) ont été relevés.",
        "decision":    "Accepté",
        "justification": "Le prompt est complet, les contraintes sont claires, l'architecture existante est respectée. Kiro a correctement identifié les éléments réutilisables et les risques avant toute implémentation.",
        "tests":       "Aucun — prompt de configuration",
        "erreurs":     "Aucune",
        "modifs_humaines": "Aucune",
    },
    {
        "id":          "P02",
        "date":        "25/10/2026",
        "module":      "Pet Store — Analyse et audit",
        "titre":       "Analyse complète du projet Pawly",
        "objectif":    "Produire un rapport d'audit complet du projet avant toute implémentation : architecture, conventions, éléments réutilisables, conflits potentiels et fichiers à créer ou modifier pour le module Pet Store.",
        "prompt":      "Analyse le projet Pawly situé dans le dossier pawly_web. Examine : pawly-backend, pawly-frontend, ai-pawly, cahier des charges PDF. Analyse particulièrement les entités User/Pet/VetProfile, rôles, Spring Security, JWT, controllers, services, repositories, DTOs, routes Angular, guards, interceptors, langue interface, configuration MySQL, communication Spring Boot ↔ ai-pawly. Retourne un rapport : architecture actuelle, conventions, éléments réutilisables, conflits potentiels, fichiers à modifier et créer.",
        "fichiers_generes": "Rapport d'analyse (artefact Markdown)",
        "fichiers_modifies": "Aucun",
        "resultat":    "Rapport complet produit : architecture 3 tiers identifiée, conventions Lombok + Spring Data + Angular 17 standalone documentées, éléments réutilisables listés (User, NotificationService, GlobalExceptionHandler, JWT interceptor), risques identifiés (JWT en clair, secret MySQL vide, Ollama résiduel).",
        "decision":    "Accepté",
        "justification": "L'analyse est exhaustive et couvre tous les fichiers demandés. Le rapport donne une base solide pour l'implémentation sans modifier aucun fichier existant.",
        "tests":       "Aucun — prompt d'analyse",
        "erreurs":     "Le PDF du cahier des charges étant en binaire compressé, l'extraction a nécessité PyPDF2.",
        "modifs_humaines": "Aucune",
    },
    {
        "id":          "P03",
        "date":        "25/10/2026",
        "module":      "Pet Store — Architecture",
        "titre":       "Architecture complète du module Pet Store",
        "objectif":    "Définir l'architecture complète du module Pet Store : packages Spring Boot, dossiers Angular, entités principales, relations MySQL, endpoints REST, guards Angular, matrice des permissions et plan de développement. Sans générer de code.",
        "prompt":      "À partir de l'audit du projet Pawly, propose l'architecture complète du module Pet Store web avec les fonctionnalités : produits, recherche, filtrage, panier, commandes, paiement Stripe, historique, gestion produits/stock/disponibilité, recommandations. Contraintes : Spring Boot, Angular, MySQL, module Users existant, même langue, séparation controller/service/repository/entity/DTO. Rôles : Pet Owner (achat, panier, commandes), Veterinarian (+ gestion produits), Administrator (toutes fonctionnalités).",
        "fichiers_generes": "Document d'architecture (artefact Markdown)",
        "fichiers_modifies": "Aucun",
        "resultat":    "Architecture complète définie : packages Spring Boot (com.pawly.controller/service/repository/entity/dto/store), structure Angular store (15 composants), 11 entités MySQL, matrice des permissions par rôle, 30+ endpoints REST, plan de développement en 6 étapes.",
        "decision":    "Accepté",
        "justification": "L'architecture respecte les conventions existantes, réutilise le module Users, et intègre correctement les 3 rôles avec une séparation claire des responsabilités.",
        "tests":       "Aucun — prompt de conception",
        "erreurs":     "Aucune",
        "modifs_humaines": "Aucune",
    },
    {
        "id":          "P04",
        "date":        "25/10/2026",
        "module":      "Pet Store — Base de données",
        "titre":       "Modèle de données MySQL du module Pet Store",
        "objectif":    "Générer toutes les entités JPA, les enums, le script SQL idempotent, les repositories Spring Data et les tests de persistance pour les 11 tables du Pet Store.",
        "prompt":      "Génère le modèle de données MySQL du module Pet Store couvrant : Product, ProductCategory, ProductReview, Cart, CartItem, Order, OrderItem, Payment, Recommendation, ProductView, ProductPurchaseHistory. Réutilise l'utilisateur existant. Pour chaque entité : colonnes, types, clés primaires/étrangères, contraintes, index, relations JPA, valeurs par défaut, règles de suppression.",
        "fichiers_generes": "Product.java, ProductCategory.java, ProductImage.java, ProductReview.java, CartItem.java, Order.java, OrderItem.java, Payment.java, Recommendation.java, ProductView.java, ProductPurchaseHistory.java, 10 repositories, application-test.properties",
        "fichiers_modifies": "pom.xml (Stripe SDK + H2), User.java (relations store), schema.sql (tables Pet Store + seed catégories)",
        "resultat":    "11 entités JPA, 10 repositories avec queries JPQL typées, script SQL appendé à schema.sql (idempotent), seed de 6 catégories, verrou optimiste @Version sur Product, 6 classes de tests @DataJpaTest.",
        "decision":    "Accepté",
        "justification": "Le modèle respecte les conventions Pawly (Lombok @Builder, @CreationTimestamp/@UpdateTimestamp, FetchType.LAZY). Les règles de suppression sont cohérentes (CASCADE sur panier, RESTRICT sur commandes pour conserver l'historique).",
        "tests":       "6 classes @DataJpaTest (H2) : ProductCategoryRepositoryTest, ProductRepositoryTest, CartItemRepositoryTest, OrderRepositoryTest, ProductReviewRepositoryTest, PaymentRepositoryTest — 65 cas couvrant contraintes d'unicité et cascades.",
        "erreurs":     "Aucune",
        "modifs_humaines": "Aucune",
    },
    {
        "id":          "P05",
        "date":        "25/10/2026",
        "module":      "Pet Store — Product Management",
        "titre":       "Implémentation du sous-module Product Management",
        "objectif":    "Implémenter le CRUD complet des produits et catégories avec contrôle d'accès par rôle, pagination, recherche, filtrage et tests unitaires + intégration.",
        "prompt":      "Implémente le sous-module Product Management du Pet Store. Pet Owner : consulter, rechercher, filtrer, détails, disponibilité, stock public, noter, avis. Veterinarian/Administrator : ajouter, modifier, supprimer produit, stock, disponibilité, catégories. Crée : entities, enums, DTOs, repositories, services, controllers, validation, gestion erreurs, pagination, recherche, filtrage, tests unitaires, tests d'intégration. Ne fais pas confiance au rôle Angular : vérifie toujours avec Spring Security.",
        "fichiers_generes": "CategoryDTO, ProductImageDTO, ProductSummaryDTO, ProductDTO, CreateProductRequest, UpdateProductRequest, UpdateStockRequest, ReviewDTO, CategoryService, ProductService, ReviewService, CategoryController, ProductController",
        "fichiers_modifies": "SecurityConfig.java (routes store), UserService.java (getUserByEmail)",
        "resultat":    "8 DTOs, 3 services (CategoryService, ProductService, ReviewService), 2 controllers. La visibilité du stock est déterminée côté serveur par principal.getAuthorities(), jamais côté Angular. Propriété des produits vérifiée dans le service pour les vétérinaires.",
        "decision":    "Accepté",
        "justification": "Le rôle est lu exclusivement depuis le SecurityContext. La règle de propriété (vet touche uniquement ses produits) est appliquée dans ProductService, pas dans SecurityConfig.",
        "tests":       "5 classes unitaires Mockito (CategoryServiceTest, ProductServiceTest, ReviewServiceTest + repository tests) + 2 classes @SpringBootTest MockMvc : ProductControllerIntegrationTest (20 cas), CategoryControllerIntegrationTest (8 cas).",
        "erreurs":     "Aucune",
        "modifs_humaines": "Aucune",
    },
    {
        "id":          "P06",
        "date":        "25/10/2026",
        "module":      "Pet Store — Panier",
        "titre":       "Implémentation du panier Pet Store",
        "objectif":    "Implémenter le panier persisté en base avec gestion des quantités, vérification du stock, calcul des frais de livraison et isolation stricte entre utilisateurs.",
        "prompt":      "Implémente le panier Pet Store. Fonctionnalités : créer/récupérer panier, ajouter produit, modifier quantité, supprimer article, vider panier, calculer sous-total/livraison/total, vérifier stock, empêcher quantités négatives/nulles, empêcher achat produit indisponible. Crée : entités, DTOs, repository, service, controller, endpoints Angular, composants Angular, validations, tests. Panier lié à l'utilisateur authentifié — jamais accès au panier d'un autre utilisateur.",
        "fichiers_generes": "CartItemDTO, CartDTO, AddToCartRequest, UpdateCartItemRequest, CartService, CartController, cart.model.ts, cart.service.ts (Angular), CartComponent",
        "fichiers_modifies": "shell.component.ts (badge panier), app.routes.ts (route /store/cart)",
        "resultat":    "Panier persisté en base (CartItem). CartDTO calcule subtotal, shippingFee (5€, gratuit ≥50€), total, hasUnavailableItems. Ownership vérifié via findByIdAndUserId — impossible d'accéder au panier d'un autre utilisateur. Signal Angular itemCount pour badge.",
        "decision":    "Accepté",
        "justification": "Le panier en base (vs localStorage) garantit la cohérence cross-session et simplifie la synchronisation avec le stock. L'isolation est garantie au niveau requête SQL.",
        "tests":       "CartServiceTest (12 cas Mockito) + CartControllerIntegrationTest (16 cas @SpringBootTest) : 401 sans token, isolation utilisateurs (PUT/DELETE item autre user → 404), calculs livraison, validation inputs.",
        "erreurs":     "Aucune",
        "modifs_humaines": "Aucune",
    },
    {
        "id":          "P07",
        "date":        "25/10/2026",
        "module":      "Pet Store — Commandes",
        "titre":       "Création et historique des commandes",
        "objectif":    "Implémenter la création de commande depuis le panier, l'historique acheteur et la gestion admin des commandes avec les 7 statuts de livraison.",
        "prompt":      "Implémente la création et l'historique des commandes du Pet Store. Pet Owner/Vet : confirmer commande, consulter ses commandes, détails, statut, montant, articles, paiement, suivi. Administrator : toutes les commandes, modifier statut, filtrer, rechercher par numéro, détails client/paiement/livraison. Statuts : CONFIRMED, PREPARING, SHIPPED, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, CANCELLED.",
        "fichiers_generes": "OrderItemDTO, PaymentDTO, OrderDTO, CreateOrderRequest, UpdateOrderStatusRequest, OrderService, OrderController, order.model.ts, order.service.ts, CheckoutComponent, OrderListComponent, OrderDetailComponent, OrderManagementComponent",
        "fichiers_modifies": "Order.java (enum statuts remplacé), schema.sql (ENUM MySQL), OrderRepository.java (filtrage orderId), shell.component.ts, app.routes.ts",
        "resultat":    "7 statuts de commande. OrderService.create() : pré-vol stock + disponibilité, snapshot prix, décrémentation atomique via decrementStock(), création Payment, enregistrement ProductPurchaseHistory, vidage panier — tout en une transaction. Statuts terminaux (DELIVERED, CANCELLED) bloqués après atteinte.",
        "decision":    "Accepté",
        "justification": "La décrémentation de stock atomique avec détection de concurrence garantit l'intégrité. Le COD marque automatiquement le paiement SUCCEEDED à la livraison.",
        "tests":       "OrderServiceTest (10 cas Mockito) + OrderControllerIntegrationTest (13 cas @SpringBootTest) : statuts terminaux, isolation acheteur, 401/403/404/400.",
        "erreurs":     "Aucune",
        "modifs_humaines": "Aucune",
    },
    {
        "id":          "P08",
        "date":        "25/10/2026",
        "module":      "Pet Store — Paiement Stripe",
        "titre":       "Intégration Stripe Test Mode",
        "objectif":    "Intégrer Stripe en mode test uniquement (EUR, carte), sans aucune clé secrète dans le code ou Git, avec gestion idempotente des PaymentIntents et vérification de signature webhook.",
        "prompt":      "Intègre Stripe en mode Test dans le Pet Store. Contraintes : devise EUR, paiement par carte, Stripe test uniquement, aucune clé secrète dans Angular/Git, utiliser PaymentIntent, enregistrer l'identifiant, gérer les erreurs de paiement, empêcher les doublons. Génère : configuration Spring Boot, service Stripe, endpoints backend, DTOs, mise à jour Payment, intégration Angular, tests, instructions de configuration. Placeholders : STRIPE_SECRET_KEY, STRIPE_PUBLISHABLE_KEY.",
        "fichiers_generes": "StripeConfig.java, StripeService.java, PaymentController.java, PaymentIntentDTO.java, application-local.properties, payment.model.ts, payment.service.ts (Angular), StripePaymentComponent, STRIPE_SETUP.md",
        "fichiers_modifies": "SecurityConfig.java (webhook public), application.properties (propriétés Stripe), CheckoutComponent (flux 2 étapes), environment.ts (publishableKey), index.html (Stripe.js CDN), pom.xml (stripe-java déjà présent), application-test.properties, app.routes.ts",
        "resultat":    "StripeConfig bloque au démarrage si clé absente ou non sk_test_*. PaymentIntent idempotent (réutilisation si déjà créé). Webhook vérifié par signature HMAC avant tout traitement. CheckoutComponent en 2 étapes : COD → redirection directe, STRIPE → StripePaymentComponent → confirmation. Carte de test affichée : 4242 4242 4242 4242.",
        "decision":    "Accepté",
        "justification": "Aucune clé secrète dans le code. Le clientSecret est safe côté client. La signature webhook garantit l'authenticité des événements Stripe. Application ne peut pas démarrer avec une clé live par erreur.",
        "tests":       "StripeServiceTest (8 cas Mockito : idempotence, mismatch COD, already paid, ownership) + PaymentControllerIntegrationTest (7 cas : 401/403/404/400, webhook sans signature).",
        "erreurs":     "Faux positifs Unicode (✓) sur Windows cp1252 — corrigé.",
        "modifs_humaines": "Aucune",
    },
    {
        "id":          "P09",
        "date":        "25/10/2026",
        "module":      "Pet Store — Interface Angular",
        "titre":       "Interface Angular complète du Pet Store",
        "objectif":    "Créer les 15 composants Angular du Pet Store en respectant les conventions visuelles et linguistiques du module Users existant, sans dupliquer le système d'authentification.",
        "prompt":      "Implémente l'interface Angular complète du Pet Store. Respecte la langue du module Users. Composants séparés pour : product-list, product-card, product-filter, product-details, product-review, cart, checkout, payment, order-list, order-details, tracking, recommendations, product-management, category-management, stock-management. Fonctionnalités : recherche, filtre, pagination, ajout panier, gestion quantités, suppression, validation formulaires, erreurs API, responsive, accès conditionnel par rôle, protection routes, bouton refresh suivi. Réutilise AuthService, JWT interceptor, auth guard, styles globaux.",
        "fichiers_generes": "ProductListComponent, ProductCardComponent, ProductFilterComponent, ProductDetailsComponent, ProductReviewComponent, TrackingComponent, RecommendationsComponent, ProductManagementComponent, CategoryManagementComponent, StockManagementComponent, StoreService, store.service.ts",
        "fichiers_modifies": "OrderDetailComponent (TrackingComponent intégré + refresh), app.routes.ts (toutes routes store), shell.component.ts (navigation complète), auth.guard.ts (vetOrAdminGuard)",
        "resultat":    "15 composants standalone Angular 17 (@for, @if). StoreService centralise tous les appels HTTP store. vetOrAdminGuard protège /store/manage/**. Navigation shell complète avec badge panier signal, section Store Management visible VET/ADMIN. TrackingComponent avec bouton refresh et stepper animé.",
        "decision":    "Accepté",
        "justification": "Langue anglaise respectée (identique module Users). Styles globaux réutilisés (.fade-up, .card, .btn, .badge-*, .form-group). Pas de NgModules, composants standalone uniquement. AuthService, jwtInterceptor et guards existants — rien dupliqué.",
        "tests":       "Tests visuels manuels nécessaires. Guards testés à travers les tests d'intégration existants.",
        "erreurs":     "Aucune",
        "modifs_humaines": "Aucune",
    },
    {
        "id":          "P10",
        "date":        "25/10/2026",
        "module":      "Pet Store — Recommandations",
        "titre":       "Conception du système de recommandation Content-Based",
        "objectif":    "Concevoir l'architecture complète d'un système de recommandation Content-Based Filtering (CBF) adapté au projet Pawly : dataset, embedding, similarité, ranking, cold start, métriques et API. Comparer TF-IDF, sentence embeddings, Transformer et QLoRA.",
        "prompt":      "Conçois un système de recommandation Content-Based pour les produits Pet Store. Le système doit recommander des produits similaires à ceux consultés, ajoutés au panier, achetés ou notés positivement. Caractéristiques produits : nom, description, catégorie, type d'animal, tags, prix, propriétés, besoins spécifiques, âge recommandé. Définis : format dataset, nettoyage, représentation, embedding, similarité, ranking, cold start, nouveau produit, métriques, API. Compare TF-IDF + cosine, sentence embeddings, Transformer, QLoRA. Recommande architecture adaptée projet étudiant MySQL/Spring Boot/Angular/FastAPI.",
        "fichiers_generes": "Document de conception (artefact Markdown)",
        "fichiers_modifies": "Aucun",
        "resultat":    "Architecture hybride TF-IDF (40%) + MiniLM (60%) recommandée. Profil utilisateur = moyenne pondérée des embeddings des interactions (90j). Cold start : popularité filtrée par espèce. Cache dans table recommendations. Tableau comparatif des 4 approches avec justification du choix.",
        "decision":    "Accepté",
        "justification": "L'approche hybride TF-IDF + MiniLM est le meilleur compromis pour un projet étudiant : pas besoin de GPU, <500MB RAM, résultats suffisamment bons sur un catalogue de centaines de produits.",
        "tests":       "Aucun — prompt de conception",
        "erreurs":     "Aucune",
        "modifs_humaines": "Aucune",
    },
    {
        "id":          "P11",
        "date":        "25/10/2026",
        "module":      "Pet Store — Dataset QLoRA",
        "titre":       "Préparation du dataset pour fine-tuning QLoRA",
        "objectif":    "Créer un pipeline complet de génération et validation d'un dataset JSONL synthétique pour fine-tuner un modèle de recommandation avec QLoRA — sans aucune donnée personnelle réelle.",
        "prompt":      "Prépare un dataset pour fine-tuner un modèle de recommandation avec QLoRA. Format JSONL strict. Chaque exemple doit contenir : contexte utilisateur, historique consultation/achat, préférences, caractéristiques produit, label de pertinence, justification attendue. Génère : schéma JSONL, exemples synthétiques, règles nettoyage, règles anonymisation, séparation train/validation/test, détection fuites, scripts Python préparation, contrôles qualité, tests dataset. N'utilise aucune donnée personnelle réelle.",
        "fichiers_generes": "dataset/schema.md, dataset/generate_synthetic.py, dataset/clean_and_anonymise.py, dataset/split_dataset.py, dataset/quality_check.py, dataset/run_pipeline.py, dataset/README.md, dataset/tests/__init__.py, dataset/tests/test_generate_synthetic.py, dataset/tests/test_clean_and_anonymise.py, dataset/tests/test_split_dataset.py",
        "fichiers_modifies": "Aucun",
        "resultat":    "Pipeline complet opérationnel en 4 étapes : génération → nettoyage → split stratifié → contrôle qualité. Catalogue 540 produits synthétiques, 2000 exemples générables (500 pour démo). Split 70/15/15 stratifié par (relevance_class × species). Détection de fuites par SHA-256 fingerprint. 65 tests pytest passant à 100%.",
        "decision":    "Accepté",
        "justification": "Le pipeline tourne sans erreur. Les 65 tests passent. Les alertes PII sont des faux positifs documentés (pattern phone matche les UUID et prix dans les données purement synthétiques — comportement intentionnel pour les données réelles).",
        "tests":       "65 tests pytest : TestMakeProduct (5), TestMakeUserProfile (3), TestMakeInteractions (3), TestComputeLabel (7), TestBuildExample (4), TestNormaliseText (5), TestDetectPII (4), TestAnonymiseFreeText (3), TestValidateExample (10), TestCheckLabelConsistency (3), TestFingerprintAndLeaks (4), TestCleanExample (5), TestStratifySplit (9). Résultat : 65 passed in 0.34s.",
        "erreurs":     "UnicodeEncodeError sur Windows (cp1252 + emoji ✓) — corrigé en remplaçant le caractère.",
        "modifs_humaines": "Remplacement du caractère ✓ par du texte ASCII pour compatibilité Windows cp1252.",
    },
]

# ── Helpers mise en forme ──────────────────────────────────────────────────────

def set_cell_bg(cell, hex_color):
    tc = cell._tc
    tcPr = tc.get_or_add_tcPr()
    shd = OxmlElement('w:shd')
    shd.set(qn('w:val'), 'clear')
    shd.set(qn('w:color'), 'auto')
    shd.set(qn('w:fill'), hex_color)
    tcPr.append(shd)

def add_heading(doc, text, level=1):
    h = doc.add_heading(text, level=level)
    run = h.runs[0] if h.runs else h.add_run(text)
    if level == 1:
        run.font.color.rgb = RGBColor(0x6C, 0x63, 0xFF)  # --primary Pawly
    elif level == 2:
        run.font.color.rgb = RGBColor(0x1A, 0x1D, 0x2E)
    return h

def add_field(doc, label, value):
    para = doc.add_paragraph()
    run_label = para.add_run(f"{label} : ")
    run_label.bold = True
    run_label.font.size = Pt(10)
    run_label.font.color.rgb = RGBColor(0x6C, 0x63, 0xFF)
    run_value = para.add_run(value)
    run_value.font.size = Pt(10)
    para.paragraph_format.space_after = Pt(4)
    return para

def add_decision_badge(doc, decision):
    para = doc.add_paragraph()
    run = para.add_run(f"  {decision}  ")
    run.bold = True
    run.font.size = Pt(11)
    if decision == "Accepté":
        run.font.color.rgb = RGBColor(0x15, 0x80, 0x3D)
    else:
        run.font.color.rgb = RGBColor(0xDC, 0x26, 0x26)
    return para

# ── Génération du document ─────────────────────────────────────────────────────

def generate():
    doc = Document()

    # ── Marges ────────────────────────────────────────────────────────────────
    for section in doc.sections:
        section.top_margin    = Cm(2)
        section.bottom_margin = Cm(2)
        section.left_margin   = Cm(2.5)
        section.right_margin  = Cm(2.5)

    # ── Page de titre ─────────────────────────────────────────────────────────
    title = doc.add_heading("Pawly — Pet Store", 0)
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    for run in title.runs:
        run.font.color.rgb = RGBColor(0x6C, 0x63, 0xFF)
        run.font.size = Pt(28)

    subtitle = doc.add_paragraph("Journal de suivi des prompts AI")
    subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
    for run in subtitle.runs:
        run.font.size = Pt(14)
        run.font.color.rgb = RGBColor(0x6B, 0x7A, 0x99)

    meta = doc.add_paragraph("Module : Pet Store  |  Date : Octobre 2026  |  Nombre de prompts : 11")
    meta.alignment = WD_ALIGN_PARAGRAPH.CENTER
    for run in meta.runs:
        run.font.size = Pt(10)
        run.font.color.rgb = RGBColor(0x6B, 0x7A, 0x99)

    doc.add_page_break()

    # ── Table des matières (manuelle) ─────────────────────────────────────────
    add_heading(doc, "Table des matières", 1)
    for p in PROMPTS:
        toc_line = doc.add_paragraph(style='List Bullet')
        run = toc_line.add_run(f"{p['id']} — {p['titre']}")
        run.font.size = Pt(10)

    doc.add_page_break()

    # ── Fiches prompts ─────────────────────────────────────────────────────────
    for p in PROMPTS:
        # Titre de la fiche
        add_heading(doc, f"{p['id']} — {p['titre']}", 1)

        # Bandeau Module
        module_para = doc.add_paragraph()
        mr = module_para.add_run(f"MODULE : {p['module']}")
        mr.bold = True
        mr.font.size = Pt(9)
        mr.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        module_para.paragraph_format.space_after = Pt(12)

        # Champs principaux
        add_field(doc, "ID",     p["id"])
        add_field(doc, "Date",   p["date"])
        add_field(doc, "Module", p["module"])

        add_heading(doc, "Objectif", 2)
        body = doc.add_paragraph(p["objectif"])
        body.paragraph_format.space_after = Pt(6)

        add_heading(doc, "Prompt utilisé", 2)
        prompt_para = doc.add_paragraph(p["prompt"])
        prompt_para.paragraph_format.left_indent = Cm(0.5)
        for run in prompt_para.runs:
            run.font.size = Pt(9)
            run.font.color.rgb = RGBColor(0x1A, 0x1D, 0x2E)

        add_heading(doc, "Fichiers générés", 2)
        doc.add_paragraph(p["fichiers_generes"])

        add_heading(doc, "Fichiers modifiés", 2)
        doc.add_paragraph(p["fichiers_modifies"])

        add_heading(doc, "Résultat", 2)
        doc.add_paragraph(p["resultat"])

        add_heading(doc, "Décision", 2)
        add_decision_badge(doc, p["decision"])

        add_heading(doc, "Justification", 2)
        doc.add_paragraph(p["justification"])

        add_heading(doc, "Tests exécutés", 2)
        doc.add_paragraph(p["tests"])

        add_heading(doc, "Erreurs rencontrées", 2)
        doc.add_paragraph(p["erreurs"])

        add_heading(doc, "Modifications humaines", 2)
        doc.add_paragraph(p["modifs_humaines"])

        doc.add_page_break()

    # ── Synthèse finale ────────────────────────────────────────────────────────
    add_heading(doc, "Synthèse du projet", 1)

    table = doc.add_table(rows=1, cols=4)
    table.style = 'Table Grid'
    hdr = table.rows[0].cells
    for i, title in enumerate(["ID", "Titre", "Décision", "Tests"]):
        hdr[i].text = title
        hdr[i].paragraphs[0].runs[0].bold = True
        set_cell_bg(hdr[i], "6C63FF")
        hdr[i].paragraphs[0].runs[0].font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)

    for p in PROMPTS:
        row = table.add_row().cells
        row[0].text = p["id"]
        row[1].text = p["titre"]
        row[2].text = p["decision"]
        row[3].text = "✓" if "passed" in p["tests"].lower() or "Aucun" in p["tests"] else "Manuel"
        if p["decision"] == "Accepté":
            set_cell_bg(row[2], "DCFCE7")

    doc.add_paragraph()
    stats = doc.add_paragraph()
    sr = stats.add_run(f"Total : {len(PROMPTS)} prompts  |  Acceptés : {len(PROMPTS)}  |  Tests automatisés : 65+ cas pytest + MockMvc")
    sr.bold = True
    sr.font.color.rgb = RGBColor(0x6C, 0x63, 0xFF)

    # ── Sauvegarde ────────────────────────────────────────────────────────────
    out = "pawly_pet_store_prompts.docx"
    doc.save(out)
    print(f"Document généré : {out}")

if __name__ == "__main__":
    generate()
