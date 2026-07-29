# CareQ — Testing Documentation

**Version:** 1.3 (Day 3)  
**Status:** Updated — Auth Module + User Module Tests

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

## 4. Integration / API Tests (Future Day)

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

## 5. Running Tests

```bash
# Run all auth-service tests
cd backend/auth-service
mvn test

# Run all user-service tests
cd backend/user-service
mvn test

# Run a specific test class (user-service)
mvn test -Dtest=UserProfileServiceImplTest

# Run all tests across all modules
cd backend
mvn test
```

---

## 6. Test Coverage Target

- **Service layer:** ≥ 70% (ADF Section 9 requirement)
- **Controller layer:** ≥ 50% (via integration tests on testing day)
- **Utility classes (JwtService):** ≥ 80%
