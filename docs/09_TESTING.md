# CareQ — Testing Documentation

**Version:** 1.10 (Day 10 — Final Test Report + bug-fix follow-up)  
**Status:** Complete — the full Day 10 testing pass is delivered: **104 backend tests** (auth 14, user 13, doctor 38, queue 39) + **23 frontend component tests**, 2 critical integration flows, a Newman API run (31/31), and a manually executed role-journey checklist (**38/38 — both Day 10 bugs BUG-1 and BUG-2 are now FIXED**).

---

## 1. Auth Service Unit Tests (Day 2 — built Day 10)

`AuthServiceImplTest` — **6 tests**, JUnit 5 + Mockito. `JwtServiceTest` — **4 tests** against a real JWT secret (no mocking, so the round-trip is genuinely verified). All passing.

### Test Class: `AuthServiceImplTest`

| Test | Description | Expected Outcome |
|------|-------------|-----------------|
| `signup_Success_ShouldReturnAuthResponse` | Valid signup request with unique email creates user and returns JWT | Returns `AuthResponseDto` with non-null token, correct email, correct role |
| `signup_DuplicateEmail_ShouldThrowException` | Signup with already-registered email | Throws `DuplicateEmailException` with "already exists" message |
| `login_Success_ShouldReturnAuthResponse` | Correct email + password returns valid JWT | Returns `AuthResponseDto` with non-null token, correct user info |
| `login_WrongPassword_ShouldThrowException` | Correct email but wrong password | Throws `InvalidCredentialsException` with "Invalid email or password" message |
| `login_NonexistentEmail_ShouldThrowException` | Email not found in database | Throws `InvalidCredentialsException` with "Invalid email or password" message |
| `login_DeactivatedAccount_ShouldThrowException` | Account with `isActive = false` | Throws `InvalidCredentialsException` with "Account is deactivated" message |

---

## 2. JwtService Unit Tests (Day 2 — built Day 10)

| Test | Description | Expected Outcome |
|------|-------------|-----------------|
| `generateToken_ShouldProduceValidToken` | Token generated with valid inputs can be parsed back | `extractUserId`, `extractEmail`, `extractRole` return correct values |
| `isTokenValid_ExpiredToken_ShouldReturnFalse` | An expired token returns false | `isTokenValid` returns `false` |
| `isTokenValid_TamperedToken_ShouldReturnFalse` | A modified token fails validation | `isTokenValid` returns `false` |
| `isTokenValid_GarbageToken_ShouldReturnFalse` | `"not-a-real-jwt"`, `""`, `null` | `isTokenValid` returns `false` |

---

## 3. User Service Unit Tests (Day 3 — built Day 10)

### Test Class: `UserProfileServiceImplTest` (8 tests, all passing)

These tests use JUnit 5 with Mockito to test the service layer in isolation.

| Test | Description | Expected Outcome |
|------|-------------|-----------------|
| `getOrCreateProfile_WhenNotExists_CreatesAndReturnsProfile` | First call for a userId with no existing profile | Creates a new `UserProfile` with default empty fields, returns it |
| `getOrCreateProfile_WhenExists_ReturnsExistingProfile` | Subsequent call for an existing userId | Returns existing profile without creating a new one |
| `getOrCreateProfile_BackfillsMissingNameAndEmail` | Profile from before Day 7a (no name/email stored) | Name/email backfilled from JWT claims and saved |
| `updateProfile_WhenExists_UpdatesFieldsAndReturns` | Valid update request for an existing profile | Fields are updated, non-null fields only, returns updated profile |
| `updateProfile_WhenNotExists_ThrowsException` | Update for a userId with no profile | Throws `UserProfileNotFoundException` |
| `updateProfile_PartialUpdate_OnlyUpdatesNonNullFields` | Update with only some fields set | Only the provided fields are changed, others remain intact |
| `getProfileByUserId_WhenExists_ReturnsProfile` | Lookup by userId for an existing profile | Returns the correct profile |
| `getProfileByUserId_WhenNotExists_ThrowsException` | Lookup by userId with no profile | Throws `UserProfileNotFoundException` |

### Test Class: `UserProfileControllerTest` (5 tests, all passing)

These tests verify controller behavior including the Admin-only restriction. Standalone MockMvc wired with the service's own `GlobalExceptionHandler`; identity headers are simulated directly (the gateway normally injects them).

