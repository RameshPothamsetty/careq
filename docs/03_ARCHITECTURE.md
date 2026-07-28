# CareQ — Architecture Document

**Version:** 1.0 (Day 1)  
**Status:** Initial

---

## 1. High-Level Architecture Diagram

```
                         ┌─────────────────────────────────┐
                         │           React SPA             │
                         │   (Vite + React Router v6)      │
                         └──────────────┬──────────────────┘
                                        │ HTTP (Axios)
                                        ▼
                         ┌─────────────────────────────────┐
                         │       API Gateway (port 8080)   │
                         │   Spring Cloud Gateway          │
                         │   JWT Validation Filter         │
                         └──┬────────┬────────┬───────────┘
                            │        │        │
              ┌─────────────┘        │        └─────────────┐
              ▼                      ▼                      ▼
   ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
   │   Auth Service   │  │   User Service   │  │  Doctor Service  │
   │   (port 8081)    │  │   (port 8082)    │  │   (port 8083)    │
   │                  │  │                  │  │                  │
   │  /api/auth/**    │  │  /api/users/**   │  │  /api/doctors/** │
   └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘
            │                     │                     │
            └──────────────┬──────┴─────────────────────┘
                           │
                  ┌──────────────────┐
                  │  Queue Service   │
                  │   (port 8084)    │
                  │                  │
                  │  /api/queue/**   │
                  └────────┬─────────┘
                           │
                  ┌──────────────────┐
                  │  Eureka Server   │
                  │   (port 8761)    │
                  │ Service Registry │
                  └──────────────────┘
                           │
                  ┌──────────────────┐
                  │      MySQL       │
                  │    careq_db      │
                  │ (single shared)  │
                  └──────────────────┘
```

---

## 2. Service Responsibility Table

| Service | Responsibility | Owns DB Tables | Registers with Eureka |
|---------|---------------|----------------|----------------------|
| **eureka-server** | Service registry — all services discover each other via Eureka | None | No (it is the registry) |
| **api-gateway** | Routes external requests to internal services via Eureka discovery; validates JWT tokens | None | Yes (as a client) |
| **auth-service** | User registration, login, JWT issuance and refresh | `users` | Yes |
| **user-service** | Profile CRUD for Patients, Doctors, Admins | `users` (shared), `patient_profiles`, `admin_profiles` | Yes |
| **doctor-service** | Department and Doctor catalog management | `departments`, `doctor_profiles` | Yes |
| **queue-service** | Queue operations, AI wait-time prediction, AI symptom triage | `queue_entries` | Yes |

> **Assumption:** All services share a single MySQL database (`careq_db`) for simplicity in this 15-day student project. In production, each service would own its schema.

---

## 3. Authentication Flow

```
┌──────┐         ┌─────────────┐         ┌──────────────┐         ┌──────────────┐
│Client│         │API Gateway  │         │ Auth Service  │         │  User Service│
│      │         │(port 8080)  │         │ (port 8081)   │         │ (port 8082)  │
└──┬───┘         └──────┬──────┘         └───────┬───────┘         └──────┬───────┘
   │                     │                        │                        │
   │  POST /api/auth/    │                        │                        │
   │  /login             │                        │                        │
   │ {email, password}   │                        │                        │
   ├─────────────────────▶   Forward to auth-svc  │                        │
   │                     ├────────────────────────▶                        │
   │                     │                        │  Validate credentials  │
   │                     │                        │  & return JWT          │
   │                     │                        ├────────────────────────▶
   │                     │                        │◀────────────────────────
   │                     │◀────────────────────────                        │
   │◀────────────────────┤                        │                        │
   │                     │                        │                        │
   │  JWT returned       │                        │                        │
   │  {access_token,     │                        │                        │
   │   role, expires_in} │                        │                        │
   │                     │                        │                        │
   │  GET /api/queue/    │                        │                        │
   │  Authorization:     │                        │                        │
   │  Bearer <JWT>       │                        │                        │
   ├─────────────────────▶                        │                        │
   │                     │ Validate JWT           │                        │
   │                     │ at Gateway             │                        │
   │                     │   (or forward to       │                        │
   │                     │    auth-service to      │                        │
   │                     │    validate)            │                        │
   │                     │                        │                        │
   │                     │ Forward to queue-svc   │                        │
   │                     ├─────────────────────────────────────────────────▶
   │                     │◀─────────────────────────────────────────────────
   │◀────────────────────┤                        │                        │
   │ {queue data}        │                        │                        │
```

**Assumption:** JWT validation will be performed at the **API Gateway level** using a shared secret or by calling auth-service's validation endpoint. Individual services may optionally re-validate for fine-grained role checks. This decision aligns with the typical Spring Cloud Gateway + JWT pattern for microservices.

---

## 4. JWT Token Structure (Proposed)

```json
{
  "sub": "user-uuid",
  "email": "patient@example.com",
  "role": "PATIENT",
  "iat": 1700000000,
  "exp": 1700003600
}
```

- Token validity: 1 hour (short-lived)
- Refresh token: (to be designed — Day 2)
- Algorithm: HMAC-SHA256 (HS256)
- Secret: Configured via `application.yml` (environment variable in production)

---

## 5. API Route Mapping (API Gateway)

| Gateway Route | Target Service | Auth Required | Roles |
|---------------|---------------|---------------|-------|
| `/api/auth/**` | auth-service | No (except refresh) | — |
| `/api/users/**` | user-service | Yes | PATIENT, DOCTOR, ADMIN |
| `/api/doctors/**` | doctor-service | Yes | PATIENT, DOCTOR, ADMIN |
| `/api/departments/**` | doctor-service | Yes | PATIENT, DOCTOR, ADMIN |
| `/api/queue/**` | queue-service | Yes | PATIENT, DOCTOR, ADMIN |
| `/api/eureka/**` | eureka-server | No | — (internal) |
