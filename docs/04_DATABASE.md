# CareQ — Database Schema (Day 16)

**Version:** 1.8 (Day 16)  
**Database:** MySQL 8.x

> **Day 16 schema additions** (applied automatically by Hibernate `ddl-auto: update`):
> - `push_subscriptions` — Web Push registrations, one row per browser/device (owned by notification-service). See § 9 below.
> - `notification_preferences` — per-user delivery preferences, one row per user (owned by notification-service). See § 10 below.

> **Day 13 schema addition** (applied automatically by Hibernate `ddl-auto: update`):
> - `notification_entries` — persisted in-app notifications, one row per consumed RabbitMQ queue event (owned by notification-service). See § 8 below.

> **Day 7a schema additions** (all applied automatically by Hibernate `ddl-auto: update`):
> - `user_profiles`: `full_name VARCHAR(255)`, `email VARCHAR(255)` — populated from JWT claims (forwarded as `X-User-Name`/`X-User-Email`) at profile creation, with a self-healing backfill on the next `/api/users/me` access.
> - `doctor_catalog_entries`: `name VARCHAR(255)` — the doctor's display name (sortable, searchable since Day 7a).
> - `queue_entries`: `patient_name VARCHAR(255)` — patient display name captured at join time (searchable by doctors).

---

## 1. Entity-Relationship Diagram (ASCII)

```
┌─────────────────┐          ┌─────────────────────┐
│     users       │          │   user_profiles      │
│ (auth-service)  │          │  (user-service)      │
├─────────────────┤          ├─────────────────────┤
│ id (PK, UUID)   │──┐       │ id (PK, BIGINT)     │
│ email (unique)   │  └───────│ user_id (unique)    │
│ password_hash    │   ref    │   (plain reference) │
│ full_name        │          │ phone               │
│ role (ENUM)      │          │ address             │
│ is_active        │          │ date_of_birth       │
│ created_at       │          │ gender              │
│ updated_at       │          │ profile_picture_url │
└─────────────────┘          │ role (denormalized) │
                              │ created_at          │
                              │ updated_at          │
                              └─────────────────────┘
                                      │
         ┌────────────────────────────┼────────────────────────────┐
         │                            │                            │
         ▼                            ▼                            ▼
┌─────────────────────┐       ┌──────────────────────────┐      ┌─────────────────────┐
│  doctor_catalog      │       │      departments          │      │   admin_profiles     │
│  _entries            │       │  (doctor-service)         │      │   (future)           │
│  (doctor-service)    │       ├──────────────────────────┤      └─────────────────────┘
├──────────────────────┤       │ id (PK, BIGINT)          │
│ id (PK, BIGINT)      │       │ name (unique)             │
│ user_id (unique)     │───────│ description               │
│ department_id        │  ref  │ is_active                 │
│ specialization       │       │ created_at                │
│ qualification        │       └──────────────────────────┘
│ experience_years     │
│ consultation_fee     │
│ avg_consultation_time│
│ _minutes             │
│ is_available         │
│ created_at           │
│ updated_at           │
└──────────────────────┘
```

> **Note:** `user_profiles` is a single unified profile table owned by `user-service`. This simpler design was chosen to avoid complex multi-table joins for the common "view my profile" use case. Role-specific detail tables (e.g., `doctor_profiles`) will still be added in later days for role-specific fields.

---

## 2. MySQL Schema (DDL)