| Test | Description | Expected Outcome |
|------|-------------|-----------------|
| `getMyProfile_WithoutHeaders_ShouldReturn400` | Missing X-User-Id header | Returns 400 Bad Request |
| `getMyProfile_WithValidHeaders_ShouldReturn200` | Valid headers, lazy-create if needed | Returns 200 OK with profile data |
| `updateMyProfile_WithValidPayload_ShouldReturn200` | Valid update payload | Returns 200 OK with updated profile |
| `getUserProfile_AsAdmin_ShouldReturn200` | Admin role, valid target userId | Returns 200 OK with profile data |
| `getUserProfile_AsPatient_ShouldReturn403` | Patient role attempting admin endpoint | Returns 403 Forbidden |

### Mock Setup

- `UserProfileRepository` — mocked with Mockito
- `UserProfileService` — mocked for controller tests

### Test Data

```java
private static final String TEST_USER_ID = "550e8400-e29b-41d4-a716-446655440000";
private static final String TEST_ROLE = "PATIENT";
private static final String TEST_PHONE = "+1234567890";
private static final String TEST_ADDRESS = "123 Main St";
```

---

## 4. Doctor Service Unit Tests (Day 4 — built Day 10)

> **Note:** `spring-boot-starter-test` had to be added to `doctor-service/pom.xml` (it was missing — the only service without it).

### Test Class: `DepartmentServiceImplTest` (9 tests, all passing)

These tests use JUnit 5 with Mockito to test the department service layer in isolation.

| Test | Description | Expected Outcome |
|------|-------------|-----------------|
| `getAllDepartments_ShouldReturnList` | Fetch all departments | Returns list of all departments |
| `getDepartmentById_WhenExists_ShouldReturnDepartment` | Valid department ID | Returns department with correct data |
| `getDepartmentById_WhenNotExists_ShouldThrowException` | Invalid department ID | Throws `DepartmentNotFoundException` |
| `createDepartment_WithUniqueName_ShouldCreate` | Valid request with unique name | Creates and returns department |
| `createDepartment_WithDuplicateName_ShouldThrowException` | Duplicate department name | Throws `IllegalArgumentException` |
| `updateDepartment_WhenExists_ShouldUpdate` | Valid update on existing department | Updates and returns department |
| `updateDepartment_WhenNotExists_ShouldThrowException` | Update on non-existent department | Throws `DepartmentNotFoundException` |
| `deleteDepartment_WhenExists_ShouldDelete` | Delete existing department | Deletes successfully |
| `deleteDepartment_WhenNotExists_ShouldThrowException` | Delete non-existent department | Throws `DepartmentNotFoundException` |

### Test Class: `DoctorCatalogServiceImplTest` (15 tests, all passing)

These tests verify the doctor catalog service logic including filters and availability toggle. Also covers pagination clamping (size ≤ 100) and sorting direction.

| Test | Description | Expected Outcome |
|------|-------------|-----------------|
| `getAllDoctors_WithoutFilters_ShouldReturnAll` | No filters applied | Returns all doctor entries |
| `getAllDoctors_WithDepartmentFilter_ShouldReturnFiltered` | Filter by departmentId | Returns only doctors in that department |
| `getAllDoctors_WithSpecializationFilter_ShouldReturnFiltered` | Filter by specialization | Returns only doctors matching specialization (case-insensitive) |
| `getAllDoctors_WithBothFilters_ShouldReturnFiltered` | Both departmentId and specialization | Returns intersection of both filters |
| `getDoctorById_WhenExists_ShouldReturnEntry` | Valid doctor ID | Returns doctor with correct data |
| `getDoctorById_WhenNotExists_ShouldThrowException` | Invalid doctor ID | Throws `DoctorCatalogNotFoundException` |
| `createDoctor_WithUniqueUserId_ShouldCreate` | Valid request with unique userId | Creates and returns entry |
| `createDoctor_WithDuplicateUserId_ShouldThrowException` | Duplicate userId | Throws `DuplicateDoctorCatalogEntryException` |
| `createDoctor_WithInvalidDepartment_ShouldThrowException` | Non-existent departmentId | Throws `DepartmentNotFoundException` *(Day 10 note: the doc originally said `IllegalArgumentException`; the real implementation throws `DepartmentNotFoundException`, so the test asserts the real behavior)* |
| `updateDoctor_WhenExists_ShouldUpdate` | Valid update on existing entry | Updates and returns entry |
| `deleteDoctor_WhenExists_ShouldDelete` | Delete existing entry | Deletes successfully |
| `toggleAvailability_WhenExists_ShouldToggle` | Toggle availability for valid userId | Returns entry with flipped `isAvailable` |
| `toggleAvailability_WhenNotExists_ShouldThrowException` | Toggle for userId with no catalog entry | Throws `DoctorCatalogNotFoundException` |

