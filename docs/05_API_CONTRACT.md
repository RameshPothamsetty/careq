# CareQ — API Contract

**Version:** 1.0 (Day 4)
**Status:** Doctor/Department Module

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

### 6. List All Doctors (Public/Patient)

```
GET /api/doctors
```

**Auth:** All authenticated roles

**Query Parameters (all optional):**
| Name | Type | Description |
|------|------|-------------|
| departmentId | Long | Filter by department ID |
| specialization | String | Filter by specialization (partial match, case-insensitive) |

**Success Response (200):**
```json
[
  {
    "id": 1,
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
]
```

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

## Status Codes Summary

| Code | Meaning |
|------|---------|
| 200 | Success (GET, PUT) |
| 201 | Created (POST) |
| 204 | No Content (DELETE) |
| 400 | Bad Request / Validation Error / Duplicate |
| 404 | Not Found |
| 403 | Forbidden (wrong role) |
| 401 | Unauthorized (missing/invalid JWT) |
