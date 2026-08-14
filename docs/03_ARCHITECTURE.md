# CareQ — Architecture Document

**Version:** 1.9 (Day 16)  
**Status:** Updated — Web Push (VAPID) delivery with per-user preferences (notification-service), on top of Day 13's Redis catalog caching + RabbitMQ event bus

---

## 1. High-Level Architecture Diagram

```
                         ┌─────────────────────────────────┐
                         │           React SPA             │
                         │   (Vite + React Router v6)      │
                         │   (RTK Query server-state)      │
                         └──────────────┬──────────────────┘
                                        │ HTTP (fetch / RTK Query)
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
   └────────┬─────────┘  └────────┬─────────┘  └───────┬─────────┘
            │                     │                    │   Redis (:6379)
            │                     │                    │   careq:doctorCatalog
            │                     │                    │   careq:departments
            │                     │                    │   (60s TTL cache)
            └──────────────┬──────┴────────────────────┘
                           │
                  ┌──────────────────┐      RabbitMQ (:5672, mgmt :15672)
                  │  Queue Service   │      careq.events topic exchange
                  │   (port 8084)    │      queue.joined / queue.triaged
                  │                  │────▶  queue.called  / queue.completed
                  │  /api/queue/**   │      ───────────▶┐
                  │  + AI triage    │                   ▼
                  │  (Groq LLM)     │       ┌──────────────────────────┐
                  └───────┬───┬─────┘       │   Notification Service   │
                          │   │  Feign      │        (port 8085)       │
                          │   └──────────▶  │  consumes careq.notifications,
                          │   doctor-service│  persists notification_entries,
                          │                 │  serves /api/notifications/**
                  ┌──────────────────┐      └──────────────────────────┘
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
| **notification-service** | Consumes RabbitMQ queue events, persists in-app notifications, serves them to the caller's bell, and delivers Web Push (VAPID) to registered browsers | `notification_entries`, `push_subscriptions`, `notification_preferences` | Yes |

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
| `/api/notifications/**` | notification-service | Yes | PATIENT, DOCTOR, ADMIN | Day 13 — persisted in-app notifications |
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

---

## 9. Frontend State Management (RTK Query)

**Decision:** all *server data* calls (profile, departments, doctors, queue) run through **RTK Query** slices; the hand-rolled `fetch` helpers were removed from `src/services/api.ts`. `api.ts` now keeps only the shared **type contract** (re-exported to slices and screens) plus `login`/`signup`, which remain session-state calls owned by `AuthContext`.

**File layout:**

| File | Responsibility |
|------|---------------|
| `src/store.ts` | Redux store — registers the three slices + middleware; exports typed `useAppDispatch`/`useAppSelector` and `resetApiState()` |
| `src/services/rtk/baseQuery.ts` | Single shared `fetchBaseQuery` with the JWT injected centrally (`careq_token` from localStorage) + `getErrorMessage()` error normalizer matching the backend `{ message, details }` shape |
| `src/services/rtk/userApi.ts` | user-service profile endpoints (`GET`/`PUT /api/users/me`, multipart picture upload) |
| `src/services/rtk/doctorApi.ts` | doctor-service endpoints: department CRUD, doctor catalog browse/CRUD, availability toggle |
| `src/services/rtk/queueApi.ts` | queue-service endpoints: join, my-status, doctor queue, override/call-next/complete, admin live overview, analytics summary |
| `src/services/rtk/notificationApi.ts` | notification-service endpoints: paginated `GET /api/notifications/me` (polled by the bell) + `PUT /api/notifications/{id}/read` (Day 13); delivery preferences + push subscription endpoints (Day 16) |
| `src/hooks/useWebPush.ts` | Day 16: Web Push lifecycle — permission prompt → SW registration → `pushManager.subscribe(VAPID)` → backend subscription + preference calls; drives the bell toggle |
| `src/hooks/useQueueNotifications.ts` | Isolated hook watching the my-status polling; emits derived notification events on tracked transitions |
| `src/context/NotificationContext.tsx` | Day 13: slimmed to the real-time TOAST layer only (the bell now reads persisted notifications from notification-service) |

**Key decisions:**
- **Two kinds of state:** session state (user / role / token) stays in `AuthContext` + localStorage; server data lives in the RTK Query cache. `AuthContext.logout()` calls `resetApiState()` so one session's cached data never leaks into the next.
- **Tag invalidation instead of manual refetch:** e.g. `joinQueue`, `callNext`, and the admin CRUD mutations `invalidatesTags` the affected lists, so screens update automatically after a write — no `fetchQueue()`-style calls remain.
- **Polling replaces `setInterval`:** the live queue screens (`PatientQueuePage`, `DoctorQueuePage`, `AdminQueueOverview`) pass `pollingInterval: 10_000` to their query hooks; `dataUpdatedAt` drives the `LiveBadge` staleness indicator, and `refetch()` is the manual refresh.
- **Dependent queries** (e.g. `DoctorQueuePage` needs the doctor's catalog id before fetching their queue) use the `skipToken` option so the second query only fires once the first resolves.
- **Centralized auth header:** `baseQuery.prepareHeaders` reads the token from localStorage — the same source of truth `AuthContext` writes on login — keeping the header logic out of every screen.

---

## 10. Admin Analytics — SQL Aggregation, Not In-Memory (Day 7b)

**Decision:** `GET /api/queue/analytics/summary` aggregates the `queue_entries` table in **MySQL, not in Java**. The repository uses native queries with `GROUP BY DATE(...)`/`TIMESTAMPDIFF`, returning only per-day/per-doctor rollups via lightweight interface projections. The dataset grows with every queue entry, so pulling all rows into memory to count in Java would not scale.

```
queue-service                     MySQL (careq_db)
    │  countCompletedPerDaySince    │
    │  avgCalledWaitPerDaySince     │
    │  countCompletedPerDoctorSince │
    ├────────────────────────────────▶  GROUP BY DATE(completed_at / called_at)
    │◀────────────────────────────────  per-day counts / AVG waits / per-doctor counts
    │
    │  departmentDistribution:  per-doctor counts mapped to department names
    │  via DoctorServiceClient.getAllDoctors (Feign — single source of truth)
    └─▶ AnalyticsSummaryDto (7-day zero-filled window)
```

**Definitions (documented in the API contract):** patients handled = entries *completed* that day; avg wait = `called_at − joined_at` for entries *called* that day; department distribution = completed entries grouped by department. The 7-day window is zero-filled in the service (bounded work — 7 rows) so charts always render a full window, and an empty dataset returns 200 with zeros rather than erroring.

**Resilience:** if doctor-service is unreachable the department slice degrades to an empty list (logged) while the two time series still return — a single-service outage does not blank the whole dashboard.

---

## 11. Client-Side Derived Notifications (Day 7b)

**Decision:** notifications are **derived client-side from the existing polling**, not a new persisted backend system. The frontend already polls `GET /api/queue/my-status` every 10 s (RTK Query `pollingInterval`); `useQueueNotifications` watches that same cache entry and emits an event on tracked transitions:

| Transition | Event |
|-----------|-------|
| inactive → active | "You joined Dr. X's queue" |
| `WAITING` → `IN_PROGRESS` | "It's your turn!" — the flagship case (doctor called the patient) |
| `WAITING`, position decreased | "You moved up to position #N" |
| active → gone / `COMPLETED` | "Consultation complete" |

Events land in `NotificationContext` (a session-only React store) which powers: the notification bell + unread badge + dropdown in the shared `QueuePageHeader`, and auto-dismissing toasts via `ToastHost`. One poll, many subscribers: the My Queue screen and the notification hook share the same RTK Query cache entry.

**Known, deliberate limitation (not a bug):** the history is **client-side and session-only** — it resets on page refresh and is cleared when the signed-in user changes. There is no backend notification store and no cross-device delivery.

> **Day 13 — this limitation is now removed.** The Phase 2 roadmap item was deliberately scoped out on Day 7b and built properly later: see § 12 (RabbitMQ event-driven notification-service) below. The Day 7b toast layer remains as the immediate-feedback layer; the bell's history is now real persisted data.

---

## 12. Event-Driven Notifications — RabbitMQ + notification-service (Day 13)

**Decision:** Day 7b's "client-side, session-only" notification limitation is replaced by a proper persisted system, while the real-time toast behavior is kept as an immediate-feedback layer on top.

### Topology

```
queue-service (publisher)                 notification-service (consumer)
────────────────────────────              ────────────────────────────────
careq.events (durable topic exchange)  ─▶ careq.notifications (durable queue)
  routing keys:                           binding: queue.* (covers all four)
  queue.joined     (join / auto-assign)
  queue.triaged    (doctor triage override)
  queue.called     (call next)
  queue.completed  (complete consultation)
```

- **One topic exchange `careq.events`**, declared (idempotently) by both services so either may start first. **Single publisher** (queue-service) and **single consumer** (notification-service) — no competing consumers, no event storms.
- **Event payload** (`QueueEventDto`, duplicated in both services — this codebase has no shared module; the contract is the JSON field names): `eventType`, `recipientUserId` (the patient), `queueEntryId`, `patientName`, `doctorName`, `departmentName`, `position`, `triageLevel`, `timestamp`. JSON serialization via `Jackson2JsonMessageConverter` on both sides.
- **Non-blocking contract (hard rule):** publishing happens AFTER the decision is persisted and every publish is wrapped in try/catch inside `QueueEventPublisher` — a RabbitMQ outage only logs a warning; a patient joining, being called, or completing must NEVER fail because a notification couldn't be published. Unit-tested (`QueueEventPublisherTest`).
- **AI triage stays synchronous** — RabbitMQ carries post-decision notification events only, never the Auto-Assignment decision path.

### notification-service

- Consumes the four routing keys, composes a human-readable `message` per event type, and persists a `NotificationEntry` (`recipientUserId`, `type`, `message`, `read`, `createdAt`) in the shared `careq_db`.
- Exposes `GET /api/notifications/me` (paginated, caller's own rows only, plus a total `unreadCount` for the badge) and `PUT /api/notifications/{id}/read` (ownership-checked: recipient or ADMIN). Same gateway header-trust identity pattern as every other service; registered with Eureka; routed at `/api/notifications/**`.
- **Frontend:** the bell polls `GET /api/notifications/me` every 15s (persisted history survives refresh); the Day 7b toast-on-status-change stays as the instant-feedback layer. Mark-all-read issues one PUT per unread row on the loaded page (bounded by page size — deliberately no bulk endpoint).

---

## 13. Redis Catalog Caching (Day 13)

**Decision:** `doctor-service` caches the two read-heavy, rarely-changing catalog endpoints in Redis with a **60s TTL**: `GET /api/doctors` (`@Cacheable(cacheNames="doctorCatalog")`, keyed by every filter/pagination param) and `GET /api/departments`. Every Admin create/update/delete mutation — plus the doctor's own availability toggle and department renames (department names appear inside the cached doctor list) — `@CacheEvict`s the affected cache(s) so stale data never lingers.

**Scope boundary (hard rule):** live queue data is NEVER cached. Position, predicted wait, and — critically — `isAvailable` are always re-read fresh: `getDoctorById` (the endpoint queue-service Feign-calls to validate joins) is deliberately NOT cached, so a doctor going offline blocks new joins immediately. Within the cached *list* page, availability may be up to 60s stale — an acceptable, documented tradeoff (a toggled doctor's own entry evicts the cache immediately; the join path never trusts the cached availability).

**Resilience:** a custom `CacheErrorHandler` logs and swallows every Redis failure — if Redis is briefly unreachable, reads fall through to the database and evictions are skipped; the catalog never 500s because the cache layer is down. Keys are namespaced `careq:doctorCatalog::…` / `careq:departments::…` for direct inspection with `redis-cli KEYS careq:*`.

**Also Day 13:** `GET /api/doctors/me` resolves the calling doctor's own catalog entry by header identity. The doctor dashboard and queue page now use it instead of scanning the whole paginated catalog (which silently broke once the catalog outgrew one page, and was the root cause of the misleading "no catalog entry — ask an admin" dead-end); the Admin doctor form gained a DOCTOR-account picker so user IDs are selected, never hand-typed.

---

## 14. Web Push Delivery (Day 16) — VAPID + per-user preferences

**Decision:** the Phase 2 roadmap item "real delivery" is implemented as **browser Web Push via VAPID** — the only channel that is truly free at scale (the browser's own push service, FCM/APNs/Mozilla, does the heavy lifting; no third-party account or API key is required, only a VAPID keypair). Email/SMS remain future channels: the preference + delivery architecture below is channel-agnostic by design (a per-user `webPushEnabled` flag and a registry of destinations), so adding e.g. a Resend email transport later means adding a transport class and a flag — not re-architecting.

### Topology

```
SPA (patient browser)                 notification-service (backend)
─────────────────────                 ────────────────────────────────
bell toggle:  permission →            GET/PUT /api/notifications/preferences
  register /sw.js                     POST/DELETE /api/notifications/push/subscriptions
  pushManager.subscribe(VAPID pub) ─▶ push_subscriptions (endpoint + keys)
        │                                                        ▲
        │  encrypted payload {type, message}                     │
        │◀──────────────────── WebPushDeliveryService ───────────┘
        │                     (@Async, on every queue event)
        ▼
  service worker → OS notification ("It's your turn!", …)
```

### Design decisions

- **Delivery is fire-and-forget and fail-open (hard rule, matching Day 13's non-blocking contract):** the RabbitMQ consumer persists the in-app `NotificationEntry` FIRST (the source of truth), then calls `WebPushDeliveryService.deliverAsync` on a dedicated `webPushExecutor` thread pool. Delivery never blocks the consumer's ack and never propagates failures — a slow push service, a DB hiccup, or missing VAPID keys only log. The same philosophy as an empty `GROQ_API_KEY`: **no keys → delivery silently disabled, everything else keeps working.**
- **No recipient-resolution step needed:** unlike email/SMS, a push destination IS the browser's subscription — the SPA registers it (keyed by the gateway's `X-User-Id`) at enable time, so the async consumer only reads its own tables. No cross-service Feign calls on the delivery path.
- **Per-user preferences:** `notification_preferences` (one row per user, upsert). A missing row means defaults ON — an existing patient who never opened settings still receives pushes once they grant the browser permission. The preference is the user's opt-out; the browser permission is the separate OS-level opt-in — **both must be true**. The bell toggle reflects the EFFECTIVE state (preference AND a live subscription), so it never claims "on" when only half the setup exists.
- **Subscription lifecycle:** upsert by endpoint (a re-subscribe, or a session switch on a shared browser, re-assigns the row — delivery never goes to the wrong person). Dead endpoints are self-cleaned during delivery: a 404/410 from the push service deletes the row so we stop paying for it.
- **VAPID keys are env-driven:** `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` (base64url, from `scripts/generate-vapid-keys.sh`) with `VAPID_SUBJECT` (mailto). The PUBLIC key is ALSO baked into the frontend build as `VITE_VAPID_PUBLIC_KEY` (same pattern as `VITE_API_BASE_URL`); without it the bell toggle is hidden entirely. The payload is the same `{type, message}` pair the in-app bell shows; the service worker maps `type` → title (queue.called is sent with `Urgency: high` so it can break through Do-Not-Disturb).
- **Security:** `web-push` 5.1.1's transitive BouncyCastle (jdk15on 1.61, known CVEs) and httpclient are explicitly overridden with `bcprov/bcpkix-jdk18on 1.78.1` and `httpclient 4.5.14` in `notification-service/pom.xml`. The VAPID private key never leaves the backend.
- **Browser support caveat:** push requires a secure context (HTTPS — or localhost in dev) and the user's permission; iOS Safari supports it only from 16.4+. The feature degrades gracefully: unsupported browsers simply don't show the toggle.