### Test Class: `DataSeederTest` (2 tests, all passing — Day 10 follow-up, BUG-2b)

| Test | Description | Expected Outcome |
|------|-------------|-----------------|
| `run_EmptyTable_CreatesAllTenDefaultDepartments` | Departments table is empty on first boot | All 10 default departments created |
| `run_PartiallyDeletedTable_HealsOnlyTheMissingDepartments` | Cardiology exists, other 9 missing (observed BUG-2 case) | Only the 9 missing defaults are created; the existing one is left untouched |

### Test Class: `DepartmentControllerTest` (5 tests, all passing)

These tests verify controller behavior including Admin-only restrictions. Standalone MockMvc + `GlobalExceptionHandler`; `X-User-Role` simulated directly.

| Test | Description | Expected Outcome |
|------|-------------|-----------------|
| `getAllDepartments_ShouldReturn200` | Any authenticated user | Returns 200 OK with list |
| `createDepartment_AsAdmin_ShouldReturn201` | Admin creates department | Returns 201 Created |
| `createDepartment_AsNonAdmin_ShouldReturn403` | Patient/Doctor tries to create | Returns 403 Forbidden |
| `updateDepartment_AsAdmin_ShouldReturn200` | Admin updates department | Returns 200 OK |
| `deleteDepartment_AsAdmin_ShouldReturn204` | Admin deletes department | Returns 204 No Content |

### Test Class: `DoctorCatalogControllerTest` (7 tests, all passing)

Standalone MockMvc + `GlobalExceptionHandler`; identity headers simulated directly. Includes a `deleteDoctor_AsAdmin_ShouldReturn204` case.

| Test | Description | Expected Outcome |
|------|-------------|-----------------|
| `getAllDoctors_ShouldReturn200` | Any authenticated user | Returns 200 OK with list |
| `getDoctorById_ShouldReturn200` | Valid ID | Returns 200 OK |
| `createDoctor_AsAdmin_ShouldReturn201` | Admin creates doctor entry | Returns 201 Created |
| `createDoctor_AsNonAdmin_ShouldReturn403` | Patient/Doctor tries to create | Returns 403 Forbidden |
| `toggleAvailability_AsDoctor_ShouldReturn200` | Doctor toggles own availability | Returns 200 OK with updated entry |
| `toggleAvailability_AsNonDoctor_ShouldReturn403` | Patient/Admin tries to toggle | Returns 403 Forbidden |

### Mock Setup

- `DepartmentRepository` — mocked with Mockito
- `DoctorCatalogRepository` — mocked with Mockito
- `DepartmentService` — mocked for controller tests
- `DoctorCatalogService` — mocked for controller tests

---

## 5. Queue Service Unit Tests (Day 5)

These tests use JUnit 5 with Mockito. `doctor-service` is simulated by mocking the Feign client; the AI client is mocked so failure paths are asserted directly. **33 unit tests, all passing** (Day 7a added the patient-name search test; Day 7b added three analytics tests; Day 10 added 5 integration tests — see section 6).

### Test Class: `QueueOrderingServiceTest`

| Test | Description | Expected Outcome |
|------|-------------|-----------------|
| `orderByEffectivePriority_SortsByTriageThenFifo` | Mixed triage levels | Order is EMERGENCY > HIGH > NORMAL (FIFO) > FOLLOW_UP |
| `orderByEffectivePriority_FifoWithinSameTriageLevel` | Same level, different joinedAt | Earliest joined first |
| `orderByEffectivePriority_DoctorOverrideIsFinal` | Doctor overrides NORMAL → EMERGENCY | Overridden entry outranks AI-emergency entry (FIFO within level) |
| `orderByEffectivePriority_ExcludesCompletedAndCancelled` | COMPLETED/CANCELLED present | Only active entries remain in the ordered list |
| `effectiveTriage_PrefersDoctorOverride` | Override set / not set | Returns override when present, else AI value |
| `positionOf_ReturnsOneBasedPosition` | Ordered list | Position 1 = next to be seen |
| `predictedWaitMinutes_PatientsAheadTimesAvgTime` | Position 3, avg 15 | `(3-1) x 15 = 30`; position 1 → 0; null avg → 0 |

### Test Class: `AiTriageServiceTest` (fallback-to-NORMAL — the critical path)

