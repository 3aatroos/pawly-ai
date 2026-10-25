- ─────────────────────────────────────────────────────────────
-- Pawly Database Schema
-- MySQL 8.0+
-- ─────────────────────────────────────────────────────────────

CREATE DATABASE IF NOT EXISTS pawly_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE pawly_db;

-- ── Users ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
  id            BIGINT          NOT NULL AUTO_INCREMENT,
  email         VARCHAR(255)    NOT NULL,
  password_hash VARCHAR(255)    NOT NULL,
  first_name    VARCHAR(100)    NOT NULL,
  last_name     VARCHAR(100)    NOT NULL,
  phone         VARCHAR(30),
  address       VARCHAR(500),
  photo_url     VARCHAR(500),
  role          ENUM('PET_OWNER','VETERINARIAN','ADMINISTRATOR') NOT NULL DEFAULT 'PET_OWNER',
  status        ENUM('ACTIVE','INACTIVE','PENDING')              NOT NULL DEFAULT 'ACTIVE',
  created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Pets ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS pets (
  id            BIGINT          NOT NULL AUTO_INCREMENT,
  owner_id      BIGINT          NOT NULL,
  name          VARCHAR(100)    NOT NULL,
  species       VARCHAR(50)     NOT NULL,
  breed         VARCHAR(100),
  date_of_birth DATE,
  gender        ENUM('MALE','FEMALE','UNKNOWN') NOT NULL DEFAULT 'UNKNOWN',
  photo_url     VARCHAR(500),
  health_info   TEXT,
  weight_kg     DECIMAL(5,2),
  color         VARCHAR(100),
  microchip_id  VARCHAR(100),
  is_vaccinated BOOLEAN         NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_pets_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Vet Profiles ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS vet_profiles (
  id                    BIGINT          NOT NULL AUTO_INCREMENT,
  user_id               BIGINT          NOT NULL,
  specialization        VARCHAR(200)    NOT NULL,
  experience_years      INT             NOT NULL DEFAULT 0,
  clinic_name           VARCHAR(200),
  clinic_address        VARCHAR(500),
  city                  VARCHAR(100),
  working_hours         VARCHAR(200),
  services              TEXT,
  license_number        VARCHAR(100),
  bio                   TEXT,
  is_available          BOOLEAN         NOT NULL DEFAULT TRUE,
  verification_status   ENUM('PENDING','VERIFIED','REJECTED') NOT NULL DEFAULT 'PENDING',
  verified_at           TIMESTAMP,
  rejection_reason      TEXT,
  created_at            TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_vet_user (user_id),
  CONSTRAINT fk_vet_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Notifications ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notifications (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  user_id     BIGINT        NOT NULL,
  title       VARCHAR(300)  NOT NULL,
  message     TEXT          NOT NULL,
  type        ENUM('INFO','WARNING','EMERGENCY','SYSTEM','REMINDER') NOT NULL DEFAULT 'INFO',
  is_read     BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  INDEX idx_notif_user_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Pet-Vet Assignment Migration ──────────────────────────────
-- Idempotent: safe to run on every startup (spring.sql.init.mode=always).
-- Adds assigned_vet_id if the column does not already exist, then adds
-- the FK constraint and index each only if absent.

SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'pets'
    AND COLUMN_NAME  = 'assigned_vet_id'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE pets ADD COLUMN assigned_vet_id BIGINT NULL',
  'SELECT 1 -- col already exists'
);
PREPARE _stmt FROM @sql; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;

SET @fk_exists = (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME         = 'pets'
    AND CONSTRAINT_NAME    = 'fk_pets_assigned_vet'
);
SET @sql = IF(@fk_exists = 0,
  'ALTER TABLE pets ADD CONSTRAINT fk_pets_assigned_vet FOREIGN KEY (assigned_vet_id) REFERENCES vet_profiles (id) ON DELETE SET NULL',
  'SELECT 1 -- fk already exists'
);
PREPARE _stmt FROM @sql; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;

SET @idx_exists = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'pets'
    AND INDEX_NAME   = 'idx_pets_assigned_vet'
);
SET @sql = IF(@idx_exists = 0,
  'CREATE INDEX idx_pets_assigned_vet ON pets (assigned_vet_id)',
  'SELECT 1 -- index already exists'
);
PREPARE _stmt FROM @sql; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;

-- ── Seed: default admin account ───────────────────────────────
-- Password: Admin@123  (BCrypt hash)
INSERT IGNORE INTO users (email, password_hash, first_name, last_name, phone, role, status)
VALUES (
  'admin@pawly.com',
  '$2a$12$3NkzfGhGI3t6kXpCVv.JiO2kJC/F.xjVJK4cHK3NJbOIWtETZEfpC',
  'Rached',
  'Trabelsi',
  '+216 55 123 456',
  'ADMINISTRATOR',
  'ACTIVE'
);

-- -- Knowledge Chunks for RAG ------------------------------------
-- Stores extracted knowledge from PDFs for RAG retrieval.
-- Idempotent: safe to run on every startup.

SET @table_exists = (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'knowledge_chunks'
);

SET @sql = IF(@table_exists = 0,
  'CREATE TABLE knowledge_chunks (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    topic             VARCHAR(255)    NOT NULL COMMENT "Section/topic from the knowledge base",
    species           VARCHAR(100)    COMMENT "Pet species (e.g., dog, cat, bird, rabbit)",
    urgency_level     VARCHAR(50)     COMMENT "CRITICAL, URGENT, MODERATE, MILD",
    keywords          TEXT            COMMENT "Comma-separated keywords for search",
    content_text      LONGTEXT        NOT NULL COMMENT "Main chunk text",
    source            VARCHAR(255)    NOT NULL DEFAULT "Pet Emergency Triage KB" COMMENT "Source document",
    reviewed_date     TIMESTAMP       COMMENT "Last review/verification date",
    metadata_json     JSON            COMMENT "Additional metadata as JSON object",
    created_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_topic (topic),
    INDEX idx_species (species),
    INDEX idx_urgency (urgency_level),
    FULLTEXT INDEX idx_content_ft (content_text)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci',
  'SELECT 1 -- table already exists'
);

PREPARE _stmt FROM @sql; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;
<<<<<<< HEAD


-- ═══════════════════════════════════════════════════════════════
-- Pet Store Module
-- ═══════════════════════════════════════════════════════════════

-- ── Product Categories ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS product_categories (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100)  NOT NULL,
  description TEXT,
  image_url   VARCHAR(500),
  is_active   BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_category_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Products ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS products (
  id              BIGINT          NOT NULL AUTO_INCREMENT,
  category_id     BIGINT          NOT NULL,
  created_by_id   BIGINT          NOT NULL,
  name            VARCHAR(200)    NOT NULL,
  description     TEXT,
  price           DECIMAL(10,2)   NOT NULL,
  stock_quantity  INT             NOT NULL DEFAULT 0,
  is_available    BOOLEAN         NOT NULL DEFAULT TRUE,
  average_rating  DECIMAL(3,2)    NOT NULL DEFAULT 0.00,
  review_count    INT             NOT NULL DEFAULT 0,
  target_species  VARCHAR(50)     COMMENT 'dog | cat | bird | rabbit | all',
  version         BIGINT          NOT NULL DEFAULT 0 COMMENT 'Optimistic locking',
  created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_product_category   FOREIGN KEY (category_id)   REFERENCES product_categories (id) ON DELETE RESTRICT,
  CONSTRAINT fk_product_created_by FOREIGN KEY (created_by_id) REFERENCES users (id)              ON DELETE RESTRICT,
  INDEX idx_product_category    (category_id),
  INDEX idx_product_available   (is_available),
  INDEX idx_product_species     (target_species),
  INDEX idx_product_created_by  (created_by_id),
  FULLTEXT INDEX idx_product_search (name, description)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Product Images ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS product_images (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  product_id  BIGINT        NOT NULL,
  image_url   VARCHAR(500)  NOT NULL,
  is_primary  BOOLEAN       NOT NULL DEFAULT FALSE,
  sort_order  INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_image_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
  INDEX idx_product_image_product (product_id),
  INDEX idx_product_image_primary (product_id, is_primary)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Product Reviews ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS product_reviews (
  id          BIGINT    NOT NULL AUTO_INCREMENT,
  product_id  BIGINT    NOT NULL,
  user_id     BIGINT    NOT NULL,
  rating      TINYINT   NOT NULL COMMENT 'Value between 1 and 5',
  comment     TEXT,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_review_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
  CONSTRAINT fk_review_user    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE CASCADE,
  UNIQUE KEY uq_review_user_product (user_id, product_id),
  INDEX idx_review_product (product_id),
  INDEX idx_review_user    (user_id),
  INDEX idx_review_rating  (rating),
  CONSTRAINT chk_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Cart Items ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cart_items (
  id          BIGINT    NOT NULL AUTO_INCREMENT,
  user_id     BIGINT    NOT NULL,
  product_id  BIGINT    NOT NULL,
  quantity    INT       NOT NULL DEFAULT 1,
  added_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_cart_user    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE CASCADE,
  CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
  UNIQUE KEY uq_cart_user_product (user_id, product_id),
  INDEX idx_cart_user (user_id),
  CONSTRAINT chk_cart_quantity CHECK (quantity >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Orders ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS orders (
  id                    BIGINT         NOT NULL AUTO_INCREMENT,
  buyer_id              BIGINT         NOT NULL,
  status                ENUM('CONFIRMED','PREPARING','SHIPPED','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','CANCELLED')
                                       NOT NULL DEFAULT 'CONFIRMED',
  payment_method        ENUM('STRIPE','CASH_ON_DELIVERY')
                                       NOT NULL,
  total_amount          DECIMAL(10,2)  NOT NULL,
  shipping_fee          DECIMAL(6,2)   NOT NULL DEFAULT 0.00,
  shipping_address      TEXT           NOT NULL,
  shipping_city         VARCHAR(100),
  shipping_country      VARCHAR(100)   NOT NULL DEFAULT 'TN',
  notes                 TEXT,
  created_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  -- RESTRICT : ne pas supprimer un user qui a des commandes
  CONSTRAINT fk_order_buyer FOREIGN KEY (buyer_id) REFERENCES users (id) ON DELETE RESTRICT,
  INDEX idx_order_buyer   (buyer_id),
  INDEX idx_order_status  (status),
  INDEX idx_order_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Order Items ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS order_items (
  id            BIGINT         NOT NULL AUTO_INCREMENT,
  order_id      BIGINT         NOT NULL,
  product_id    BIGINT,
  product_name  VARCHAR(200)   NOT NULL COMMENT 'Snapshot at purchase time',
  quantity      INT            NOT NULL,
  unit_price    DECIMAL(10,2)  NOT NULL COMMENT 'Price frozen at order creation',
  PRIMARY KEY (id),
  CONSTRAINT fk_order_item_order   FOREIGN KEY (order_id)   REFERENCES orders (id)   ON DELETE CASCADE,
  -- SET NULL : conserver la ligne d'historique si le produit est supprimé
  CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE SET NULL,
  INDEX idx_order_item_order   (order_id),
  INDEX idx_order_item_product (product_id),
  CONSTRAINT chk_order_item_qty CHECK (quantity >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Payments ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payments (
  id                        BIGINT         NOT NULL AUTO_INCREMENT,
  order_id                  BIGINT         NOT NULL,
  status                    ENUM('PENDING','SUCCEEDED','FAILED','CANCELLED','REFUNDED')
                                           NOT NULL DEFAULT 'PENDING',
  amount                    DECIMAL(10,2)  NOT NULL,
  currency                  CHAR(3)        NOT NULL DEFAULT 'EUR',
  stripe_payment_intent_id  VARCHAR(255),
  stripe_client_secret      VARCHAR(500),
  stripe_charge_id          VARCHAR(255),
  failure_reason            TEXT,
  paid_at                   TIMESTAMP,
  created_at                TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
  UNIQUE KEY uq_payment_order (order_id),
  INDEX idx_payment_stripe_intent (stripe_payment_intent_id),
  INDEX idx_payment_status        (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Recommendations ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS recommendations (
  id          BIGINT         NOT NULL AUTO_INCREMENT,
  user_id     BIGINT         NOT NULL,
  product_id  BIGINT         NOT NULL,
  score       DECIMAL(5,4)   NOT NULL DEFAULT 0.0000,
  reason      VARCHAR(200),
  created_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_reco_user    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE CASCADE,
  CONSTRAINT fk_reco_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
  UNIQUE KEY uq_recommendation_user_product (user_id, product_id),
  INDEX idx_recommendation_user  (user_id),
  INDEX idx_recommendation_score (user_id, score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Product Views ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS product_views (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  product_id  BIGINT       NOT NULL,
  user_id     BIGINT,
  session_id  VARCHAR(100),
  viewed_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_view_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
  CONSTRAINT fk_view_user    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE SET NULL,
  INDEX idx_view_product   (product_id),
  INDEX idx_view_user      (user_id),
  INDEX idx_view_viewed_at (viewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Product Purchase History ──────────────────────────────────
CREATE TABLE IF NOT EXISTS product_purchase_history (
  id            BIGINT         NOT NULL AUTO_INCREMENT,
  user_id       BIGINT         NOT NULL,
  product_id    BIGINT,
  order_id      BIGINT         NOT NULL,
  product_name  VARCHAR(200)   NOT NULL COMMENT 'Snapshot at purchase time',
  quantity      INT            NOT NULL,
  unit_price    DECIMAL(10,2)  NOT NULL,
  purchased_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_purchase_user    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE CASCADE,
  CONSTRAINT fk_purchase_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE SET NULL,
  CONSTRAINT fk_purchase_order   FOREIGN KEY (order_id)   REFERENCES orders (id)   ON DELETE CASCADE,
  INDEX idx_purchase_user     (user_id),
  INDEX idx_purchase_product  (product_id),
  INDEX idx_purchase_order    (order_id),
  INDEX idx_purchase_date     (purchased_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Seed : default product categories ────────────────────────
INSERT IGNORE INTO product_categories (name, description, is_active) VALUES
  ('Food',       'Pet food and treats',                       TRUE),
  ('Toys',       'Toys and entertainment for pets',           TRUE),
  ('Accessories','Collars, leashes, harnesses and more',      TRUE),
  ('Hygiene',    'Shampoos, grooming and hygiene products',   TRUE),
  ('Health',     'Vitamins, supplements and health products', TRUE),
  ('Bedding',    'Beds, blankets and comfort items',          TRUE);

-- ── Adoption feed ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS adoption_posts (
  id                   BIGINT        NOT NULL AUTO_INCREMENT,
  pet_id               BIGINT,
  author_id            BIGINT        NOT NULL,
  status               VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',
  title                VARCHAR(120)  NOT NULL,
  animal_name          VARCHAR(80),
  shelter_notes        TEXT          NOT NULL,
  description          TEXT,
  species              VARCHAR(50),
  breed                VARCHAR(100),
  age_label            VARCHAR(80),
  gender               VARCHAR(20),
  personality          VARCHAR(500),
  special_needs        VARCHAR(500),
  area_label           VARCHAR(80),
  photo_url            VARCHAR(500),
  photo_description    VARCHAR(2000),
  ai_draft             TEXT,
  status_changed_by_id BIGINT,
  adopted_at           DATETIME,
  created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_adoption_post_pet FOREIGN KEY (pet_id) REFERENCES pets (id) ON DELETE SET NULL,
  CONSTRAINT fk_adoption_post_author FOREIGN KEY (author_id) REFERENCES users (id),
  CONSTRAINT fk_adoption_post_status_user FOREIGN KEY (status_changed_by_id) REFERENCES users (id),
  INDEX idx_adoption_posts_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS adoption_comments (
  id         BIGINT        NOT NULL AUTO_INCREMENT,
  post_id    BIGINT        NOT NULL,
  author_id  BIGINT        NOT NULL,
  body       VARCHAR(1000) NOT NULL,
  created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_adoption_comment_post FOREIGN KEY (post_id) REFERENCES adoption_posts (id) ON DELETE CASCADE,
  CONSTRAINT fk_adoption_comment_author FOREIGN KEY (author_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS adoption_favorites (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  post_id    BIGINT   NOT NULL,
  user_id    BIGINT   NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uq_adoption_favorite_user_post UNIQUE (user_id, post_id),
  CONSTRAINT fk_adoption_favorite_post FOREIGN KEY (post_id) REFERENCES adoption_posts (id) ON DELETE CASCADE,
  CONSTRAINT fk_adoption_favorite_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS adoption_applications (
  id                   BIGINT       NOT NULL AUTO_INCREMENT,
  post_id              BIGINT       NOT NULL,
  applicant_id         BIGINT       NOT NULL,
  status               VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED',
  housing              VARCHAR(80),
  has_garden           BOOLEAN,
  hours_away_per_day   INT,
  other_pets           BOOLEAN,
  children_at_home     BOOLEAN,
  experience           VARCHAR(80),
  motivation           TEXT,
  reviewer_summary     TEXT,
  points_to_verify     TEXT,
  suggested_attention  VARCHAR(10),
  reviewed_by_id       BIGINT,
  decision_note        VARCHAR(1000),
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uq_adoption_application_post_user UNIQUE (post_id, applicant_id),
  CONSTRAINT fk_adoption_application_post FOREIGN KEY (post_id) REFERENCES adoption_posts (id) ON DELETE CASCADE,
  CONSTRAINT fk_adoption_application_user FOREIGN KEY (applicant_id) REFERENCES users (id),
  CONSTRAINT fk_adoption_application_reviewer FOREIGN KEY (reviewed_by_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
=======
>>>>>>> 8bbbdb2a74dc7243fdcc74b4d1d4082519058a91
