# CareQ — API Contract

**Version:** 1.9
**Status:** Added Phase 2 endpoints — `GET /api/doctors/search` (ranked), `POST /api/queue/chat` (AI assistant), `/api/bills/**` (sandbox billing); WebSocket STOMP at `/ws` (notification-service)

---

## Identity Headers (all services)

The API Gateway validates the JWT and forwards these headers to downstream services — services never re-parse the token:

| Header | Source claim | Notes |
|--------|-------------|-------|
| `X-User-Id` | `sub` | Always present on authenticated routes |
| `X-User-Role` | `role` | Always present on authenticated routes |
| `X-User-Name` | `fullName` | Added with the name-plumbing feature — present on all current tokens |
| `X-User-Email` | `email` | Identity header |

---

## Shared Error Response Shape

Every CareQ service returns errors in this exact shape (enforced by each service's `GlobalExceptionHandler`):

```json
{
  "timestamp": "2026-07-30T10:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Department not found with id: 999",
  "path": "/api/departments/999",
  "validationErrors": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| `timestamp` | string | When the error occurred (ISO local date-time) |
| `status` | int | HTTP status code |
| `error` | string | Short HTTP status reason phrase |
| `message` | string | Human-readable error message |
| `path` | string | Request path that produced the error |
| `validationErrors` | array \| null | Field-level failures (`{ field, message }` pairs); present only on 400 validation errors (replaces the pre-Day-9 `details` list) |

> **Consistency rules:** all 400 validation errors use `validationErrors`; all 404s use "Not Found"; all duplicate resources use **409 Conflict**.

---

## User Endpoints (`/api/users`)

### 0. My Profile (own profile, lazy-created)

```
GET /api/users/me
PUT /api/users/me
POST /api/users/me/profile-picture
```

**Auth:** Any authenticated role.

**Description (GET):** Returns the calling user's profile. A default empty profile is **lazily created on first access**, so this endpoint always succeeds. Identity comes from the `X-User-Id` / `X-User-Role` headers set by the gateway.

**Description (PUT):** Partial update — only non-null fields are changed. Fields: `phone`, `address`, `dateOfBirth`, `gender`, `profilePictureUrl`.

**Description (POST):** Multipart upload (`file` part, `image/*` only). Stores the file and returns the profile with the new `profilePictureUrl`.

**Success Response (200):** `UserProfileResponseDto`:
```json
{
  "id": 1,
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "fullName": "John Patient",
  "email": "john@careq.com",
  "phone": "+919876543210",
  "address": null,
  "dateOfBirth": null,
  "gender": "MALE",
  "profilePictureUrl": null,
  "role": "PATIENT"
}
```

**Error Responses:** `400` (validation failed / missing identity header).

### 0b. Serve a Profile Picture (public)

```
GET /api/users/profile-pictures/{filename}
```

**Auth:** None — whitelisted at the gateway so `<img>` tags can load pictures. Returns the image file; `404` when missing.

### 0c. Get Any User's Profile (Admin only)

```
GET /api/users/{id}
```

**Auth:** ADMIN.

**Path Variables:** `id` = target user UUID.

**Error Responses:** `403` (non-admin), `404` (no profile for that user).

### 0a. List Users (Admin only)

```
GET /api/users
```

**Auth:** ADMIN

**Description:** Paginated list of all user profiles, optionally filtered by a case-insensitive substring search on display name or email. This was the missing counterpart to `GET /api/users/{id}` — it gives the Admin an actual user-management view.

**Query Parameters (all optional):**
| Name | Type | Default | Description |
|------|------|---------|-------------|
| search | String | — | Case-insensitive match on `fullName` or `email` |
| page | int | 0 | Zero-based page number |
| size | int | 20 | Page size (clamped to max 100) |

**Success Response (200):**
```json
{
  "content": [
    {
      "id": 1,
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "fullName": "John Patient",
      "email": "john@careq.com",
      "phone": "+919876543210",
      "address": null,
      "dateOfBirth": null,
      "gender": "MALE",
      "profilePictureUrl": null,
      "role": "PATIENT"
    }
  ],
  "totalElements": 12,
  "totalPages": 1,
  "number": 0,
  "size": 20,
  "first": true,
  "last": true,
  "empty": false
}
```

**Error Responses:** `403` (non-admin), `401` (no/invalid JWT).

---

## Department Endpoints (`/api/departments`)

All department endpoints require **ADMIN** role unless marked otherwise.

---

### 1. List All Departments

```
GET /api/departments
```

**Auth:** ADMIN (all authenticated roles can list — public/patient friendly)

**Description:** Returns a list of all departments, including active and inactive ones.

**Query Parameters:** None

**Success Response (200):**
```json
[
  {
    "id": 1,
    "name": "Cardiology",
    "description": "Heart and cardiovascular system",
    "isActive": true
  },
  {
    "id": 2,
    "name": "Neurology",
    "description": "Brain and nervous system",
    "isActive": true
  }
]
```

**Error Response:** None

---

### 2. Get Department by ID

```
GET /api/departments/{id}
```

**Auth:** All authenticated roles

**Path Variables:**
| Name | Type | Description |
|------|------|-------------|
| id | Long | Department ID |

**Success Response (200):**
```json
{
  "id": 1,
  "name": "Cardiology",
  "description": "Heart and cardiovascular system",
  "isActive": true
}
```

**Error Response (404):**
```json
{
  "timestamp": "2026-07-30T10:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Department not found with id: 999",
  "path": "/api/departments/999",
  "validationErrors": null
}
```

---

### 3. Create Department (Admin only)

```
POST /api/departments
```

**Auth:** ADMIN

**Request Body:**
```json
{
  "name": "Cardiology",
  "description": "Heart and cardiovascular system"
}
```

**Validation Rules:**
| Field | Rule |
|-------|------|
| name | Required, max 255 characters, must be unique |
| description | Max 2000 characters |

**Success Response (201):**
```json
{
  "id": 1,
  "name": "Cardiology",
  "description": "Heart and cardiovascular system",
  "isActive": true
}
```

**Error Response (400) — Duplicate name:**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Department with name 'Cardiology' already exists",
  "timestamp": "2026-07-30T10:00:00"
}
```

**Error Response (400) — Validation Error:**
```json
{
  "timestamp": "2026-07-30T10:00:00",
  "status": 400,
  "error": "Validation Failed",
  "message": "Request validation failed",
  "path": "/api/departments",
  "validationErrors": [
    { "field": "name", "message": "Department name is required" }
  ]
}
```

---

### 4. Update Department (Admin only)

```
PUT /api/departments/{id}
```

**Auth:** ADMIN

**Path Variables:**
| Name | Type | Description |
|------|------|-------------|
| id | Long | Department ID |

**Request Body:**
```json
{
  "name": "Cardiology Updated",
  "description": "Updated description"
}
```

**Validation Rules:** Same as Create

**Success Response (200):**
```json
{
  "id": 1,
  "name": "Cardiology Updated",
  "description": "Updated description",
  "isActive": true
}
```

**Error Response (404):**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Department not found with id: 999",
  "timestamp": "2026-07-30T10:00:00"
}
```

---

### 5. Delete Department (Admin only)

```
DELETE /api/departments/{id}
```

**Auth:** ADMIN

**Path Variables:**
| Name | Type | Description |
|------|------|-------------|
| id | Long | Department ID |

**Success Response (204):** No content

**Error Response (404):**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Department not found with id: 999",
  "timestamp": "2026-07-30T10:00:00"
}
```

---

## Doctor Catalog Endpoints (`/api/doctors`)

---

### 6. List All Doctors (Public/Patient)

```
GET /api/doctors
```

**Auth:** All authenticated roles

**Query Parameters (all optional):**
| Name | Type | Default | Description |
|------|------|---------|-------------|
| departmentId | Long | — | Filter by department ID |
| specialization | String | — | Filter by specialization (partial match, case-insensitive) |
| search | String | — | Free-text search on name OR specialization (partial match, case-insensitive) |
| page | int | 0 | Zero-based page number |
| size | int | 20 | Page size (clamped to max 100) |
| sortBy | String | name | `name`, `consultationFee`, or `experienceYears` |
| sortDirection | String | asc | `asc` or `desc` |

**Success Response (200) — paginated shape:**
```json
{
  "content": [
    {
      "id": 1,
      "name": "Dr. Arjun Sharma",
      "userId": "550e8400-e29b-41d4-a716-446655440001",
      "departmentId": 1,
      "departmentName": "Cardiology",
      "specialization": "Interventional Cardiology",
      "qualification": "MD, DM Cardiology",
      "experienceYears": 12,
      "consultationFee": 500.00,
      "avgConsultationTimeMinutes": 15,
      "isAvailable": true
    }
  ],
  "totalElements": 10,
  "totalPages": 1,
  "number": 0,
  "size": 20,
  "first": true,
  "last": true,
  "empty": false
}
```

**Example:** `GET /api/doctors?departmentId=1&page=0&size=5&sortBy=consultationFee&sortDirection=desc`

---

### 7. Get Doctor by ID (Public/Patient)

```
GET /api/doctors/{id}
```

**Auth:** All authenticated roles

**Path Variables:**
| Name | Type | Description |
|------|------|-------------|
| id | Long | Doctor catalog entry ID |

**Success Response (200):**
```json
{
  "id": 1,
  "name": "Dr. Arjun Sharma",
  "userId": "550e8400-e29b-41d4-a716-446655440001",
  "departmentId": 1,
  "departmentName": "Cardiology",
  "specialization": "Interventional Cardiology",
  "qualification": "MD, DM Cardiology",
  "experienceYears": 12,
  "consultationFee": 500.00,
  "avgConsultationTimeMinutes": 15,
  "isAvailable": true
}
```

**Error Response (404):**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Doctor catalog entry not found with id: 999",
  "timestamp": "2026-07-30T10:00:00"
}
```

---

### 8. Create Doctor Catalog Entry (Admin only)

```
POST /api/doctors
```

**Auth:** ADMIN

**Request Body:**
```json
{
  "name": "Dr. Arjun Sharma",
  "userId": "550e8400-e29b-41d4-a716-446655440001",
  "departmentId": 1,
  "specialization": "Interventional Cardiology",
  "qualification": "MD, DM Cardiology",
  "experienceYears": 12,
  "consultationFee": 500.00,
  "avgConsultationTimeMinutes": 15
}
```

**Validation Rules:**
| Field | Rule |
|-------|------|
| name | Required, max 255 characters |
| userId | Required, valid UUID |
| departmentId | Required (must reference existing department) |
| specialization | Required, max 255 characters |
| qualification | Required, max 500 characters |
| experienceYears | Required, min 0 |
| consultationFee | Required, min 0 |
| avgConsultationTimeMinutes | Required, min 1 |

**Success Response (201):**
```json
{
  "id": 1,
  "name": "Dr. Arjun Sharma",
  "userId": "550e8400-e29b-41d4-a716-446655440001",
  "departmentId": 1,
  "departmentName": "Cardiology",
  "specialization": "Interventional Cardiology",
  "qualification": "MD, DM Cardiology",
  "experienceYears": 12,
  "consultationFee": 500.00,
  "avgConsultationTimeMinutes": 15,
  "isAvailable": true
}
```

**Error Response (409) — Duplicate userId** *(duplicates return 409 Conflict consistently across services):*
```json
{
  "timestamp": "2026-07-30T10:00:00",
  "status": 409,
  "error": "Conflict",
  "message": "Doctor catalog entry already exists for userId: 550e8400-e29b-41d4-a716-446655440001",
  "path": "/api/doctors",
  "validationErrors": null
}
```

---

### 9. Update Doctor Catalog Entry (Admin only)

```
PUT /api/doctors/{id}
```

**Auth:** ADMIN

**Path Variables:**
| Name | Type | Description |
|------|------|-------------|
| id | Long | Doctor catalog entry ID |

**Request Body:** Same shape as Create

**Success Response (200):** Same shape as Create response

**Error Response (404):**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Doctor catalog entry not found with id: 999",
  "timestamp": "2026-07-30T10:00:00"
}
```

---

### 10. Delete Doctor Catalog Entry (Admin only)

```
DELETE /api/doctors/{id}
```

**Auth:** ADMIN

**Path Variables:**
| Name | Type | Description |
|------|------|-------------|
| id | Long | Doctor catalog entry ID |

**Success Response (204):** No content

---

### 11. Toggle Availability (Doctor's own record)

```
PUT /api/doctors/me/availability
```

**Auth:** DOCTOR

**Headers:**
| Header | Value | Source |
|--------|-------|--------|
| X-User-Id | \<user-uuid\> | Set by API Gateway from JWT |
| X-User-Role | DOCTOR | Set by API Gateway from JWT |

**Request Body:**
```json
{
  "isAvailable": false
}
```

**Validation Rules:**
| Field | Rule |
|-------|------|
| isAvailable | Required |

**Success Response (200):**
```json
{
  "id": 1,
  "name": "Dr. Arjun Sharma",
  "userId": "550e8400-e29b-41d4-a716-446655440001",
  "departmentId": 1,
  "departmentName": "Cardiology",
  "specialization": "Interventional Cardiology",
  "qualification": "MD, DM Cardiology",
  "experienceYears": 12,
  "consultationFee": 500.00,
  "avgConsultationTimeMinutes": 15,
  "isAvailable": false
}
```

**Error Response (404):**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "No doctor catalog entry found for userId: 550e8400-e29b-41d4-a716-446655440001",
  "timestamp": "2026-07-30T10:00:00"
}
```

---

### 11b. Get My Catalog Entry (Doctor)

```
GET /api/doctors/me
```

**Auth:** DOCTOR

**Headers:** `X-User-Id`, `X-User-Role` (set by gateway).

**Description:** Resolves the CALLING doctor's own catalog entry from their `X-User-Id` (header-based identity). The doctor dashboard and queue page use this instead of scanning the whole paginated catalog to find "which entry is mine" — which silently broke once the catalog outgrew one page. A 404 here is expected for accounts not yet linked to a catalog entry (the UI turns it into a friendly setup state).

**Success Response (200):** `DoctorCatalogResponseDto` (same shape as `GET /api/doctors/{id}`).

**Error Responses:** `403` (not a DOCTOR), `404` (no catalog entry for this account — "Ask an admin to create one in Admin → Manage Doctors").

---

### 11c. Ranked Doctor Search (RAG-style)

```
GET /api/doctors/search?q=<query>
```

**Auth:** All authenticated roles

**Query Parameters:**
| Name | Type | Default | Description |
|------|------|---------|-------------|
| q | String (required) | — | Free-text query (symptom, specialty, department, doctor name) |
| limit | int | 10 | Max results (clamped to 50) |

**Description:** Deterministic, RAG-style relevance retrieval — no vector store. Every catalog doctor is scored by weighted token overlap between the normalized query and their **name (3×), specialization (2×), department (2×), and qualification (1×)**; ties break alphabetically. Returns a flat, relevance-ranked list (not paginated) — one `DoctorSearchResultDto` per doctor (same fields as `DoctorCatalogResponseDto` plus `score`). Drives the patient browse screen's search box and grounds the AI chat assistant's doctor suggestions.

**Success Response (200):**
```json
{
  "results": [
    {
      "id": 1,
      "name": "Dr. Arjun Sharma",
      "departmentName": "Cardiology",
      "specialization": "Interventional Cardiology",
      "qualification": "MD, DM Cardiology",
      "experienceYears": 12,
      "consultationFee": 500.00,
      "avgConsultationTimeMinutes": 15,
      "isAvailable": true,
      "score": 0.95
    }
  ]
}
```

---

## Queue Endpoints (`/api/queue`)

All queue endpoints require a valid JWT (validated at the gateway) and use the `X-User-Id` / `X-User-Role` headers propagated by the API Gateway.

**Triage levels:** `EMERGENCY > HIGH > NORMAL > FOLLOW_UP`. The AI suggests a level (`aiSuggestedTriage`); the doctor can override it (`doctorOverrideTriage`) — the override is final. The effective level drives queue ordering.

**Wait-time formula:** `predictedWaitMinutes = (number of patients ahead in effective queue order) x doctor's avgConsultationTimeMinutes`, recalculated on every read (never cached).

---

### 12. Join Queue (Patient)

```
POST /api/queue/join
```

**Auth:** PATIENT

**Headers:** `X-User-Id`, `X-User-Role` (set by gateway)

**Request Body:**
```json
{
  "doctorCatalogEntryId": 1,
  "symptomText": "Severe chest pain radiating to my left arm for the past hour",
  "patientName": "John Patient"
}
```

**Validation Rules:**
| Field | Rule |
|-------|------|
| doctorCatalogEntryId | Required, must reference an existing doctor catalog entry |
| symptomText | Required, max 2000 characters |
| patientName | Optional, max 255 characters — sent by the frontend from the auth session so the doctor's queue shows real names |

**Behavior:**
- Calls the Groq LLM (model `llama-3.1-8b-instant`) for AI triage. On any AI failure, falls back to `NORMAL` — a broken AI call never blocks a real patient.
- Rejects if the doctor is unavailable (`isAvailable: false`) → 409.
- Rejects duplicate active entries for the same doctor → 409.

**Success Response (201):**
```json
{
  "id": 1,
  "patientId": "550e8400-e29b-41d4-a716-446655440010",
  "patientName": "John Patient",
  "doctorCatalogEntryId": 1,
  "doctorName": "Dr. Arjun Sharma",
  "departmentName": "Cardiology",
  "specialization": "Interventional Cardiology",
  "symptomText": "Severe chest pain radiating to my left arm for the past hour",
  "aiSuggestedTriage": "EMERGENCY",
  "doctorOverrideTriage": null,
  "effectiveTriage": "EMERGENCY",
  "status": "WAITING",
  "position": 1,
  "predictedWaitMinutes": 0,
  "joinedAt": "2026-07-31T09:00:00",
  "calledAt": null,
  "completedAt": null
}
```

**Error Responses:**
- `409` — Doctor is currently unavailable
- `409` — You already have an active queue entry for this doctor
- `404` — Doctor catalog entry not found
- `400` — Validation failed
- `503` — Doctor service temporarily unavailable

---

### 13. My Queue Status (Patient)

```
GET /api/queue/my-status
```

**Auth:** PATIENT

**Description:** Returns the patient's own current queue entry with a freshly recalculated position and predicted wait time. Recalculated on every read — not cached.

**Success Response (200) — active entry:**
```json
{
  "active": true,
  "entry": {
    "id": 1,
    "status": "WAITING",
    "position": 3,
    "predictedWaitMinutes": 30,
    "effectiveTriage": "HIGH",
    "symptomText": "...",
    "joinedAt": "2026-07-31T09:00:00"
  }
}
```

**Success Response (200) — not in queue:**
```json
{
  "active": false,
  "entry": null
}
```

---

### 13b. My Visit History (Patient)

```
GET /api/queue/my-history?limit=10
```

**Auth:** PATIENT.

**Query Parameters:** `limit` (optional, default 10, clamped server-side to 1-50).

**Description:** Returns the calling patient's recent **completed/cancelled** visits, newest first. Each entry includes the enriched doctor name / department / specialization (fetched live from doctor-service).

**Success Response (200):** Array of `QueueEntryResponseDto` (status `COMPLETED` or `CANCELLED`; `position`/`predictedWaitMinutes` are `null`).

---

### 14. Doctor's Live Queue (Doctor/Admin)

```
GET /api/queue/doctor/{doctorCatalogEntryId}
```

**Auth:** DOCTOR (own queue only) or ADMIN (any)

**Path Variables:**
| Name | Type | Description |
|------|------|-------------|
| doctorCatalogEntryId | Long | Doctor catalog entry ID |

**Query Parameters (optional):**
| Name | Type | Description |
|------|------|-------------|
| search | String | Case-insensitive substring match on patient name. Positions reported remain the patient's REAL queue position — the search only narrows which rows are returned. |

**Description:** Returns that doctor's live queue (WAITING + IN_PROGRESS entries), ordered by effective triage level then FIFO by `joinedAt`. Each entry includes a derived `position` (1 = next to be seen) and `predictedWaitMinutes`.

**Success Response (200):**
```json
[
  {
    "id": 5,
    "patientId": "550e8400-e29b-41d4-a716-446655440010",
    "patientName": "John Patient",
    "status": "IN_PROGRESS",
    "position": 1,
    "predictedWaitMinutes": 0,
    "effectiveTriage": "EMERGENCY",
    "aiSuggestedTriage": "EMERGENCY",
    "doctorOverrideTriage": null
  },
  {
    "id": 8,
    "patientId": "550e8400-e29b-41d4-a716-446655440011",
    "patientName": "Alice Wonder",
    "status": "WAITING",
    "position": 2,
    "predictedWaitMinutes": 15,
    "effectiveTriage": "HIGH"
  }
]
```

**Error Responses:** `403` (another doctor's queue), `404` (unknown doctor), `503` (doctor service down).

---

### 14b. Per-Doctor Analytics (Doctor/Admin)

```
GET /api/queue/doctor/{doctorCatalogEntryId}/analytics
```

**Auth:** DOCTOR (own queue only) or ADMIN (any).

**Description:** Today's scalars (patients completed, average wait, average consult time) plus 7-day patient-load and average-wait trends for one doctor, zero-filled so charts render a full window.

**Success Response (200):**
```json
{
  "patientsCompletedToday": 12,
  "avgWaitTodayMinutes": 10.5,
  "avgConsultTimeTodayMinutes": 14.0,
  "patientsPerDay": [
    { "date": "2026-07-29", "count": 0 },
    { "date": "2026-07-30", "count": 3 }
  ],
  "avgWaitTimeTrend": [
    { "date": "2026-07-29", "avgWaitMinutes": null },
    { "date": "2026-07-30", "avgWaitMinutes": 12.5 }
  ]
}
```

**Error Responses:** `403` (wrong role / another doctor's queue), `404` (unknown doctor).

---

### 15. Override Triage (Doctor)

```
PUT /api/queue/{id}/override-triage
```

**Auth:** DOCTOR (owner of the queue) or ADMIN

**Path Variables:**
| Name | Type | Description |
|------|------|-------------|
| id | Long | Queue entry ID |

**Request Body:**
```json
{
  "triageLevel": "EMERGENCY"
}
```

**Validation Rules:** `triageLevel` required — one of `EMERGENCY`, `HIGH`, `NORMAL`, `FOLLOW_UP`.

**Behavior:** Sets `doctorOverrideTriage` (final). The queue reorders on the next read. Cannot override a COMPLETED/CANCELLED/IN_PROGRESS entry → 400.

**Success Response (200):** Updated `QueueEntryResponseDto` with `doctorOverrideTriage` and new `effectiveTriage`.

**Error Responses:** `400` (wrong status), `403` (not the owning doctor), `404` (entry not found).

---

### 16. Call Next (Doctor)

```
PUT /api/queue/{id}/call-next
```

**Auth:** DOCTOR (owner of the queue) or ADMIN

**Path Variables:**
| Name | Type | Description |
|------|------|-------------|
| id | Long | Queue entry ID (the next WAITING patient to call) |

**Behavior:** Marks the entry `IN_PROGRESS` and sets `calledAt`. Only valid for a `WAITING` entry → 400 otherwise.

**Success Response (200):** Updated entry with `status: "IN_PROGRESS"` and `calledAt` set.

---

### 16b. Cancel Queue Entry (Patient/Admin)

```
PUT /api/queue/{id}/cancel
```

**Auth:** PATIENT (own entry only) or ADMIN (any entry).

**Description:** Cancels a **WAITING** entry — the patient leaves the queue before being seen. Cancelled visits still appear in the patient's history. Only valid for a `WAITING` entry → 400 otherwise.

**Success Response (200):** Updated entry with `status: "CANCELLED"`.

**Error Responses:** `400` (not WAITING), `403` (patient cancelling someone else's entry), `404` (entry not found).

---

### 17. Complete Consultation (Doctor)

```
PUT /api/queue/{id}/complete
```

**Auth:** DOCTOR (owner of the queue) or ADMIN

**Path Variables:**
| Name | Type | Description |
|------|------|-------------|
| id | Long | Queue entry ID |

**Behavior:** Marks the entry `COMPLETED` and sets `completedAt`. Only valid for an `IN_PROGRESS` entry → 400 otherwise. Completed entries leave the active queue; remaining patients' waits are recalculated on next read.

**Success Response (200):** Updated entry with `status: "COMPLETED"` and `completedAt` set (`position`/`predictedWaitMinutes` are `null`).

---

### 18. Live Overview (Admin)

```
GET /api/queue/live
```

**Auth:** ADMIN

**Description:** Hospital-wide live overview: summary metrics plus a per-doctor breakdown. A WAITING patient counts as **delayed** when their predicted wait exceeds `queue.delay-threshold-minutes` (default 30).

**Success Response (200):**
```json
{
  "totalWaiting": 14,
  "totalInProgress": 3,
  "doctorsOnline": 8,
  "doctorsOffline": 2,
  "delayedConsultations": 2,
  "averageWaitMinutes": 22,
  "doctors": [
    {
      "doctorCatalogEntryId": 1,
      "doctorName": "Dr. Arjun Sharma",
      "doctorUserId": "550e8400-e29b-41d4-a716-446655440001",
      "departmentName": "Cardiology",
      "specialization": "Interventional Cardiology",
      "avgConsultationTimeMinutes": 15,
      "isAvailable": true,
      "waitingCount": 4,
      "inProgressCount": 1,
      "delayedCount": 1,
      "longestWaitMinutes": 60
    }
  ]
}
```

---

### 19. Analytics Summary (Admin)

```
GET /api/queue/analytics/summary
```

**Auth:** ADMIN

**Description:** Aggregates the last 7 days (inclusive of today) from the `queue_entries` table for the Admin Analytics Dashboard. Aggregation happens in SQL (native `GROUP BY DATE(...)`), never by pulling rows into Java memory. Missing days are zero-filled so the chart window is always complete.

**Response shape (definitions):**
| Field | Type | Definition |
|-------|------|-----------|
| `patientsPerDay` | list | Entries **completed** that day (patients actually handled), one item per day, oldest first |
| `avgWaitTimeTrend` | list | Average **wait time in minutes** (`called_at − joined_at`) for entries **called** that day; `null` when no patient was called that day |
| `departmentDistribution` | list | Completed entries grouped by department (joined via the Feign doctor catalog), sorted by count descending |

**Success Response (200):**
```json
{
  "patientsPerDay": [
    { "date": "2026-07-29", "count": 0 },
    { "date": "2026-07-30", "count": 0 },
    { "date": "2026-07-31", "count": 3 },
    { "date": "2026-08-01", "count": 2 },
    { "date": "2026-08-02", "count": 0 },
    { "date": "2026-08-03", "count": 5 },
    { "date": "2026-08-04", "count": 1 }
  ],
  "avgWaitTimeTrend": [
    { "date": "2026-07-29", "avgWaitMinutes": null },
    { "date": "2026-07-30", "avgWaitMinutes": null },
    { "date": "2026-07-31", "avgWaitMinutes": 12.5 },
    { "date": "2026-08-01", "avgWaitMinutes": 8.0 },
    { "date": "2026-08-02", "avgWaitMinutes": null },
    { "date": "2026-08-03", "avgWaitMinutes": 15.0 },
    { "date": "2026-08-04", "avgWaitMinutes": 20.0 }
  ],
  "departmentDistribution": [
    { "departmentName": "Cardiology", "patientCount": 6 },
    { "departmentName": "Neurology", "patientCount": 3 }
  ]
}
```

**Sparse/empty data behavior:** with no activity in the window the endpoint still returns 200 with seven zero-filled days and an empty `departmentDistribution` — it never crashes or 500s on an empty dataset.

**Error Responses:** `403` (non-admin). If doctor-service is unreachable, `departmentDistribution` degrades to an empty list (logged) while the time series still return — a doctor-service outage does not take down the whole analytics response.

---

### 19b. AI Chat Assistant (Patient)

```
POST /api/queue/chat
```

**Auth:** Any authenticated role

**Headers:** `X-User-Id`, `X-User-Role` (set by gateway)

**Request Body:**
```json
{
  "message": "How long will I wait?"
}
```

**Success Response (200):**
```json
{
  "reply": "You are position 2 of 5 with an estimated wait of ~15 minutes.",
  "intent": "WAIT_TIME",
  "suggestedDoctors": []
}
```

**Description:** Heuristic intent matching over live data (deterministic — works with no Groq key):
- `WAIT_TIME` — resolves the caller's active queue entry and answers with position + estimated wait
- `SYMPTOM_TRIAGE` — maps reported symptoms to a suggested department + ranked doctors (reuses `DoctorRecommendationService`); replies include `suggestedDoctors`
- `HELP` / `DEFAULT` — generic guidance

**Error Responses:** `400` (empty message), `503` (doctor-service unreachable — degrades to a generic reply instead).

---

## Bill Endpoints (`/api/bills`)

Bills are created automatically when a visit is marked **completed**: the queue-service charges the doctor's catalog `consultationFee` (fetched via Feign), adds 18% GST, and stores an `UNPAID` bill. Payments are **sandboxed** — `POST …/pay` flips the status to `PAID` with a timestamp; no gateway, no real money.

### 25. My Bills (Patient)

```
GET /api/bills/my
```

**Auth:** PATIENT

**Success Response (200):**
```json
[
  {
    "id": 12,
    "queueEntryId": 34,
    "doctorName": "Dr. Arjun Sharma",
    "departmentName": "Cardiology",
    "amount": 500.00,
    "gst": 90.00,
    "total": 590.00,
    "status": "UNPAID",
    "createdAt": "2026-08-17T10:00:00"
  }
]
```

**Error Responses:** `403` (non-patient), `404` (no profile identity).

---

### 26. Bills for a Queue (Doctor/Admin/Receptionist)

```
GET /api/bills/queue/{queueEntryId}
```

**Auth:** DOCTOR, ADMIN, or RECEPTIONIST

**Description:** The single bill for a given queue entry — lets the doctor's queue screen surface the invoice for a completed visit. Ownership-checked: doctors can only fetch their own queue's bills; ADMIN/RECEPTIONIST can fetch any.

**Success Response (200):** one `BillResponseDto` (same shape as above).

**Error Responses:** `403` (wrong role or not your queue), `404` (no bill for this queue entry).

---

### 27. Pay a Bill (sandbox)

```
POST /api/bills/{id}/pay
```

**Auth:** the owning PATIENT (or ADMIN)

**Description:** Sandbox payment — marks the bill `PAID` and records `paidAt`. Idempotent: paying an already-`PAID` bill succeeds (no error).

**Success Response (200):** updated `BillResponseDto` with `"status": "PAID"`.

**Error Responses:** `403` (not the owner), `404` (no such bill).

---

## Notification Endpoints (`/api/notifications`)

All endpoints require a valid JWT (validated at the gateway) and use the `X-User-Id` / `X-User-Role` headers propagated by the API Gateway — the same header-trust pattern as every other service. Notifications are written by the RabbitMQ consumer (one row per `queue.joined` / `queue.triaged` / `queue.called` / `queue.completed` event published by queue-service).

### 20. My Notifications (paginated)

```
GET /api/notifications/me?page=0&size=20
```

**Auth:** Any authenticated role (in practice only PATIENTs receive events today).

**Query Parameters (all optional):**
| Name | Type | Default | Description |
|------|------|---------|-------------|
| page | int | 0 | Zero-based page number |
| size | int | 20 | Page size (clamped to max 50) |

**Description:** Returns the caller's OWN notifications, newest first. The response mirrors the Spring Data Page JSON shape plus a total `unreadCount` (for the bell badge — independent of the loaded page).

**Success Response (200):**
```json
{
  "content": [
    {
      "id": 12,
      "type": "queue.called",
      "message": "Dr. Arjun Sharma has called you — please head to the consultation room.",
      "read": false,
      "createdAt": "2026-08-10T09:00:00"
    }
  ],
  "totalElements": 4,
  "totalPages": 1,
  "number": 0,
  "size": 20,
  "first": true,
  "last": true,
  "empty": false,
  "unreadCount": 3
}
```

**Event type → title/kind (frontend mapping):** `queue.joined` → Queue joined (success); `queue.triaged` → Urgency updated (info); `queue.called` → It's your turn! (warning); `queue.completed` → Consultation complete (success).

**Error Responses:** `400` (missing identity header).

### 21. Mark Notification as Read

```
PUT /api/notifications/{id}/read
```

**Auth:** The recipient, or ADMIN (any).

**Path Variables:**
| Name | Type | Description |
|------|------|-------------|
| id | Long | Notification ID |

**Description:** Marks the notification read. Ownership-checked — a doctor must never flip another patient's read state. Idempotent (marking an already-read notification is a no-op).

**Success Response (200):** The updated `NotificationResponseDto` with `read: true`.

**Error Responses:** `403` (not the recipient and not admin), `404` (notification not found).

### 22. My Delivery Preferences (Web Push)

```
GET /api/notifications/preferences
```

**Auth:** Any authenticated role.

**Description:** Returns the caller's delivery preferences. `webPushEnabled` defaults to `true` when no preference row exists yet (missing row = all defaults on); delivery ALSO requires the OS-level browser permission + a registered subscription.

**Success Response (200):**
```json
{ "webPushEnabled": true }
```

**Error Responses:** `400` (missing identity header).

### 23. Update My Delivery Preferences

```
PUT /api/notifications/preferences
```

**Auth:** Any authenticated role.

**Request Body:**
```json
{ "webPushEnabled": false }
```

**Description:** Upserts the caller's preference row. `webPushEnabled` is the user's opt-out switch (the browser permission is tracked by the browser itself).

**Success Response (200):** The updated `{ "webPushEnabled": ... }`.

**Error Responses:** `400` (missing `webPushEnabled`, or missing identity header).

### 24. Register a Push Subscription

```
POST /api/notifications/push/subscriptions
```

**Auth:** Any authenticated role (the SPA calls this after the user grants the Notification permission).

**Request Body** (the browser's `PushSubscriptionJSON` — Web Push spec shape):
```json
{
  "endpoint": "https://fcm.googleapis.com/fcm/send/...",
  "keys": {
    "p256dh": "BOH8nTQA5iZhl23+NCzGG9prvOZ5...",
    "auth": "TRlY/7yQzvqcLpgHQTxiU5fVzAAvAw/cdSh5kLFLNqg="
  }
}
```

**Description:** Registers (or re-registers, upsert by endpoint) the browser subscription for the calling user. Re-registering the same endpoint — e.g. after a session switch on a shared device — re-assigns the row to the caller so delivery never goes to the wrong person.

**Success Response (201):** empty body.

**Error Responses:** `400` (validation: missing `endpoint` / `keys.p256dh` / `keys.auth`, or missing identity header).

### 25. Remove a Push Subscription

```
DELETE /api/notifications/push/subscriptions?endpoint=<url-encoded>
```

**Auth:** Any authenticated role.

**Query Parameters:**
| Name | Type | Description |
|------|------|-------------|
| endpoint | string (required) | The push endpoint to remove |

**Description:** Removes the caller's subscription for the given endpoint (called on unsubscribe). Idempotent — removing a nonexistent endpoint is a no-op. Dead endpoints are also self-cleaned during delivery when the push service answers 404/410.

**Success Response (204):** empty body.

**Error Responses:** `400` (missing identity header).

---

## Auth Endpoints (`/api/auth`)

Public (no Bearer token). Error responses use the shared shape plus an
optional `code` field for machine-readable branching.

### `POST /api/auth/signup` — register

Body: `{ "fullName", "email", "password", "role" }` → `201`.

| Mode | Response |
|---|---|
| Verification **off** (local dev) | `AuthResponse` with a real `token` — the session is created immediately |
| Verification **on** (production) | `token: null`, `verificationRequired: true`, `message` explains the email was sent — the account is locked until verified |

`409` duplicate email · `400` validation · `429` `RATE_LIMITED` (10/hour/IP).

### `POST /api/auth/login` — sign in

Body: `{ "email", "password" }` → `200` + JWT.

- `401` invalid credentials or deactivated account
- **`403` `EMAIL_NOT_VERIFIED`** — account hasn't clicked its verification link yet (frontend shows the resend screen)
- **`429` `RATE_LIMITED`** — brute-force guard (5 attempts/15 min per email+IP)

### `GET /api/auth/verify?token=<raw>` — verify email (link target)

`200` `{ message: "Your email has been verified — you can now sign in." }`
`400` `INVALID_TOKEN` (unknown / expired / already used). One-time.

### `POST /api/auth/resend-verification`

Body: `{ "email" }` → `200` with an outcome `message` (new link sent, already
verified, or generic for unknown emails — no enumeration). `429` `RATE_LIMITED` (3/hour/email).

### `POST /api/auth/forgot-password`

Body: `{ "email" }` → `200` with a **generic** message either way (the endpoint
can't be used to enumerate accounts). Emails a 30-min reset link for verified
accounts only. `429` `RATE_LIMITED` (3/hour/email).

### `POST /api/auth/reset-password`

Body: `{ "token", "newPassword" }` → `200` + message. `400` `INVALID_TOKEN`
(unknown / expired / already used). Invalidates the reset token (one-time).

### Error codes

| `code` | HTTP | Meaning |
|---|---|---|
| `EMAIL_NOT_VERIFIED` | 403 | login blocked pending email verification |
| `RATE_LIMITED` | 429 | brute-force / abuse limit hit |
| `INVALID_TOKEN` | 400 | verification/reset token unknown, expired, or used |

---

## Status Codes Summary

| Code | Meaning |
|------|---------|
| 200 | Success (GET, PUT) |
| 201 | Created (POST) |
| 204 | No Content (DELETE) |
| 400 | Bad Request / Validation Error / Invalid state transition |
| 403 | Forbidden (wrong role / not your queue) |
| 404 | Not Found |
| 409 | Conflict (doctor unavailable, duplicate queue entry) |
| 401 | Unauthorized (missing/invalid JWT) |
| 503 | Service Unavailable (doctor-service / AI temporarily down) |

---

## Health Endpoints (all services)

Each service exposes a public health probe (no auth, whitelisted at the gateway):

| Endpoint | Service |
|----------|---------|
| `GET /api/auth/health` | auth-service (:8081) |
| `GET /api/users/health` | user-service (:8082) |
| `GET /api/doctors/health` | doctor-service (:8083) |
| `GET /api/queue/health` | queue-service (:8084) |
| `GET /api/notifications/health` | notification-service (:8085) |

All return `200` with `{ "service": "<name>", "status": "UP", "timestamp": <epoch-ms> }`.

---

## Interactive Documentation

- **Centralized Swagger UI:** `http://localhost:8080/swagger-ui.html` — the gateway aggregates every service's OpenAPI docs (`/v3/api-docs/{service}`). Click **Authorize** and paste a JWT to test authenticated endpoints directly.
- **Per-service UI:** `http://localhost:808X/swagger-ui.html` on each service port.
- **Postman collection:** `postman/CareQ.postman_collection.json` + `postman/CareQ.postman_environment.json` — import both; the Login request auto-captures the JWT into `{{authToken}}`.

> **Note:** Swagger UI is open in local dev, but **production deploys set `SPRINGDOC_ENABLED=false`** (CD workflow) which switches off both `/v3/api-docs/**` and `/swagger-ui/**` on every service and the gateway. The gateway still whitelists those paths so a local demo keeps working.