| Test | Description | Expected Outcome |
|------|-------------|-----------------|
| `classify_Success_ReturnsAiLevel` | Mock AI returns EMERGENCY | Returns EMERGENCY |
| `classify_ClientThrows_FallsBackToNormal` | Mock AI throws (timeout/network/5xx) | Returns NORMAL, no exception propagates |
| `classify_UnparseableResponse_FallsBackToNormal` | Mock AI returns `Optional.empty()` | Returns NORMAL |
| `classify_ClientReturnsNull_FallsBackToNormal` | Mock AI returns `null` | Returns NORMAL |

> **AI-failure path is a first-class test target** — the fallback is verified by deliberately breaking the AI client, not just by testing the happy path.

### Test Class: `QueueServiceImplTest`

| Test | Description | Expected Outcome |
|------|-------------|-----------------|
| `joinQueue_Success_ReturnsEntryWithPositionAndPredictedWait` | Feign + AI mocked success | Entry saved, position 1, wait 0, effectiveTriage from AI |
| `joinQueue_UnavailableDoctor_Throws` | `isAvailable=false` via Feign | Throws `DoctorUnavailableException`, no save |
| `joinQueue_DuplicateActiveEntry_Throws` | Patient already active for the doctor | Throws `DuplicateQueueEntryException` |
| `overrideTriage_Success_ChangesEffectiveTriage` | Doctor overrides NORMAL → EMERGENCY | Override persisted, effectiveTriage = EMERGENCY |
| `overrideTriage_CompletedEntry_Throws` | Override on COMPLETED entry | Throws `InvalidQueueStateException` |
| `overrideTriage_AnotherDoctor_Throws` | Non-owner doctor tries | Throws `UnauthorizedAccessException` |
| `callNext_ThenComplete_AdvancesAndFinishesEntry` | WAITING → IN_PROGRESS → COMPLETED | `calledAt`/`completedAt` set; completed entry has no position |
| `complete_NotInProgress_Throws` | Complete on WAITING entry | Throws `InvalidQueueStateException` |
| `getDoctorQueue_WithPatientNameSearch_FiltersRows` (Day 7a) | Search `"alice"` against a 2-patient queue | Only Alice returned; blank search returns all rows |
| `getAnalyticsSummary_EmptyData_ReturnsZeroFilledSevenDayWindow` (Day 7b) | Empty `queue_entries` for the window | 7 zero-filled days, null avg waits, empty distribution — never crashes on sparse data |
| `getAnalyticsSummary_PopulatedData_AggregatesAndMapsDepartments` (Day 7b) | Mocked per-day counts/waits + Feign catalog | Counts land on the right days; avg waits null on empty days; departments mapped & sorted desc |
| `getAnalyticsSummary_DoctorServiceDown_DistributionDegradesToEmpty` (Day 7b) | Feign throws (doctor-service down) | Time series still return; only the department slice degrades to empty |

### Mock Setup

- `TriageAiClient` — mocked (simulates AI success/failure/null)
- `DoctorServiceClient` (Feign) — mocked (simulates doctor-service responses)
- `QueueEntryRepository` — mocked with Mockito
- `QueueOrderingService` — real instance (pure logic)

---

## 6. Integration Tests (Day 10)

Two critical end-to-end flows were built with `@SpringBootTest` + `MockMvc`.

**Choice: H2 (MySQL mode) instead of Testcontainers** — CareQ's JPA model is portable, the MySQL-native analytics queries are deliberately not exercised by these flows, and H2 keeps the suite runnable on any machine with zero Docker dependency. External calls (the Groq LLM and the doctor-service Feign catalog) are mocked at the bean layer per the Day 10 scope decision; the real controllers, services, transactions, JPA repositories and the real `AiTriageService` fallback wrapper all run.

### Flow A — auth round-trip (`AuthFlowIntegrationTest`, auth-service, 4 tests)

| Test | What it proves |
|------|----------------|
| `flowA_signupLoginAndJwtGrantsAccessToProtectedRoute` | Signup → 201 with real JWT; duplicate email → 409; login → 200; wrong password → 401; **the login JWT actually unlocks a protected route** (passes the `authenticated()` rule), while a missing or tampered token is rejected by Spring Security (403) |
| `signupValidation_BlankFields_Returns400WithSharedErrorShape` | 400 with the shared `path` + `validationErrors` shape |
| `login_UnknownEmail_Returns401` | Unknown account → 401 |
| `unknownRoute_Returns404WithSharedErrorShape` (BUG-1) | **Unmapped route → 404** with the shared error shape, not the catch-all's 500 |

