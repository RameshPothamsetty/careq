# CareQ — Database Schema (Initial)

**Version:** 1.0 (Day 1)  
**Database:** MySQL 8.x

---

## 1. Entity-Relationship Diagram (ASCII)

```
┌─────────────────┐          ┌─────────────────────┐
│     users       │          │   patient_profiles   │
├─────────────────┤          ├─────────────────────┤
│ id (PK, UUID)   │──┐       │ id (PK)             │
│ email (unique)   │  │       │ user_id (FK → users)│
│ password_hash    │  │       │ date_of_birth       │
│ full_name        │  │       │ blood_group         │
│ role (ENUM)      │  │       │ phone               │
│ is_active        │  │       │ address             │
│ created_at       │  │       │ emergency_contact   │
│ updated_at       │  │       └─────────────────────┘
└─────────────────┘  │       
                      │       ┌─────────────────────┐
                      │       │   admin_profiles     │
                      ├──┐    ├─────────────────────┤
                      │  │    │ id (PK)             │
                      │  │    │ user_id (FK → users)│
                      │  │    │ phone               │
                      │  │    │ department          │
                      │  │    └─────────────────────┘
                      │  │
                      │  │    ┌─────────────────────┐
                      │  └────│   doctor_profiles    │
                      │       ├─────────────────────┤
                      │       │ id (PK)             │
                      │       │ user_id (FK → users)│
                      │       │ specialization       │
                      │       │ qualifications       │
                      │       │ experience_years     │
                      │       │ department_id(FK→dept)│
                      │       │ consultation_fee     │
                      │       │ is_available         │
                      │       └─────────────────────┘
                      │
                      │       ┌─────────────────────┐
                      │       │   departments        │
                      │       ├─────────────────────┤
                      │       │ id (PK)             │
                      │       │ name (unique)        │
                      │       │ description          │
                      │       │ is_active            │
                      │       └─────────────────────┘
                      │
                      │       ┌──────────────────────────┐
                      └───────│     queue_entries         │
                              ├──────────────────────────┤
                              │ id (PK)                  │
                              │ patient_id (FK → users)  │
                              │ doctor_id (FK → users)   │
                              │ department_id (FK→dept)  │
                              │ status (ENUM)            │
                              │ position                 │
                              │ triage_level (ENUM)      │
                              │ triage_reason            │
                              │ reported_symptoms        │
                              │ predicted_wait_minutes   │
                              │ actual_wait_minutes      │
                              │ joined_at                │
                              │ started_at               │
                              │ completed_at             │
                              └──────────────────────────┘
```

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
-- 2. patient_profiles — Extended profile for PATIENT role
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
-- 3. admin_profiles — Extended profile for ADMIN role
-- ============================================================
CREATE TABLE admin_profiles (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id    CHAR(36)     NOT NULL UNIQUE,
    phone      VARCHAR(20),
    department VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 4. departments — Hospital departments (e.g., Cardiology)
-- ============================================================
CREATE TABLE departments (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ============================================================
-- 5. doctor_profiles — Extended profile for DOCTOR role
-- ============================================================
CREATE TABLE doctor_profiles (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id          CHAR(36)     NOT NULL UNIQUE,
    specialization   VARCHAR(255) NOT NULL,
    qualifications   TEXT,                                -- Comma-separated or JSON
    experience_years INT          DEFAULT 0,
    department_id    BIGINT,
    consultation_fee DECIMAL(10,2) DEFAULT 0.00,
    is_available     BOOLEAN      NOT NULL DEFAULT TRUE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE SET NULL,
    INDEX idx_doctor_specialization (specialization),
    INDEX idx_doctor_department (department_id)
) ENGINE=InnoDB;

-- ============================================================
-- 6. queue_entries — Core queue tracking with AI predictions
-- ============================================================
CREATE TABLE queue_entries (
    id                      BIGINT       AUTO_INCREMENT PRIMARY KEY,
    patient_id              CHAR(36)     NOT NULL,
    doctor_id               CHAR(36)     NOT NULL,
    department_id           BIGINT,
    status                  ENUM('WAITING', 'IN_CONSULTATION', 'COMPLETED', 'CANCELLED')
                                        NOT NULL DEFAULT 'WAITING',
    position                INT          NOT NULL,        -- 1-based position in queue
    triage_level            ENUM('CRITICAL', 'URGENT', 'NORMAL')
                                        NOT NULL DEFAULT 'NORMAL',
    triage_reason           VARCHAR(500),                 -- Why this triage level was assigned
    reported_symptoms       TEXT,                          -- Free-text symptoms from patient
    predicted_wait_minutes  INT,                           -- AI-predicted wait time in minutes
    actual_wait_minutes     INT,                           -- Actual wait (filled after start)
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

## 3. Column Rationale: `queue_entries`

### `triage_level` (ENUM: CRITICAL / URGENT / NORMAL)

**Why stored as a column:**
- The triage level is computed once when the patient joins the queue based on their reported symptoms using a rule-based AI engine.
- Storing it as a column (rather than computing it on every read) allows:
  - Fast sorting/filtering by urgency on the Doctor's dashboard
  - Historical audit of triage decisions
  - Admin reporting on acuity distribution
- The value is immutable once assigned (can only be overridden by a doctor/admin manually).
- Indexing this column enables efficient queries like "show all CRITICAL patients across all queues."

### `predicted_wait_minutes` (INT)

**Why stored as a column:**
- The predicted wait time is computed when the patient joins the queue based on:
  - Number of patients ahead with their triage levels
  - Average consultation time per doctor (historical)
  - Triage-based priority weighting
- Storing the predicted value allows:
  - The patient to see their estimated wait time without recomputation
  - Historical accuracy analysis (compare predicted vs. actual)
  - The value is updated periodically (e.g., every 30 seconds via a scheduled task or on queue state change) and pushed to the UI via polling
- `actual_wait_minutes` is filled later when consultation starts, enabling accuracy benchmarking.

---

## 4. Entity Relationship Summary

| Table | Primary Key | Foreign Keys | Indexes |
|-------|-------------|-------------|---------|
| users | id (UUID) | — | email, role |
| patient_profiles | id (BIGINT) | user_id → users.id | user_id (unique) |
| admin_profiles | id (BIGINT) | user_id → users.id | user_id (unique) |
| departments | id (BIGINT) | — | name (unique) |
| doctor_profiles | id (BIGINT) | user_id → users.id, department_id → departments.id | specialization, department_id |
| queue_entries | id (BIGINT) | patient_id → users.id, doctor_id → users.id, department_id → departments.id | doctor_id+status, patient_id, triage_level, position |
