# CareQ — Database Schema (Day 4)

**Version:** 1.4 (Day 4)  
**Database:** MySQL 8.x

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
-- 7. queue_entries — Core queue tracking with AI predictions
-- ============================================================
CREATE TABLE queue_entries (
    id                      BIGINT       AUTO_INCREMENT PRIMARY KEY,
    patient_id              CHAR(36)     NOT NULL,
    doctor_id               CHAR(36)     NOT NULL,
    department_id           BIGINT,
    status                  ENUM('WAITING', 'IN_CONSULTATION', 'COMPLETED', 'CANCELLED')
                                        NOT NULL DEFAULT 'WAITING',
    position                INT          NOT NULL,
    triage_level            ENUM('CRITICAL', 'URGENT', 'NORMAL')
                                        NOT NULL DEFAULT 'NORMAL',
    triage_reason           VARCHAR(500),
    reported_symptoms       TEXT,
    predicted_wait_minutes  INT,
    actual_wait_minutes     INT,
    joined_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at              TIMESTAMP    NULL,
    completed_at            TIMESTAMP    NULL,
    FOREIGN KEY (patient_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (doctor_id)  REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE SET NULL,
    INDEX idx_queue_doctor_status (doctor_id, status),
    INDEX idx_queue_patient (patient_id),
    INDEX idx_queue_triage (triage_level),
    INDEX idx_queue_position (doctor_id, position)
) ENGINE=InnoDB;
```

---

## 3. Table Summary

| Table | Service Owner | Primary Key | Reference to users.id | Notes |
|-------|--------------|-------------|----------------------|-------|
| users | auth-service | id (UUID) | — | Auth table, JWT identity |
| user_profiles | user-service | id (BIGINT) | user_id (plain ref) | Lazy-created on first profile access |
| patient_profiles | user-service | id (BIGINT) | user_id (FK) | Future |
| admin_profiles | user-service | id (BIGINT) | user_id (FK) | Future |
| departments | doctor-service | id (BIGINT) | — | Day 4 — CRUD managed by Admin |
| doctor_catalog_entries | doctor-service | id (BIGINT) | user_id (plain ref) | Day 4 — Catalog data separate from user_profiles |
| queue_entries | queue-service | id (BIGINT) | patient_id, doctor_id (FK) | Day 5+ |