### Flow B — full queue lifecycle (`QueueFlowIntegrationTest`, queue-service, 6 tests)

| Test | What it proves |
|------|----------------|
| `flowB_fullLifecycle_joinToCompletedWithWaitTimeAndHistory` | Patient joins → 201 with AI triage assigned, `position=1`, `predictedWaitMinutes` present → `my-status` active → doctor queue view → call-next (`IN_PROGRESS` + `calledAt`) → complete (`COMPLETED` + `completedAt`) → visit appears in patient history |
| `join_doctorServiceUnreachable_Returns503NotUnhandled500` | **Feign failure during join surfaces as a graceful 503**, never an unhandled 500 (the single most important queue failure path) |
| `join_samePatientSecondActiveEntry_Returns409` | Duplicate active entry → 409 |
| `join_nonPatientRole_Returns403` | Role guard enforced |
| `join_blankSymptomText_Returns400WithValidationErrors` | Bean validation → 400 + `validationErrors` |
| `unknownRoute_Returns404WithSharedErrorShape` (BUG-1) | **Unmapped route → 404** with the shared error shape, not the catch-all's 500 |

## 6.1 API Testing — Postman / Newman (Day 10, leveraging Day 9's collection)

The full Day 9 Postman collection (31 requests across all 4 business services, auto-auth token script) was run against the live stack with Newman:

| Metric | Result |
|--------|--------|
| Requests executed | **31 / 31 (0 failed)** |
| Auto-auth token extraction | ✅ verified — protected endpoints (e.g. admin `/api/queue/live`) returned 200 with the extracted JWT |
| Run report | regenerable via `newman run` — `postman/test-report.json` is gitignored (results summarized in this table) |
| Avg response time | 27ms (min 9ms, max 89ms) |

Note: two queue-mutation requests (`cancel`/`complete` on entry id 1) returned 400/404 because that entry's state was consumed by an earlier run — expected for a stateful collection; the collection assertions still passed.

## 6.2 UI Testing — Vitest + React Testing Library (Day 10)

**23 tests across 4 files, all passing** (`npm test`). This is a light, risk-prioritized pass — not full coverage (full Playwright E2E remains the existing `npm run test:e2e` suite).

| File | Tests | Covers |
|------|-------|--------|
| `LoginPage.test.tsx` | 3 | Renders the form; submits credentials and navigates to the role dashboard; shows the error banner on failure |
| `ProtectedRoute.test.tsx` | 4 | Loading placeholder; redirect to `/login` when unauthenticated; role mismatch redirects to the user's own dashboard; renders children when allowed |
| `StatusTag.test.tsx` | 12 | Correct label + color class for every triage/status/availability value; dot toggle; unknown-value fallback |
| `RtkHookStates.test.tsx` | 4 | One RTK Query hook's loading / success / error states render correctly in a consuming component (real Provider wrapper, hook mocked at module level) + `getErrorMessage` normalization (incl. field-level `validationErrors`) |

> **Known environment note:** driving the real RTK Query `fetchBaseQuery` against a stubbed global `fetch` in jsdom is blocked by an undici/jsdom realm mismatch — RTK v2.12 constructs `new Request(...)` with jsdom's `AbortSignal`, which undici rejects (`Expected signal to be an instance of AbortSignal`). The hook-state test therefore mocks the hook at module level; this is documented here rather than silently worked around in production code.

## 6.3 Manual Testing — executed against the live stack (Day 10)

The checklist was **executed, not just written** — `scripts/day10-manual-test.mjs` drove every journey through the API Gateway against the running 6-service stack. **38 of 38 checks passed** (S3, the unknown-route check, passes after the BUG-1 fix).

| Journey | Checks | Result |
|---------|--------|--------|
| **Patient** (signup → browse doctors → join queue → track status → history → leave queue) | P1–P14 | ✅ all pass — incl. duplicate signup 409, wrong password 401, wait-time calculation (2nd patient = position 2, wait = avg consult time), duplicate join 409, cancel own 200, cancel another's 403, AI fallback to NORMAL live-verified (no GROQ_API_KEY) |
| **Doctor** (login → view queue → search → override triage → call next → complete) | D0–D10 | ✅ all pass — incl. availability toggle, patient-name search, override jumps patient to position 1, non-owning doctor 403, call-next on non-WAITING 400 |
| **Admin** (login → live overview → analytics → manage departments → manage users) | A1–A9 | ✅ all pass — incl. department CRUD (201/200/204), 7-day analytics window, admin-only user list, patient blocked 403 |
| **Security / edge paths** | S1–S4 | ✅ 4/4 — invalid JWT 401 ✅, patient on admin endpoint 403 ✅, **unknown route returns 404** ✅ (BUG-1 fixed), departments browsable 200 ✅ |

