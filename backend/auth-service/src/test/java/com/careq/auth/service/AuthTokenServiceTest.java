package com.careq.auth.service;

import com.careq.auth.entity.AuthToken;
import com.careq.auth.entity.AuthTokenPurpose;
import com.careq.auth.repository.AuthTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Day 17 — one-time token lifecycle: issue stores only a SHA-256 hash,
 * consume works exactly once, expiry/purpose mismatches are rejected.
 */
@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    @Mock
    private AuthTokenRepository authTokenRepository;

    @InjectMocks
    private AuthTokenService authTokenService;

    @Test
    void issue_StoresHashNotRawToken() {
        when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String raw = authTokenService.issue("user-1", AuthTokenPurpose.VERIFY_EMAIL, 1440);

        ArgumentCaptor<AuthToken> captor = ArgumentCaptor.forClass(AuthToken.class);
        verify(authTokenRepository).save(captor.capture());
        AuthToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getPurpose()).isEqualTo(AuthTokenPurpose.VERIFY_EMAIL);
        // The DB row must contain the digest, never the raw token.
        assertThat(saved.getTokenHash()).isEqualTo(authTokenService.sha256(raw));
        assertThat(saved.getTokenHash()).isNotEqualTo(raw);
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
        // A fresh issue invalidates any previous token of the same purpose.
        verify(authTokenRepository).deleteByUserIdAndPurpose("user-1", AuthTokenPurpose.VERIFY_EMAIL);
    }

    @Test
    void consume_ValidToken_ReturnsUserIdAndMarksUsed() {
        AuthToken stored = new AuthToken("user-1", AuthTokenPurpose.VERIFY_EMAIL, authTokenService.sha256("raw"), LocalDateTime.now().plusHours(1));
        when(authTokenRepository.findByTokenHashAndPurpose(authTokenService.sha256("raw"), AuthTokenPurpose.VERIFY_EMAIL))
                .thenReturn(Optional.of(stored));

        Optional<String> userId = authTokenService.consume("raw", AuthTokenPurpose.VERIFY_EMAIL);

        assertThat(userId).contains("user-1");
        assertThat(stored.getUsedAt()).isNotNull();
    }

    @Test
    void consume_ExpiredToken_ReturnsEmpty() {
        AuthToken stored = new AuthToken("user-1", AuthTokenPurpose.VERIFY_EMAIL, authTokenService.sha256("raw"), LocalDateTime.now().minusMinutes(5));
        when(authTokenRepository.findByTokenHashAndPurpose(authTokenService.sha256("raw"), AuthTokenPurpose.VERIFY_EMAIL))
                .thenReturn(Optional.of(stored));

        assertThat(authTokenService.consume("raw", AuthTokenPurpose.VERIFY_EMAIL)).isEmpty();
        assertThat(stored.getUsedAt()).isNull();
    }

    @Test
    void consume_AlreadyUsedToken_ReturnsEmpty() {
        AuthToken stored = new AuthToken("user-1", AuthTokenPurpose.VERIFY_EMAIL, authTokenService.sha256("raw"), LocalDateTime.now().plusHours(1));
        stored.setUsedAt(LocalDateTime.now());
        when(authTokenRepository.findByTokenHashAndPurpose(authTokenService.sha256("raw"), AuthTokenPurpose.VERIFY_EMAIL))
                .thenReturn(Optional.of(stored));

        assertThat(authTokenService.consume("raw", AuthTokenPurpose.VERIFY_EMAIL)).isEmpty();
    }

    @Test
    void consume_WrongPurpose_ReturnsEmpty() {
        // The service hashes the raw token, then looks up by hash + purpose.
        when(authTokenRepository.findByTokenHashAndPurpose(authTokenService.sha256("raw"), AuthTokenPurpose.RESET_PASSWORD))
                .thenReturn(Optional.empty());

        assertThat(authTokenService.consume("raw", AuthTokenPurpose.RESET_PASSWORD)).isEmpty();
        verify(authTokenRepository, never()).save(any());
    }

    @Test
    void consume_BlankToken_ReturnsEmpty() {
        assertThat(authTokenService.consume("", AuthTokenPurpose.VERIFY_EMAIL)).isEmpty();
        verify(authTokenRepository, never()).findByTokenHashAndPurpose(eq(""), any());
    }
}
