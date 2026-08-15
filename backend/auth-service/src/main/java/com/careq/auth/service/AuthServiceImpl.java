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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
public class AuthServiceImpl implements AuthService {

    private static final int VERIFY_TTL_MINUTES = 24 * 60;   // 24h
    private static final int RESET_TTL_MINUTES = 30;          // 30 min

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthTokenService authTokenService;
    private final EmailService emailService;
    private final RateLimiter rateLimiter;
    private final AuditLogger auditLogger;

    private final boolean emailVerificationEnabled;
    private final int loginMax;
    private final int loginWindowMinutes;
    private final int signupMax;
    private final int signupWindowMinutes;
    private final int emailMax;
    private final int emailWindowMinutes;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           AuthTokenService authTokenService,
                           EmailService emailService,
                           RateLimiter rateLimiter,
                           AuditLogger auditLogger,
                           @Value("${app.auth.email-verification-enabled:false}") boolean emailVerificationEnabled,
                           @Value("${app.rate-limit.login-max:5}") int loginMax,
                           @Value("${app.rate-limit.login-window-minutes:15}") int loginWindowMinutes,
                           @Value("${app.rate-limit.signup-max:10}") int signupMax,
                           @Value("${app.rate-limit.signup-window-minutes:60}") int signupWindowMinutes,
                           @Value("${app.rate-limit.email-max:3}") int emailMax,
                           @Value("${app.rate-limit.email-window-minutes:60}") int emailWindowMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authTokenService = authTokenService;
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
        this.auditLogger = auditLogger;
        this.emailVerificationEnabled = emailVerificationEnabled;
        this.loginMax = loginMax;
        this.loginWindowMinutes = loginWindowMinutes;
        this.signupMax = signupMax;
        this.signupWindowMinutes = signupWindowMinutes;
        this.emailMax = emailMax;
        this.emailWindowMinutes = emailWindowMinutes;
    }

    @Override
    @Transactional
    public AuthResponseDto signup(SignupRequestDto request, String clientIp) {
        String key = "signup:" + clientIp;
        if (!rateLimiter.tryAcquire(key, signupMax, Duration.ofMinutes(signupWindowMinutes))) {
            auditLogger.log("signup", request.getEmail(), clientIp, "rate_limited");
            throw new RateLimitExceededException(
                    "Too many signups from this address. Please try again in about an hour.");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(
                    "An account with email '" + request.getEmail() + "' already exists"
            );
        }

        Role role = Role.valueOf(request.getRole().toUpperCase());

        User user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getFullName(),
                role
        );
        user = userRepository.save(user);

        if (emailVerificationEnabled) {
            // No JWT yet — the account is gated until the email link is consumed.
            String rawToken = authTokenService.issue(user.getId(), AuthTokenPurpose.VERIFY_EMAIL, VERIFY_TTL_MINUTES);
            emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), rawToken);
            auditLogger.log("signup", user.getEmail(), clientIp, "verification_pending");

            AuthResponseDto response = new AuthResponseDto(null, user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
            response.setMessage("Verification email sent to " + user.getEmail() + " — click the link in it to activate your account.");
            response.setVerificationRequired(true);
            return response;
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
        auditLogger.log("signup", user.getEmail(), clientIp, "verification_disabled");
        return new AuthResponseDto(token, user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponseDto login(LoginRequestDto request, String clientIp) {
        // Brute-force guard: per (email, IP) and per IP, before any work.
        if (!rateLimiter.tryAcquire("login:" + request.getEmail() + ":" + clientIp, loginMax, Duration.ofMinutes(loginWindowMinutes))
                || !rateLimiter.tryAcquire("login-ip:" + clientIp, loginMax * 4, Duration.ofMinutes(loginWindowMinutes))) {
            auditLogger.log("login", request.getEmail(), clientIp, "rate_limited");
            throw new RateLimitExceededException(
                    "Too many login attempts. Please try again in " + loginWindowMinutes + " minutes.");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    auditLogger.log("login_failed", request.getEmail(), clientIp, "unknown_email");
                    return new InvalidCredentialsException("Invalid email or password");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            auditLogger.log("login_failed", request.getEmail(), clientIp, "wrong_password");
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (!user.getIsActive()) {
            auditLogger.log("login_failed", request.getEmail(), clientIp, "deactivated");
            throw new InvalidCredentialsException("Account is deactivated. Please contact an administrator.");
        }

        if (emailVerificationEnabled && authTokenService.hasPendingVerification(user.getId())) {
            auditLogger.log("login_blocked", user.getEmail(), clientIp, "email_not_verified");
            throw new EmailNotVerifiedException(
                    "Please verify your email before signing in — check your inbox (and spam) for the verification link, or request a new one.");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
        auditLogger.log("login_success", user.getEmail(), clientIp, "-");
        return new AuthResponseDto(token, user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
    }

    @Override
    @Transactional
    public AuthResponseDto verifyEmail(String rawToken) {
        return authTokenService.consume(rawToken, AuthTokenPurpose.VERIFY_EMAIL)
                .map(userId -> {
                    String email = userRepository.findById(userId).map(User::getEmail).orElse("unknown");
                    auditLogger.log("verify_email", email, "-", "success");
                    AuthResponseDto response = new AuthResponseDto(null, userId, email, null, null);
                    response.setMessage("Your email has been verified — you can now sign in.");
                    response.setVerificationRequired(false);
                    return response;
                })
                .orElseThrow(() -> new InvalidTokenException(
                        "This verification link is invalid or has expired. Request a new one from the sign-in screen."));
    }

    @Override
    @Transactional
    public AuthResponseDto resendVerification(String email, String clientIp) {
        if (!rateLimiter.tryAcquire("resend:" + email, emailMax, Duration.ofMinutes(emailWindowMinutes))) {
            auditLogger.log("resend_verification", email, clientIp, "rate_limited");
            throw new RateLimitExceededException(
                    "Too many resend requests. Please wait a while before trying again.");
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            auditLogger.log("resend_verification", email, clientIp, "unknown_email");
            return genericResponse("If an account exists for " + email + " and is awaiting verification, a new link is on its way.");
        }
        if (!authTokenService.hasPendingVerification(user.getId())) {
            auditLogger.log("resend_verification", email, clientIp, "already_verified");
            return genericResponse("This email is already verified — you can sign in now.");
        }

        String rawToken = authTokenService.issue(user.getId(), AuthTokenPurpose.VERIFY_EMAIL, VERIFY_TTL_MINUTES);
        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), rawToken);
        auditLogger.log("resend_verification", email, clientIp, "sent");
        return genericResponse("A new verification email is on its way to " + email + ".");
    }

    @Override
    @Transactional
    public AuthResponseDto forgotPassword(String email, String clientIp) {
        if (!rateLimiter.tryAcquire("forgot:" + email, emailMax, Duration.ofMinutes(emailWindowMinutes))) {
            auditLogger.log("forgot_password", email, clientIp, "rate_limited");
            throw new RateLimitExceededException(
                    "Too many reset requests. Please wait a while before trying again.");
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            auditLogger.log("forgot_password", email, clientIp, "unknown_email");
            return genericResponse("If an account exists for " + email + ", a reset link is on its way.");
        }
        if (authTokenService.hasPendingVerification(user.getId())) {
            auditLogger.log("forgot_password", email, clientIp, "unverified_account");
            return genericResponse("Please verify your email first — check your inbox for the verification link, then request a password reset.");
        }

        String rawToken = authTokenService.issue(user.getId(), AuthTokenPurpose.RESET_PASSWORD, RESET_TTL_MINUTES);
        emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), rawToken);
        auditLogger.log("forgot_password", email, clientIp, "sent");
        return genericResponse("If an account exists for " + email + ", a reset link is on its way.");
    }

    @Override
    @Transactional
    public AuthResponseDto resetPassword(String token, String newPassword) {
        String userId = authTokenService.consume(token, AuthTokenPurpose.RESET_PASSWORD)
                .orElseThrow(() -> new InvalidTokenException(
                        "This reset link is invalid or has expired. Request a new one."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("This reset link is invalid or has expired. Request a new one."));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        auditLogger.log("reset_password", user.getEmail(), "-", "success");
        return genericResponse("Your password has been reset — you can now sign in.");
    }

    private AuthResponseDto genericResponse(String message) {
        AuthResponseDto response = new AuthResponseDto(null, null, null, null, null);
        response.setMessage(message);
        response.setVerificationRequired(false);
        return response;
    }
}
