# CareQ — API Contract

**Version:** 1.6 (Day 7a)
**Status:** Auth + User + Doctor/Department + Queue (+ pagination/search/sorting)

---

## Identity Headers (all services)

The API Gateway validates the JWT and forwards these headers to downstream services — services never re-parse the token:

| Header | Source claim | Notes |
|--------|-------------|-------|
| `X-User-Id` | `sub` | Always present on authenticated routes |
| `X-User-Role` | `role` | Always present on authenticated routes |
| `X-User-Name` | `fullName` | Added Day 7a — present on tokens issued after Day 7a |
| `X-User-Email` | `email` | Added Day 7a |

---

## User Endpoints (`/api/users`) — Day 3 + Day 7a

### 0a. List Users (Admin only) — NEW Day 7a

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
  "status": 404,
  "error": "Not Found",
  "message": "Department not found with id: 999",
  "timestamp": "2026-07-30T10:00:00"
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
  "status": 400,
  "error": "Validation Failed",
  "message": "Request validation failed",
  "details": ["name: must not be blank"],
  "timestamp": "2026-07-30T10:00:00"
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

### 6. List All Doctors (Public/Patient) — paginated since Day 7a

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

**Error Response (400) — Duplicate userId:**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Doctor catalog entry already exists for userId: 550e8400-e29b-41d4-a716-446655440001",
  "timestamp": "2026-07-30T10:00:00"
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

## Queue Endpoints (`/api/queue`) — Day 5

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
| patientName | Optional, max 255 characters — sent by the frontend from the auth session so the doctor's queue shows real names (Day 7a) |

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
| search | String | Case-insensitive substring match on patient name (Day 7a). Positions reported remain the patient's REAL queue position — the search only narrows which rows are returned. |

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