```sql
CREATE DATABASE IF NOT EXISTS careq_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE careq_db;

-- ============================================================
-- 1. users — Shared authentication table with role column
-- ============================================================
CREATE TABLE users (
    id            CHAR(36)     PRIMARY KEY,              -- UUID
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,                 -- BCrypt hash
    full_name     VARCHAR(255) NOT NULL,
    role          ENUM('PATIENT', 'DOCTOR', 'ADMIN') NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_role (role),
    INDEX idx_users_email (email)
) ENGINE=InnoDB;

-- ============================================================
-- 2. user_profiles — Unified profile for all roles
--     Owned by user-service. userId is a plain reference to
--     auth-service's users.id (no FK constraint, microservice boundary).
--     Created lazily on first GET /api/users/me.
-- ============================================================
CREATE TABLE user_profiles (
    id                 BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id            CHAR(36)     NOT NULL UNIQUE,      -- Plain reference to users.id
    full_name          VARCHAR(255),                       -- Day 7a: from JWT claim (X-User-Name header)
    email              VARCHAR(255),                       -- Day 7a: from JWT claim (X-User-Email header)
    phone              VARCHAR(20),
    address            TEXT,
    date_of_birth      DATE,
    gender             VARCHAR(10),                       -- MALE, FEMALE, OTHER
    profile_picture_url VARCHAR(500),
    role               VARCHAR(20) NOT NULL,               -- Denormalized from users.role
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_profiles_role (role)
) ENGINE=InnoDB;

-- ============================================================
-- 3. patient_profiles — Extended profile for PATIENT role
-- ============================================================
CREATE TABLE patient_profiles (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id          CHAR(36)     NOT NULL UNIQUE,
    date_of_birth    DATE,
    blood_group      VARCHAR(5),                          -- e.g., A+, B-, O+
    phone            VARCHAR(20),
    address          TEXT,
    emergency_contact VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 4. admin_profiles — Extended profile for ADMIN role
-- ============================================================
CREATE TABLE admin_profiles (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id    CHAR(36)     NOT NULL UNIQUE,
    phone      VARCHAR(20),
    department VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 5. departments — Hospital departments (e.g., Cardiology)
--     Owned by doctor-service.
-- ============================================================
CREATE TABLE departments (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ============================================================
-- 6. doctor_catalog_entries — Doctor catalog-specific data
--     Owned by doctor-service.
--     userId is a plain reference to users.id (microservice boundary,
--     no FK constraint). DepartmentId is a plain reference to departments.id.
--     This is separate from user-service's user_profiles.
-- ============================================================
CREATE TABLE doctor_catalog_entries (
    id                          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id                     CHAR(36)     NOT NULL UNIQUE,
    name                        VARCHAR(255),              -- Day 7a: display name (sortable/searchable)
    department_id               BIGINT       NOT NULL,
    specialization              VARCHAR(255) NOT NULL,
    qualification               VARCHAR(500) NOT NULL,
    experience_years            INT          NOT NULL DEFAULT 0,
    consultation_fee            DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    avg_consultation_time_minutes INT        NOT NULL DEFAULT 15,
    is_available                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_doctor_user_id (user_id),
    INDEX idx_doctor_department (department_id),
    INDEX idx_doctor_specialization (specialization),
    INDEX idx_doctor_availability (is_available)
) ENGINE=InnoDB;

-- ============================================================
-- 7. queue_entries — Core queue tracking with AI triage (Day 5)
--     Owned by queue-service. patientId is a plain reference to
--     users.id; doctorCatalogEntryId is a plain reference to
--     doctor_catalog_entries.id (microservice boundary, no FKs).
--     avgConsultationTimeMinutes is NOT duplicated here — it is
--     fetched live from doctor-service via Feign (single source
--     of truth). Position and predicted wait are DERIVED on every
--     read and are never persisted.
-- ============================================================
CREATE TABLE queue_entries (
    id                       BIGINT       AUTO_INCREMENT PRIMARY KEY,
    patient_id               CHAR(36)     NOT NULL,          -- users.id (plain ref)
    patient_name             VARCHAR(255),                   -- Day 7a: captured at join, searchable by doctors
    doctor_catalog_entry_id  BIGINT       NOT NULL,          -- doctor_catalog_entries.id (plain ref)
    symptom_text             VARCHAR(2000) NOT NULL,
    ai_suggested_triage      ENUM('EMERGENCY', 'HIGH', 'NORMAL', 'FOLLOW_UP') NOT NULL DEFAULT 'NORMAL',
    doctor_override_triage   ENUM('EMERGENCY', 'HIGH', 'NORMAL', 'FOLLOW_UP') NULL,  -- final when set
    status                   ENUM('WAITING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'WAITING',
    joined_at                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    called_at                TIMESTAMP    NULL,
    completed_at             TIMESTAMP    NULL,
    INDEX idx_queue_patient (patient_id),
    INDEX idx_queue_doctor_status (doctor_catalog_entry_id, status),
    INDEX idx_queue_ai_triage (ai_suggested_triage),
    INDEX idx_queue_override (doctor_override_triage)
) ENGINE=InnoDB;

-- ============================================================
-- 8. notification_entries — Persisted in-app notifications (Day 13)
--     Owned by notification-service. Written by the RabbitMQ consumer
--     (one row per queue.joined|triaged|called|completed event), read by
--     GET /api/notifications/me. recipientUserId is a plain reference to
--     users.id (microservice boundary, no FK).
-- ============================================================
CREATE TABLE notification_entries (
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY,
    recipient_user_id   CHAR(36)     NOT NULL,          -- users.id (plain ref)
    type                VARCHAR(32)  NOT NULL,          -- queue.joined | queue.triaged | queue.called | queue.completed
    message             VARCHAR(500) NOT NULL,          -- human-readable, composed by the consumer
    is_read             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notification_recipient (recipient_user_id),
    INDEX idx_notification_created (created_at)
) ENGINE=InnoDB;

-- ============================================================
-- 9. push_subscriptions — Web Push registrations (Day 16)
--     Owned by notification-service. One row per browser/device,
--     written by POST /api/notifications/push/subscriptions and
--     deleted when the user unsubscribes or the push service
--     reports the endpoint dead (HTTP 404/410 — the consumer
--     self-cleans during delivery). endpoint is unique: the same
--     browser re-subscribing (or another user on a shared device)
--     upserts the row instead of duplicating it.
-- ============================================================
CREATE TABLE push_subscriptions (
    id                  BIGINT        AUTO_INCREMENT PRIMARY KEY,
    recipient_user_id   CHAR(36)      NOT NULL,          -- users.id (plain ref)
    endpoint            VARCHAR(1000) NOT NULL,          -- push service URL (FCM/APNs/Mozilla)
    p256dh              VARCHAR(255)  NOT NULL,          -- base64url client public key
    auth                VARCHAR(255)  NOT NULL,          -- base64url auth secret
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_push_endpoint (endpoint),
    INDEX idx_push_recipient (recipient_user_id)
) ENGINE=InnoDB;

-- ============================================================
-- 10. notification_preferences — Per-user delivery prefs (Day 16)
--     Owned by notification-service. One row per user (upsert by
--     PUT /api/notifications/preferences). A MISSING row means
--     "all defaults on" — web_push_enabled defaults to TRUE, so an
--     existing patient who never opened settings still receives
--     pushes once they grant the browser permission. The preference
--     is the user's opt-out switch; the browser permission is the
--     separate OS-level opt-in (both must be true for delivery).
-- ============================================================
CREATE TABLE notification_preferences (
    id                  BIGINT   AUTO_INCREMENT PRIMARY KEY,
    recipient_user_id   CHAR(36) NOT NULL,               -- users.id (plain ref)
    web_push_enabled    BOOLEAN  NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_pref_recipient (recipient_user_id)
) ENGINE=InnoDB;
```