## 6.4 Bug List (Day 10)

| # | Bug | Severity | Status |
|---|-----|----------|--------|
| BUG-1 | **Unmapped routes return HTTP 500 instead of 404.** Every service's `GlobalExceptionHandler` has `@ExceptionHandler(Exception.class)`, which intercepts Spring's `NoResourceFoundException` (thrown when no handler matches) and converts it to 500. Verified live: `GET /api/auth/nonexistent-path` → 500. | Low (no core flow hits unmapped routes) | ✅ **FIXED** — dedicated `NoResourceFoundException` handler → 404 with the shared error shape added to **all four** services' `GlobalExceptionHandler`; regression tests added to Flow A and Flow B (auth `unknownRoute_Returns404WithSharedErrorShape`, queue `unknownRoute_Returns404WithSharedErrorShape`). GitHub issue #8. |
| BUG-2 | **`scripts/seed-data.sh` is not idempotent across DB resets.** (a) After an auth-DB reset, catalog entries point at deleted userIds and are never re-created (existing-user signups are skipped, so the catalog step no-ops) — orphaned entries. (b) doctor-service `DataSeeder` skips seeding when the departments table is non-empty, so a partially deleted table (observed: Cardiology missing) never heals. | Low (dev/demo tooling only; fresh installs correct) | ✅ **FIXED** — (a) `seed-data.sh` now falls back to **login** when a signup fails (recovering the current userId), **skips** catalog entries that already exist for a seed userId, and **deletes orphaned** catalog entries whose userId no longer matches a seed doctor; (b) `DataSeeder` now **heals missing departments** (creates only the missing defaults via `existsByName`) instead of skipping whenever the table is non-empty. GitHub issue #9. |

### 6.4.1 Bug-Fix Verification (Day 10 follow-up)

- **BUG-1:** `mvn test` passes with the new `unknownRoute_Returns404WithSharedErrorShape` tests in auth-service (Flow A) and queue-service (Flow B) — both assert 404 + the shared `path`/`status`/`error` shape.
- **BUG-2a:** the manual checklist runner (`scripts/day10-manual-test.mjs`) S3 check now passes — **38/38**. The seed script itself now reconciles on every run (login fallback + skip existing + orphan delete); the one-off resync repair script was removed as redundant.
- **BUG-2b:** `DataSeeder` self-heals a partially deleted `departments` table on restart (verified by the idempotent `existsByName` guard); re-running `bash scripts/seed-data.sh` against a live stack is now a safe no-op for already-seeded data.

## 6.5 Explicitly Out of Scope (Phase 2 roadmap)

- Full E2E browser automation (Cypress) — the existing **Playwright** suite (`npm run test:e2e`) covers the E2E tier today; Cypress is not added
- Load / performance testing
- 100% code coverage — coverage is risk-prioritized, not exhaustive

---

## 7. Running Tests

```bash
# Run all backend tests across all modules (104 tests, incl. integration)
cd backend
mvn test

# Run a single service
cd backend/queue-service
mvn test

# Run just the integration tests (Flow A / Flow B)
mvn test -pl auth-service -Dtest=AuthFlowIntegrationTest
mvn test -pl queue-service -Dtest=QueueFlowIntegrationTest

# Run the AI fallback unit tests
mvn test -pl queue-service -Dtest=AiTriageServiceTest

# Frontend component tests (Vitest, 23 tests)
cd frontend
npm test

# Frontend E2E (Playwright — separate tier)
npm run test:e2e

# API testing (Newman, against a running stack)
cd postman
npx newman run CareQ.postman_collection.json -e CareQ.postman_environment.json

# Manual testing checklist (against a running stack)
node scripts/day10-manual-test.mjs
```

---

## 8. Test Coverage Target

- **Service layer:** ≥ 70% (ADF Section 9 requirement) — every service's core service classes are unit-tested
- **Controller layer:** ≥ 50% (unit MockMvc tests in user/doctor/queue + integration flows in auth/queue)
- **Utility classes (JwtService):** ≥ 80%
- **Highest-risk logic** (AI fallback, JWT validation, queue ordering, wait-time, Feign failure): explicitly and deeply tested
