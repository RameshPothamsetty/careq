package com.careq.auth.service;

import com.careq.auth.dto.AuthResponseDto;
import com.careq.auth.dto.LoginRequestDto;
import com.careq.auth.dto.SignupRequestDto;
import com.careq.auth.entity.Role;
import com.careq.auth.entity.User;
import com.careq.auth.exception.DuplicateEmailException;
import com.careq.auth.exception.InvalidCredentialsException;
import com.careq.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthServiceImpl} (Day 2 plan, built Day 10).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String EMAIL = "john@careq.com";
    private static final String PASSWORD = "password123";
    private static final String FULL_NAME = "John Patient";
    private static final String ENCODED_HASH = "$2a$10$encodedhash";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    private SignupRequestDto signupRequest() {
        return new SignupRequestDto(FULL_NAME, EMAIL, PASSWORD, "PATIENT");
    }

    private User persistedUser() {
        return new User(EMAIL, ENCODED_HASH, FULL_NAME, Role.PATIENT);
    }

    @Test
    void signup_Success_ShouldReturnAuthResponse() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_HASH);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any(), eq(EMAIL), eq(FULL_NAME), eq(Role.PATIENT)))
                .thenReturn("jwt-token");

        AuthResponseDto response = authService.signup(signupRequest());

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUserId()).isNotBlank();
        assertThat(response.getEmail()).isEqualTo(EMAIL);
        assertThat(response.getFullName()).isEqualTo(FULL_NAME);
        assertThat(response.getRole()).isEqualTo("PATIENT");

        verify(passwordEncoder).encode(PASSWORD);
        verify(jwtService).generateToken(eq(response.getUserId()), eq(EMAIL), eq(FULL_NAME), eq(Role.PATIENT));
    }

    @Test
    void signup_DuplicateEmail_ShouldThrowException() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(signupRequest()))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
        verify(jwtService, never()).generateToken(any(), any(), any(), any());
    }

    @Test
    void login_Success_ShouldReturnAuthResponse() {
        User user = persistedUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, ENCODED_HASH)).thenReturn(true);
        when(jwtService.generateToken(eq(user.getId()), eq(EMAIL), eq(FULL_NAME), eq(Role.PATIENT)))
                .thenReturn("jwt-token");

        AuthResponseDto response = authService.login(new LoginRequestDto(EMAIL, PASSWORD));

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUserId()).isEqualTo(user.getId());
        assertThat(response.getEmail()).isEqualTo(EMAIL);
        assertThat(response.getRole()).isEqualTo("PATIENT");
    }

    @Test
    void login_WrongPassword_ShouldThrowException() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(persistedUser()));
        when(passwordEncoder.matches(PASSWORD, ENCODED_HASH)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequestDto(EMAIL, PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(jwtService, never()).generateToken(any(), any(), any(), any());
    }

    @Test
    void login_NonexistentEmail_ShouldThrowException() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequestDto(EMAIL, PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_DeactivatedAccount_ShouldThrowException() {
        User user = persistedUser();
        user.setIsActive(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, ENCODED_HASH)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequestDto(EMAIL, PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("deactivated");

        verify(jwtService, never()).generateToken(any(), any(), any(), any());
    }
}
