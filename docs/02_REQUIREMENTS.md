# CareQ — Software Requirements Specification (SRS)

**Version:** 1.0  
**Date:** 2026-07  
**Author:** CareQ Engineering Team  
**Status:** Draft

---

## 1. Problem Statement

Hospital Outpatient Departments (OPDs) face chronic operational challenges: long and unpredictable wait times, manual paper-based triage that delays urgent cases, lack of real-time visibility for patients and doctors, and no centralized queue intelligence. Patients arrive, wait indefinitely, and leave without being seen. Doctors have no foresight into patient load or acuity. Administrators lack data to optimize resource allocation.

## 2. Business Objective

Build **CareQ** (branded as **SmartOPD AI**), an AI-powered OPD operations platform that:
- Predicts patient wait times using historical and live queue data
- Triages patients by urgency using rule-based AI logic
- Gives Patients, Doctors, and Admins a live, role-specific dashboard of queue operations

The goal is to reduce average patient wait time by 30% and ensure that no urgent case waits longer than 15 minutes without being flagged.

---

## 3. Locked MVP Scope (Verbatim)

### In Scope

**3 user roles:** PATIENT, DOCTOR, ADMIN

**Microservices:**
- `eureka-server` — service registry
- `api-gateway` — routing layer
- `auth-service` — signup/login, JWT issuance, role-based access
- `user-service` — Patient/Doctor/Admin profile management
- `doctor-service` — Doctor & Department catalog (create/browse/filter by specialty)
- `queue-service` — patient joins queue, AI-driven wait-time prediction, AI-driven symptom triage, queue status updates

**Frontend:** React app (patient view, doctor view, admin view)

**Database:** MySQL only (do not introduce MongoDB, Redis, or Kafka)

**Notifications:** polling-based only (no WebSockets)

### Explicitly Out of Scope

- AI load-balancing across doctors
- Standalone AI assistant chat feature
- RAG / vector search
- WebSocket real-time push
- Redis caching
- Receptionist role
- Payment/billing anything

> These items are tracked in the **Phase 2 Roadmap** and remain out of scope for the current release.
>
> **Update:** email verification and password reset were **added** (they were implicit gaps, not roadmap items). The remaining Phase 2 items above (AI load-balancing, AI chat, RAG, WebSockets, Redis caching, receptionist role, payments) are still out of scope.

---

## 4. Functional Requirements

### 4.1 auth-service

| ID | Requirement |
|----|------------|
| FR-01 | System shall allow a new user (Patient/Doctor/Admin) to register with email, password, name, and role |
| FR-02 | System shall authenticate users via email + password and issue a JWT token |
| FR-03 | System shall validate JWT tokens on every authenticated request |
| FR-04 | System shall enforce role-based access (PATIENT, DOCTOR, ADMIN) at the API Gateway |
| FR-04a | New registrations shall verify their email via a one-time link before they can sign in (config-gated; off in local dev, on in production) |
| FR-04b | System shall email a password-reset link for forgotten passwords and accept a new password via the one-time token |
| FR-04c | Public auth endpoints (login/signup/resend/forgot) shall be rate-limited to resist brute-force and abuse |

### 4.2 user-service

| ID | Requirement |
|----|------------|
| FR-05 | System shall allow users to view and update their own profile |
| FR-06 | System shall allow Admins to list, search, and deactivate any user |
| FR-07 | System shall store separate profile fields for Patients (age, blood group, phone) and Doctors (specialization, department, qualifications) |

### 4.3 doctor-service

| ID | Requirement |
|----|------------|
| FR-08 | System shall allow Admins to create and manage departments |
| FR-09 | System shall allow Admins to create and manage doctor profiles linked to departments |
| FR-10 | System shall allow patients to browse doctors filtered by specialty/department |
| FR-11 | System shall allow patients to view a doctor's available slots (future: queue status) |

### 4.4 queue-service

| ID | Requirement |
|----|------------|
| FR-12 | System shall allow a Patient to join the queue for a specific doctor |
| FR-13 | System shall assign each queue entry a triage level (Critical / Urgent / Normal) based on reported symptoms |
| FR-14 | System shall predict and display estimated wait time for each patient in the queue |
| FR-15 | System shall provide real-time (polling-based) queue status updates |
| FR-16 | System shall allow a Doctor to view their current queue and mark patients as "In Consultation" / "Completed" |
| FR-17 | System shall allow an Admin to view all queues across all doctors/departments |

---

## 5. Non-Functional Requirements

| ID | Requirement | Target |
|----|------------|--------|
| NFR-01 | **Performance** — API response time (P95) under 500ms for read endpoints | ≤ 500 ms |
| NFR-02 | **Performance** — JWT validation at gateway under 50ms | ≤ 50 ms |
| NFR-03 | **Availability** — System available 99% during business hours | 99% |
| NFR-04 | **Security** — Passwords hashed with BCrypt | BCrypt (strength 10) |
| NFR-05 | **Security** — All API traffic routed through API Gateway; direct service access blocked | Gateway-only |
| NFR-06 | **Usability** — Role-based UI adapts to PATIENT / DOCTOR / ADMIN after login | Role-driven routing |
| NFR-07 | **Maintainability** — Layered architecture per ADF standards | Enforced |
| NFR-08 | **Testability** — Unit test coverage ≥ 70% for service layer | ≥ 70% |

---

## 6. User Roles

