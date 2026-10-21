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
