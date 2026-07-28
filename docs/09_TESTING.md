# CareQ — Testing Documentation

**Version:** 1.0 (Day 2)  
**Status:** Initial — Auth Module Tests

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

### Mock Setup

- `UserRepository` — mocked with Mockito
- `PasswordEncoder` — mocked to return/compare predictable values
- `JwtService` — mocked to return a fixed token string

### Test Data

```java
private static final String TEST_EMAIL = "patient@test.com";
private static final String TEST_PASSWORD = "password123";
private static final String TEST_NAME = "Test Patient";
private static final Role TEST_ROLE = Role.PATIENT;
private static final String TEST_TOKEN = "test-jwt-token";
```

---

## 2. JwtService Unit Tests (Day 2)

| Test | Description | Expected Outcome |
|------|-------------|-----------------|
| `generateToken_ShouldProduceValidToken` | Token generated with valid inputs can be parsed back | `extractUserId`, `extractEmail`, `extractRole` return correct values |
| `isTokenValid_ExpiredToken_ShouldReturnFalse` | An expired token returns false | `isTokenValid` returns `false` |
| `isTokenValid_TamperedToken_ShouldReturnFalse` | A modified token fails validation | `isTokenValid` returns `false` |

---

## 3. Integration / API Tests (Future Day)

> **Note:** Integration tests requiring a running MySQL instance and full microservice stack will be added on the dedicated testing day later in the checklist. These will include:
>
> - `POST /api/auth/signup` — full HTTP round-trip
> - `POST /api/auth/login` — full HTTP round-trip
> - Role-based access enforcement via API Gateway
> - Duplicate email, invalid credentials, validation error response shapes

---

## 4. Running Tests

```bash
# Run all auth-service tests
cd backend/auth-service
mvn test

# Run a specific test class
mvn test -Dtest=AuthServiceImplTest
```

---

## 5. Test Coverage Target

- **Service layer:** ≥ 70% (ADF Section 9 requirement)
- **Controller layer:** ≥ 50% (via integration tests on testing day)
- **Utility classes (JwtService):** ≥ 80%