---

### 2.x auth_tokens (Day 17 — email verification + password reset)

One-time security tokens for verification / reset. Only the **SHA-256 hash** of
the raw token is stored — the raw value travels in the email link only, so a
DB leak can't be replayed. An account is *pending verification* iff it has a
`VERIFY_EMAIL` row with `used_at IS NULL`; **legacy accounts (pre-Day-17) have
no rows and are treated as verified** — no migration needed.

```sql
CREATE TABLE auth_tokens (
    id         VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id    VARCHAR(36)  NOT NULL,          -- plain ref to users.id
    purpose    VARCHAR(20)  NOT NULL,          -- VERIFY_EMAIL | RESET_PASSWORD
    token_hash VARCHAR(64)  NOT NULL UNIQUE,   -- SHA-256 hex of the raw token
    expires_at DATETIME     NOT NULL,
    used_at    DATETIME     NULL,
    created_at DATETIME     NOT NULL
) ENGINE=InnoDB;
-- VERIFY_EMAIL: TTL 24h · RESET_PASSWORD: TTL 30 min; reissuing a token
-- deletes the previous one of the same purpose for that user.
```

---

## 3. Table Summary

| Table | Service Owner | Primary Key | Reference to users.id | Notes |
|-------|--------------|-------------|----------------------|-------|
| users | auth-service | id (UUID) | — | Auth table, JWT identity |
| auth_tokens | auth-service | id (UUID) | user_id (plain ref) | Day 17 — one-time verification/reset tokens (SHA-256 hash only); presence of an unused VERIFY_EMAIL row = account pending verification |
| user_profiles | user-service | id (BIGINT) | user_id (plain ref) | Lazy-created on first profile access |
| patient_profiles | user-service | id (BIGINT) | user_id (FK) | Future |
| admin_profiles | user-service | id (BIGINT) | user_id (FK) | Future |
| departments | doctor-service | id (BIGINT) | — | Day 4 — CRUD managed by Admin |
| doctor_catalog_entries | doctor-service | id (BIGINT) | user_id (plain ref) | Day 4 — Catalog data separate from user_profiles |
| queue_entries | queue-service | id (BIGINT) | patient_id, doctor_catalog_entry_id (plain refs) | Day 5 — AI triage + wait-time prediction; doctor consultation data NOT duplicated (fetched via Feign) |
| notification_entries | notification-service | id (BIGINT) | recipient_user_id (plain ref) | Day 13 — persisted in-app notifications, written by the RabbitMQ consumer |
| push_subscriptions | notification-service | id (BIGINT) | recipient_user_id (plain ref) | Day 16 — Web Push registrations, one per browser/device; self-cleaned on 404/410 during delivery |
| notification_preferences | notification-service | id (BIGINT) | recipient_user_id (plain ref) | Day 16 — per-user delivery prefs; missing row = defaults on |
