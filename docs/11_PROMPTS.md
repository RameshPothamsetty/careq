# CareQ — Prompt Archive

## Day 1: Project Initialization

_Not archived — project scaffolding prompt._

---

## Day 2: Authentication Module

_Not archived — authentication module prompt._

---

## Day 3: User Module

**Prompt:** CareQ — Day 3 Prompt (User Module)

**Date Executed:** 2026-07-29

**Branch:** `feature/user-module`

**Summary:** Built user-service with lazy profile creation pattern. Created UserProfile entity, DTOs, repository, service (with lazy-create on first GET /me), controller (GET/PUT /me for own profile, GET /{id} admin-only), validation, and global exception handler. Added React ProfilePage with view/edit form, API service functions, and navigation links from all dashboards. Updated gateway routing (pre-existing), database docs, architecture docs, and testing docs.

---

---

## Day 4: Doctor / Department Module

**Prompt:** CareQ — Day 4 Prompt (Doctor / Department Module)

**Date Executed:** 2026-07-30

**Branch:** `feature/doctor-service`

**Summary:** Built doctor-service with Department and DoctorCatalogEntry entities, Admin CRUD for both, public doctor browsing with filters (departmentId, specialization), doctor availability toggle via header-based identity. Created docs/05_API_CONTRACT.md with full API contract. Updated React: AdminDashboard with management cards, AdminDepartmentManager (CRUD), AdminDoctorManager (CRUD), PatientDoctorBrowser (filterable listing), DoctorDashboard (availability toggle). Updated database (departments + doctor_catalog_entries tables), architecture (service responsibility), and testing docs.

## Full Prompt Text

```
<insert Day 4 prompt text here — paste the full prompt as given>
```
