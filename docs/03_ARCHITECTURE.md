# CareQ — Architecture Document

**Version:** 1.5 (Day 5)  
**Status:** Updated — Queue Module + AI (Wait-Time Prediction & Symptom Triage) Live

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
                         │   Forwards X-User-Id, X-User-Role│
                         └──┬────────┬────────┬───────────┘
                            │        │        │
              ┌─────────────┘        │        └─────────────┐
              ▼                      ▼                      ▼
   ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
   │   Auth Service   │  │   User Service   │  │  Doctor Service  │
   │   (port 8081)    │  │   (port 8082)    │  │   (port 8083)    │
   │                  │  │                  │  │                  │
   │  /api/auth/**    │  │  /api/users/**   │  │  /api/doctors/** │
   │                  │  │                  │  │  /api/departments│
   └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘
            │                     │                     │
            └──────────────┬──────┴─────────────────────┘
                           │
                  ┌──────────────────┐
                  │  Queue Service   │
                  │   (port 8084)    │
                  │                  │
                  │  /api/queue/**   │
                  │  + AI triage    │
                  │  (Groq LLM)     │
                  └───────┬───┬─────┘
                          │   │  Feign (Eureka lb://doctor-service)
                          │   └──────────▶  doctor-service
                          │                 GET /api/doctors/{id}
                          │                 (avgConsultationTimeMinutes, isAvailable)
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
| **api-gateway** | Routes external requests to internal services via Eureka discovery; validates JWT tokens; forwards `X-User-Id` and `X-User-Role` headers to downstream services | None | Yes (as a client) |
| **auth-service** | User registration, login, JWT issuance | `users` | Yes |
| **user-service** | Profile CRUD for all roles; lazy profile creation on first access; Admin-only user profile lookup | `user_profiles` | Yes |
| **doctor-service** | Department CRUD, Doctor catalog management, public browsing with filters, availability toggle | `departments`, `doctor_catalog_entries` | Yes |
| **queue-service** | Queue operations, AI wait-time prediction, AI symptom triage | `queue_entries` | Yes |

---

## 3. Authentication & Header Propagation Flow

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
   │                     │                        │                        │
   │  GET /api/users/me  │                        │                        │
   │  Authorization:     │                        │                        │
   │  Bearer <JWT>       │                        │                        │
   ├─────────────────────▶                        │                        │
   │                     │ Validate JWT           │                        │
   │                     │ Extract userId, role   │                        │
   │                     │ Set headers:           │                        │
   │                     │  X-User-Id: <id>      │                        │
   │                     │  X-User-Role: <role>  │                        │
   │                     │                        │                        │
   │                     │ Forward to user-svc   │                        │
   │                     ├─────────────────────────────────────────────────▶
   │                     │                        │                        │
   │                     │                        │  GET /api/users/me     │
   │                     │                        │  reads X-User-Id       │
   │                     │                        │  and X-User-Role from  │
   │                     │                        │  request headers       │
   │                     │                        │                        │
   │                     │                        │  Lazy-create profile   │
   │                     │                        │  if not exists         │
   │                     │                        │                        │
   │                     │◀─────────────────────────────────────────────────
   │◀────────────────────┤                        │                        │
   │ {profile data}      │                        │                        │
```

**Key design decisions:**
- **Gateway is the sole entry point** — all external traffic goes through the API Gateway
- **JWT validation at gateway** — the gateway validates the token and strips it before forwarding; downstream services never see the raw JWT, only the trusted headers
- **Header-based identity** — `X-User-Id` and `X-User-Role` headers are set by the gateway's JWT filter and trusted by downstream services
- **Microservice boundary** — `user-service` does not have a foreign key constraint to `auth-service`'s `users` table; `userId` is a plain reference field

---

## 4. JWT Token Structure

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
- Algorithm: HMAC-SHA256 (HS256)
- Secret: Configured via `application.yml` (environment variable in production)

---

## 5. API Route Mapping (API Gateway)

| Gateway Route | Target Service | Auth Required | Roles | Notes |
|---------------|---------------|---------------|-------|-------|
| `/api/auth/**` | auth-service | No (except refresh) | — | Login, signup |
| `/api/users/**` | user-service | Yes | PATIENT, DOCTOR, ADMIN | Profile CRUD |
| `/api/users/me` | user-service | Yes | PATIENT, DOCTOR, ADMIN | Own profile (GET + PUT) — lazy-created |
| `/api/users/{id}` | user-service | Yes | ADMIN only | View any profile |
| `/api/doctors/**` | doctor-service | Yes | PATIENT, DOCTOR, ADMIN | Day 4 |
| `/api/departments/**` | doctor-service | Yes | PATIENT, DOCTOR, ADMIN | Day 4 |
| `/api/queue/**` | queue-service | Yes | PATIENT, DOCTOR, ADMIN | Day 5 — Queue + AI triage |
| `/api/eureka/**` | eureka-server | No | — (internal) | |

---

## 6. Inter-Service Communication: Feign (Day 5)

**Decision:** `queue-service` reads a doctor's `avgConsultationTimeMinutes` and `isAvailable` **live from `doctor-service`** through a declarative Feign client — never duplicating those fields into its own tables. Two sources of truth are forbidden.

```
queue-service                    doctor-service (Eureka: lb://doctor-service)
    │  DoctorServiceClient.getDoctorById(id)            
    ├──────────────────────────────────────────────────────▶
    │      GET /api/doctors/{id}   (Feign, no gateway)     
    │◀──────────────────────────────────────────────────────
    │      DoctorCatalogResponseDto
    │        ├─ avgConsultationTimeMinutes  → wait-time formula
    │        ├─ isAvailable                → join validation
    │        └─ userId                     → doctor ownership checks
```

**Details:**
- `@FeignClient(name = "doctor-service")` — resolved via Eureka, not a hardcoded URL.
- `doctor-service` has no Spring Security of its own (auth is enforced at the gateway), so direct service-to-service calls are allowed.
- A `404` from Feign is translated to `DoctorCatalogNotFoundException`; any other Feign failure is translated to `DoctorServiceUnavailableException` (503) so callers get a clean message instead of a raw Feign stack trace.

## 7. AI Integration Point (Day 5)

Symptom triage calls the **Groq API** (OpenAI-compatible chat completions). The AI is a *suggestion only* — the doctor's override is always final.

```
POST /api/queue/join (patient)
        │
        ▼
  AiTriageService.classifyWithFallback(symptomText)
        │
        ▼
  GroqTriageAiClient  ──▶  POST https://api.groq.com/openai/v1/chat/completions
        │                    model: llama-3.1-8b-instant
        │                    auth:  Bearer ${GROQ_API_KEY}  (env var only)
        │                    json mode → {"triage":"<LEVEL>","reason":"..."}
        ▼
  ├─ success  → EMERGENCY | HIGH | NORMAL | FOLLOW_UP
  └─ failure (timeout / 5xx / bad key / unparseable)
          └─▶ log warning + fall back to NORMAL   ← a broken AI call
                                                 never blocks a patient
        ▼
  QueueEntry(aiSuggestedTriage)  →  doctorOverrideTriage (nullable, final)
        ▼
  Effective triage drives ordering:
  EMERGENCY > HIGH > NORMAL > FOLLOW_UP, then FIFO by joinedAt
        ▼
  predictedWaitMinutes = (patients ahead) x avgConsultationTimeMinutes
       (recalculated on every read, never cached)
```

**Config:** `ai.groq.api-key: ${GROQ_API_KEY:}` (empty default → AI disabled gracefully), `ai.groq.model`, `ai.groq.timeout-seconds` (default 5).

---

## 8. Lazy Profile Creation Pattern

**Decision:** `user-service` does NOT get called by `auth-service` during signup. Instead, the first time a logged-in user calls `GET /api/users/me`, if no profile row exists for their `userId`, a default empty profile row is created automatically and returned.

**Rationale:**
- Keeps `auth-service` (finished and merged on Day 2) completely untouched
- Every user is guaranteed to get a profile on first use without coupling signup to profile creation
- The `role` field is denormalized into `user_profiles` for query convenience — no cross-service join needed
- The denormalized role is populated from the `X-User-Role` header on first access and can be updated if needed
