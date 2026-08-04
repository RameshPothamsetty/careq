# CareQ — Testing Documentation

**Version:** 1.7 (Day 7b)  
**Status:** Updated — Auth + User + Doctor + Queue Module Tests (23 queue-service tests, all passing)

---

## 1. Auth Service Unit Tests (Day 2)

The following unit tests are planned for `AuthServiceImpl`. These tests use JUnit 5 with Mockito and Spring Security test utilities.

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

## 2. JwtService Unit Tests (Day 2)

| Test | Description | Expected Outcome |
|------|-------------|-----------------|
| `generateToken_ShouldProduceValidToken` | Token generated with valid inputs can be parsed back | `extractUserId`, `extractEmail`, `extractRole` return correct values |
| `isTokenValid_ExpiredToken_ShouldReturnFalse` | An expired token returns false | `isTokenValid` returns `false` |
| `isTokenValid_TamperedToken_ShouldReturnFalse` | A modified token fails validation | `isTokenValid` returns `false` |

---

## 3. User Service Unit Tests (Day 3)

### Test Class: `UserProfileServiceImplTest`

These tests use JUnit 5 with Mockito to test the service layer in isolation.

| Test | Description | Expected Outcome |
|------|-------------|-----------------|
| `getOrCreateProfile_WhenNotExists_CreatesAndReturnsProfile` | First call for a userId with no existing profile | Creates a new `UserProfile` with default empty fields, returns it |
| `getOrCreateProfile_WhenExists_ReturnsExistingProfile` | Subsequent call for an existing userId | Returns existing profile without creating a new one |
| `updateProfile_WhenExists_UpdatesFieldsAndReturns` | Valid update request for an existing profile | Fields are updated, non-null fields only, returns updated profile |
| `updateProfile_WhenNotExists_ThrowsException` | Update for a userId with no profile | Throws `UserProfileNotFoundException` |
| `updateProfile_PartialUpdate_OnlyUpdatesNonNullFields` | Update with only some fields set | Only the provided fields are changed, others remain intact |
| `getProfileByUserId_WhenExists_ReturnsProfile` | Lookup by userId for an existing profile | Returns the correct profile |
| `getProfileByUserId_WhenNotExists_ThrowsException` | Lookup by userId with no profile | Throws `UserProfileNotFoundException` |

### Test Class: `UserProfileControllerTest`

These tests verify controller behavior including the Admin-only restriction.

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

## 4. Doctor Service Unit Tests (Day 4)

### Test Class: `DepartmentServiceImplTest`

These tests use JUnit 5 with Mockito to test the department service layer in isolation.

| Test | Description | Expected Outcome |
|------|-------------|-----------------|
| `getAllDepartments_ShouldReturnList` | Fetch all departments | Returns list of all departments |
| `getDepartmentById_WhenExists_ShouldReturnDepartment` | Valid department ID | Returns department with correct data |
| `getDepartmentById_WhenNotExists_ShouldThrowException` | Invalid department ID | Throws `DepartmentNotFoundException` |
| `createDepartment_WithUniqueName_ShouldCreate` | Valid request with unique name | Creates and returns department |
| `createDepartment_WithDuplicateName_ShouldThrowException` | Duplicate department name | Throws `IllegalArgumentException` |
| `updateDepartment_WhenExists_ShouldUpdate` | Valid update on existing department | Updates and returns department |
| `deleteDepartment_WhenExists_ShouldDelete` | Delete existing department | Deletes successfully |
| `deleteDepartment_WhenNotExists_ShouldThrowException` | Delete non-existent department | Throws `DepartmentNotFoundException` |

### Test Class: `DoctorCatalogServiceImplTest`

These tests verify the doctor catalog service logic including filters and availability toggle.

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
| `createDoctor_WithInvalidDepartment_ShouldThrowException` | Non-existent departmentId | Throws `IllegalArgumentException` |
| `updateDoctor_WhenExists_ShouldUpdate` | Valid update on existing entry | Updates and returns entry |
| `deleteDoctor_WhenExists_ShouldDelete` | Delete existing entry | Deletes successfully |
| `toggleAvailability_WhenExists_ShouldToggle` | Toggle availability for valid userId | Returns entry with flipped `isAvailable` |
| `toggleAvailability_WhenNotExists_ShouldThrowException` | Toggle for userId with no catalog entry | Throws `DoctorCatalogNotFoundException` |

### Test Class: `DepartmentControllerTest`

These tests verify controller behavior including Admin-only restrictions.

| Test | Description | Expected Outcome |
|------|-------------|-----------------|
| `getAllDepartments_ShouldReturn200` | Any authenticated user | Returns 200 OK with list |
| `createDepartment_AsAdmin_ShouldReturn201` | Admin creates department | Returns 201 Created |
| `createDepartment_AsNonAdmin_ShouldReturn403` | Patient/Doctor tries to create | Returns 403 Forbidden |
| `updateDepartment_AsAdmin_ShouldReturn200` | Admin updates department | Returns 200 OK |
| `deleteDepartment_AsAdmin_ShouldReturn204` | Admin deletes department | Returns 204 No Content |

### Test Class: `DoctorCatalogControllerTest`

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

These tests use JUnit 5 with Mockito. `doctor-service` is simulated by mocking the Feign client; the AI client is mocked so failure paths are asserted directly. **23 tests, all passing** (Day 7a added the patient-name search test; Day 7b added three analytics tests).

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

## 6. Integration / API Tests (Future Day)

> **Note:** Integration tests requiring a running MySQL instance and full microservice stack will be added on the dedicated testing day later in the checklist. These will include:
>
> - `POST /api/auth/signup` — full HTTP round-trip
> - `POST /api/auth/login` — full HTTP round-trip
> - Role-based access enforcement via API Gateway
> - Duplicate email, invalid credentials, validation error response shapes
> - `GET /api/users/me` — lazy-create verification
> - `PUT /api/users/me` — profile update verification
> - `GET /api/users/{id}` — Admin-only restriction verification

---

## 7. Running Tests

```bash
# Run all auth-service tests
cd backend/auth-service
mvn test

# Run all user-service tests
cd backend/user-service
mvn test

# Run queue-service tests (Day 5)
cd backend/queue-service
mvn test

# Run a specific test class (queue-service)
mvn test -Dtest=AiTriageServiceTest

# Run all tests across all modules
cd backend
mvn test
```

---

## 8. Test Coverage Target

- **Service layer:** ≥ 70% (ADF Section 9 requirement)
- **Controller layer:** ≥ 50% (via integration tests on testing day)
- **Utility classes (JwtService):** ≥ 80%