### Patient
A Patient registers on the platform, browses doctors by department or specialty, joins a queue for a specific doctor, and views their position, estimated wait time, and triage status in real time. Patients can view their consultation history and update their profile.

### Doctor
A Doctor logs in to see a live queue of patients assigned to them. The queue displays each patient's name, triage level (color-coded), estimated wait time, and status. The Doctor can mark a patient as "In Consultation" when they begin the visit and "Completed" when the consultation ends.

### Admin
An Admin has a system-wide view. They can create and manage departments, add/edit doctor profiles, view all queues across all departments, monitor real-time load, and manage user accounts (activate/deactivate). Admins do not interact directly with patients in the queue.

---

## 7. User Stories

### 7.1 Patient Stories

**US-P-01: Register and Login**
> **As a** Patient  
> **I want to** register with my email and password, then log in  
> **So that** I can access the OPD platform and join queues

**Acceptance Criteria:**
- Given I am a new user, when I submit a valid registration form, then I receive a confirmation and can log in
- Given I have registered, when I log in with correct credentials, then I receive a JWT token
- Given I have registered, when I log in with wrong password, then I see an error message

**US-P-02: Browse Doctors**
> **As a** Patient  
> **I want to** browse doctors by specialty or department  
> **So that** I can choose the right doctor for my symptoms

**Acceptance Criteria:**
- Given I am logged in, when I navigate to the doctor list, then I see all available doctors grouped by department
- Given I am on the doctor list, when I select a specialty filter, then only doctors in that specialty are shown
- Given I select a doctor, when I tap "Join Queue", then I am added to that doctor's queue

**US-P-03: View Queue Status**
> **As a** Patient  
> **I want to** see my queue position, estimated wait time, and triage status  
> **So that** I can plan my time and know if I need urgent attention

**Acceptance Criteria:**
- Given I am in a queue, when I view my dashboard, then I see my position number and estimated wait time
- Given my symptoms are flagged as urgent, when I view my triage status, then I see a prominent "URGENT" label
- Given I am in a queue, when I refresh the page, then my queue position updates in real time (polling)

### 7.2 Doctor Stories

**US-D-01: View Queue**
> **As a** Doctor  
> **I want to** see my current patient queue sorted by triage priority  
> **So that** I can treat the most urgent patients first

**Acceptance Criteria:**
- Given I am logged in as a Doctor, when I view my dashboard, then I see all patients in my queue
- Given patients are in the queue, when the queue is displayed, then Critical patients appear first, then Urgent, then Normal
- Given a patient is marked as "In Consultation", when I check the queue, then that patient's status is updated

**US-D-02: Update Patient Status**
> **As a** Doctor  
> **I want to** mark a patient as "In Consultation" and later "Completed"  
> **So that** the queue advances and wait times update correctly

**Acceptance Criteria:**
- Given I am viewing my queue, when I click "Start" on a patient, then their status changes to "In Consultation"
- Given a patient is "In Consultation", when I click "Complete", then their status changes to "Completed" and they leave the queue
- Given a patient is completed, when the queue recalculates, then the next patient's wait time updates

**US-D-03: View Triage Info**
> **As a** Doctor  
> **I want to** see each patient's triage level and reported symptoms  
> **So that** I can prepare for the consultation beforehand

**Acceptance Criteria:**
- Given I select a patient in my queue, when I expand the patient card, then I see their reported symptoms
- Given a patient has a triage level, when I view the card, then I see the triage badge (color-coded)

### 7.3 Admin Stories

**US-A-01: Manage Departments**
> **As an** Admin  
> **I want to** create, edit, and deactivate departments  
> **So that** the hospital's department structure stays up to date

**Acceptance Criteria:**
- Given I am logged in as Admin, when I create a department with name and description, then it appears in the department list
- Given a department exists, when I edit its name, then the change reflects immediately
- Given a department is no longer active, when I deactivate it, then doctors assigned to it are flagged

**US-A-02: Manage Doctors**
> **As an** Admin  
> **I want to** register new doctors, assign them to departments, and update their details  
> **So that** the doctor catalog is always accurate

**Acceptance Criteria:**
- Given I am logged in as Admin, when I add a new doctor with name, specialty, and department, then the doctor appears in the catalog
- Given a doctor exists, when I update their department assignment, then the change reflects in the doctor list
- Given a doctor leaves the hospital, when I deactivate them, then they no longer accept new queue entries

**US-A-03: Monitor All Queues**
> **As an** Admin  
> **I want to** see a dashboard of all queues across all departments in real time  
> **So that** I can identify bottlenecks and reallocate resources

**Acceptance Criteria:**
- Given I am logged in as Admin, when I view the dashboard, then I see all doctors grouped by department with queue lengths
- Given a department has long wait times, when I view the dashboard, then I see a visual alert
- Given I click on a doctor's queue, then I expand to see all patients in that queue with triage levels

---

## 8. Out of Scope (Phase 2 Roadmap)

| Feature | Rationale |
|---------|-----------|
| AI load-balancing across doctors | Requires advanced ML model — Phase 2 |
| Standalone AI assistant chat | Not part of core OPD flow |
| RAG / vector search | Infrastructure-heavy, not needed for MVP |
| WebSocket real-time push | Adds complexity — polling suffices for MVP |
| Redis caching | Performance optimization, not required for MVP scale |
| Receptionist role | Role scope creep — MVP limits to 3 roles |
| Payment/billing | Out of scope for OPD operations |
