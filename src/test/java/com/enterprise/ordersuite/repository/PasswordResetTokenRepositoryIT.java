package com.enterprise.ordersuite.repository;

import com.enterprise.ordersuite.entities.auth.PasswordResetToken;
import com.enterprise.ordersuite.entities.user.User;
import com.enterprise.ordersuite.repositories.PasswordResetTokenRepository;
import com.enterprise.ordersuite.repositories.UserRepository;
import com.enterprise.ordersuite.services.auth.PasswordResetLinkBuilder;
import com.enterprise.ordersuite.services.auth.PasswordResetService;
import com.enterprise.ordersuite.services.auth.exceptions.InvalidPasswordResetTokenException;
import com.enterprise.ordersuite.services.notification.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {

    private UserRepository userRepository;
    private PasswordResetTokenRepository tokenRepository;
    private PasswordEncoder passwordEncoder;
    private Clock clock;

    // NEW deps introduced in step 1.5
    private EmailService emailService;
    private PasswordResetLinkBuilder linkBuilder;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        emailService = mock(EmailService.class);
        linkBuilder = mock(PasswordResetLinkBuilder.class);

        clock = Clock.fixed(Instant.parse("2026-01-19T12:00:00Z"), ZoneOffset.UTC);

        // Service will call linkBuilder.build(rawToken) and then emailService.sendPasswordResetEmail(...)
        when(linkBuilder.build(anyString())).thenReturn("http://localhost/reset-password?token=mock");

        service = new PasswordResetService(
                userRepository,
                tokenRepository,
                passwordEncoder,
                clock,
                emailService,
                linkBuilder
        );
    }

    @Test
    void requestPasswordReset_whenUserNotFound_returnsEmpty_andDoesNotSaveToken() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com"))
                .thenReturn(Optional.empty());

        Optional<String> token = service.requestPasswordReset("missing@example.com");

        assertThat(token).isEmpty();
        verify(tokenRepository, never()).save(any());

        // Also ensure no email is attempted
        verifyNoInteractions(emailService);
        verifyNoInteractions(linkBuilder);
    }

    @Test
    void requestPasswordReset_whenUserFound_returnsRawToken_andSavesHashedToken_withExpiry_andSendsEmail() {
        User user = new User();
        user.setEmail("gabriel@example.com");

        when(userRepository.findByEmailIgnoreCase("gabriel@example.com"))
                .thenReturn(Optional.of(user));

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);

        Optional<String> rawTokenOpt = service.requestPasswordReset("gabriel@example.com");

        assertThat(rawTokenOpt).isPresent();
        String rawToken = rawTokenOpt.get();
        assertThat(rawToken).isNotBlank();

        verify(tokenRepository).save(captor.capture());
        PasswordResetToken saved = captor.getValue();

        // raw token should NOT be stored
        assertThat(saved.getTokenHash()).isNotBlank();
        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(saved.getTokenHash()).hasSize(64); // SHA-256 hex

        // expiry should be now + 15 minutes (EXPIRY_MINUTES)
        LocalDateTime expectedNow = LocalDateTime.now(clock);
        assertThat(saved.getExpiresAt()).isEqualTo(expectedNow.plusMinutes(15));

        assertThat(saved.getUser()).isSameAs(user);

        // Email should be triggered using the built link
        verify(linkBuilder).build(eq(rawToken));
        verify(emailService).sendPasswordResetEmail(eq("gabriel@example.com"), anyString());
    }

    @Test
    void resetPassword_whenRawTokenBlank_throwsGenericInvalidToken() {
        assertThatThrownBy(() -> service.resetPassword("   ", "NewPass123!"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);

        verifyNoInteractions(tokenRepository);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(emailService);
        verifyNoInteractions(linkBuilder);
    }

    @Test
    void resetPassword_whenTokenNotFound_throwsGenericInvalidToken() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("token", "NewPass123!"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);

        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).save(any(PasswordResetToken.class));
    }

    @Test
    void resetPassword_whenTokenUsed_throwsGenericInvalidToken() {
        PasswordResetToken prt = mock(PasswordResetToken.class);
        when(prt.isUsed()).thenReturn(true);

        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(prt));

        assertThatThrownBy(() -> service.resetPassword("token", "NewPass123!"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);

        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).save(prt);
    }

    @Test
    void resetPassword_whenTokenExpired_throwsGenericInvalidToken() {
        PasswordResetToken prt = mock(PasswordResetToken.class);
        when(prt.isUsed()).thenReturn(false);
        when(prt.getExpiresAt()).thenReturn(LocalDateTime.now(clock).minusMinutes(1));

        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(prt));

        assertThatThrownBy(() -> service.resetPassword("token", "NewPass123!"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);

        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).save(prt);
    }

    @Test
    void resetPassword_whenValidToken_updatesPassword_marksTokenUsed_andPersists() {
        User user = new User();
        user.setEmail("gabriel@example.com");
        user.setPassword("old");

        PasswordResetToken prt = mock(PasswordResetToken.class);
        when(prt.isUsed()).thenReturn(false);
        when(prt.getExpiresAt()).thenReturn(LocalDateTime.now(clock).plusMinutes(10));
        when(prt.getUser()).thenReturn(user);

        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(prt));
        when(passwordEncoder.encode("NewPass123!")).thenReturn("ENC(NewPass123!)");

        service.resetPassword("raw-token", "NewPass123!");

        assertThat(user.getPassword()).isEqualTo("ENC(NewPass123!)");

        verify(userRepository).save(user);
        verify(prt).setUsedAt(LocalDateTime.now(clock));
        verify(tokenRepository).save(prt);

        // Reset flow does not send email during reset, only during request
        verifyNoInteractions(emailService);
        verifyNoInteractions(linkBuilder);
    }
}
