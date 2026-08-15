package com.careq.auth.service;

import com.careq.auth.dto.AuthResponseDto;
import com.careq.auth.dto.LoginRequestDto;
import com.careq.auth.dto.SignupRequestDto;
import com.careq.auth.entity.AuthTokenPurpose;
import com.careq.auth.entity.Role;
import com.careq.auth.entity.User;
import com.careq.auth.exception.DuplicateEmailException;
import com.careq.auth.exception.EmailNotVerifiedException;
import com.careq.auth.exception.InvalidCredentialsException;
import com.careq.auth.exception.InvalidTokenException;
import com.careq.auth.exception.RateLimitExceededException;
import com.careq.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthServiceImpl} — signup/login (Day 2) extended on
 * Day 17 for email verification, password reset and rate limiting. The
 * service is constructed manually so the rate-limit config values are set
 * explicitly (Mockito's @InjectMocks would default them to 0 and block
 * every login).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String EMAIL = "john@careq.com";
    private static final String PASSWORD = "password123";
    private static final String FULL_NAME = "John Patient";
    private static final String ENCODED_HASH = "$2a$10$encodedhash";
    private static final String IP = "203.0.113.7";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private EmailService emailService;

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private AuditLogger auditLogger;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository, passwordEncoder, jwtService, authTokenService, emailService,
                rateLimiter, auditLogger,
                false, // emailVerificationEnabled — overridden per test where needed
                5, 15,   // login 5 / 15 min
                10, 60,  // signup 10 / 60 min
                3, 60    // email 3 / 60 min
        );
        // Lenient: only tests that exercise an endpoint touching the rate
        // limiter actually use this stub (verify/reset never call it).
        org.mockito.Mockito.lenient().when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
    }

    private SignupRequestDto signupRequest() {
        return new SignupRequestDto(FULL_NAME, EMAIL, PASSWORD, "PATIENT");
    }

    private User persistedUser() {
        return new User(EMAIL, ENCODED_HASH, FULL_NAME, Role.PATIENT);
    }

    // ── Signup ─────────────────────────────────────────────────────────

    @Test
    void signup_VerificationDisabled_ReturnsJwtAsBefore() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_HASH);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any(), eq(EMAIL), eq(FULL_NAME), eq(Role.PATIENT))).thenReturn("jwt-token");

        AuthResponseDto response = authService.signup(signupRequest(), IP);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getVerificationRequired()).isFalse();
        verify(authTokenService, never()).issue(anyString(), any(), anyInt());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void signup_VerificationEnabled_ReturnsNoTokenAndEmailsLink() {
        authService = new AuthServiceImpl(
                userRepository, passwordEncoder, jwtService, authTokenService, emailService,
                rateLimiter, auditLogger,
                true, 5, 15, 10, 60, 3, 60
        );
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_HASH);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(authTokenService.issue(anyString(), eq(AuthTokenPurpose.VERIFY_EMAIL), anyInt())).thenReturn("raw-token");

        AuthResponseDto response = authService.signup(signupRequest(), IP);

        assertThat(response.getToken()).isNull();
        assertThat(response.getVerificationRequired()).isTrue();
        assertThat(response.getMessage()).contains("Verification email sent");
        verify(authTokenService).issue(anyString(), eq(AuthTokenPurpose.VERIFY_EMAIL), anyInt());
        verify(emailService).sendVerificationEmail(EMAIL, FULL_NAME, "raw-token");
        verify(jwtService, never()).generateToken(any(), any(), any(), any());
    }

    @Test
    void signup_DuplicateEmail_ShouldThrowException() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(signupRequest(), IP))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    void signup_RateLimited_ShouldThrow429() {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.signup(signupRequest(), IP))
                .isInstanceOf(RateLimitExceededException.class);
        verify(userRepository, never()).save(any());
    }

    // ── Login ──────────────────────────────────────────────────────────

    @Test
    void login_Success_ShouldReturnAuthResponse() {
        User user = persistedUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, ENCODED_HASH)).thenReturn(true);
        when(jwtService.generateToken(eq(user.getId()), eq(EMAIL), eq(FULL_NAME), eq(Role.PATIENT))).thenReturn("jwt-token");

        AuthResponseDto response = authService.login(new LoginRequestDto(EMAIL, PASSWORD), IP);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo(EMAIL);
        assertThat(response.getRole()).isEqualTo("PATIENT");
    }

    @Test
    void login_WrongPassword_ShouldThrowException() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(persistedUser()));
        when(passwordEncoder.matches(PASSWORD, ENCODED_HASH)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequestDto(EMAIL, PASSWORD), IP))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        verify(jwtService, never()).generateToken(any(), any(), any(), any());
    }

    @Test
    void login_NonexistentEmail_ShouldThrowException() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequestDto(EMAIL, PASSWORD), IP))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_DeactivatedAccount_ShouldThrowException() {
        User user = persistedUser();
        user.setIsActive(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, ENCODED_HASH)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequestDto(EMAIL, PASSWORD), IP))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("deactivated");
    }

    @Test
    void login_RateLimited_ShouldThrow429() {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequestDto(EMAIL, PASSWORD), IP))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void login_PendingVerification_ShouldThrow403() {
        authService = new AuthServiceImpl(
                userRepository, passwordEncoder, jwtService, authTokenService, emailService,
                rateLimiter, auditLogger,
                true, 5, 15, 10, 60, 3, 60
        );
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(persistedUser()));
        when(passwordEncoder.matches(PASSWORD, ENCODED_HASH)).thenReturn(true);
        when(authTokenService.hasPendingVerification(anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequestDto(EMAIL, PASSWORD), IP))
                .isInstanceOf(EmailNotVerifiedException.class)
                .hasMessageContaining("verify your email");
        verify(jwtService, never()).generateToken(any(), any(), any(), any());
    }

    @Test
    void login_LegacyAccountWithoutTokens_PassesVerificationGate() {
        authService = new AuthServiceImpl(
                userRepository, passwordEncoder, jwtService, authTokenService, emailService,
                rateLimiter, auditLogger,
                true, 5, 15, 10, 60, 3, 60
        );
        User user = persistedUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, ENCODED_HASH)).thenReturn(true);
        when(authTokenService.hasPendingVerification(anyString())).thenReturn(false);
        when(jwtService.generateToken(eq(user.getId()), eq(EMAIL), eq(FULL_NAME), eq(Role.PATIENT))).thenReturn("jwt-token");

        AuthResponseDto response = authService.login(new LoginRequestDto(EMAIL, PASSWORD), IP);

        assertThat(response.getToken()).isEqualTo("jwt-token");
    }

    // ── Verification ───────────────────────────────────────────────────

    @Test
    void verifyEmail_ValidToken_MarksAccountVerified() {
        when(authTokenService.consume("raw-token", AuthTokenPurpose.VERIFY_EMAIL)).thenReturn(Optional.of("user-1"));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(persistedUser()));

        AuthResponseDto response = authService.verifyEmail("raw-token");

        assertThat(response.getMessage()).contains("verified");
        assertThat(response.getVerificationRequired()).isFalse();
    }

    @Test
    void verifyEmail_InvalidOrExpiredToken_Throws() {
        when(authTokenService.consume("bad-token", AuthTokenPurpose.VERIFY_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("bad-token"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("invalid or has expired");
    }

    @Test
    void resendVerification_PendingAccount_EmailsNewLink() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(persistedUser()));
        when(authTokenService.hasPendingVerification(anyString())).thenReturn(true);
        when(authTokenService.issue(anyString(), eq(AuthTokenPurpose.VERIFY_EMAIL), anyInt())).thenReturn("new-token");

        AuthResponseDto response = authService.resendVerification(EMAIL, IP);

        assertThat(response.getMessage()).contains("on its way");
        verify(emailService).sendVerificationEmail(EMAIL, FULL_NAME, "new-token");
    }

    @Test
    void resendVerification_UnknownEmail_ReturnsGenericMessage() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        AuthResponseDto response = authService.resendVerification(EMAIL, IP);

        assertThat(response.getMessage()).contains("If an account exists");
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    // ── Password reset ─────────────────────────────────────────────────

    @Test
    void forgotPassword_VerifiedAccount_EmailsResetLink() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(persistedUser()));
        when(authTokenService.hasPendingVerification(anyString())).thenReturn(false);
        when(authTokenService.issue(anyString(), eq(AuthTokenPurpose.RESET_PASSWORD), anyInt())).thenReturn("reset-token");

        AuthResponseDto response = authService.forgotPassword(EMAIL, IP);

        assertThat(response.getMessage()).contains("reset link is on its way");
        verify(emailService).sendPasswordResetEmail(EMAIL, FULL_NAME, "reset-token");
    }

    @Test
    void forgotPassword_UnknownEmail_ReturnsGenericMessage() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        AuthResponseDto response = authService.forgotPassword(EMAIL, IP);

        assertThat(response.getMessage()).contains("If an account exists");
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    void forgotPassword_UnverifiedAccount_DoesNotSendReset() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(persistedUser()));
        when(authTokenService.hasPendingVerification(anyString())).thenReturn(true);

        AuthResponseDto response = authService.forgotPassword(EMAIL, IP);

        assertThat(response.getMessage()).contains("verify your email first");
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    void resetPassword_ValidToken_UpdatesPasswordHash() {
        when(authTokenService.consume("reset-token", AuthTokenPurpose.RESET_PASSWORD)).thenReturn(Optional.of("user-1"));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(persistedUser()));
        when(passwordEncoder.encode("newPassword123")).thenReturn("$2a$10$newhash");

        AuthResponseDto response = authService.resetPassword("reset-token", "newPassword123");

        assertThat(response.getMessage()).contains("has been reset");
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(
                u -> "$2a$10$newhash".equals(u.getPasswordHash())));
    }

    @Test
    void resetPassword_InvalidToken_Throws() {
        when(authTokenService.consume("bad-token", AuthTokenPurpose.RESET_PASSWORD)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("bad-token", "newPassword123"))
                .isInstanceOf(InvalidTokenException.class);
        verify(userRepository, never()).save(any());
    }
}
