package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.domain.PasswordHistory;
import com.enterprise.ordersuite.auth.domain.PasswordResetToken;
import com.enterprise.ordersuite.auth.persistence.PasswordHistoryRepository;
import com.enterprise.ordersuite.auth.persistence.PasswordResetTokenRepository;
import com.enterprise.ordersuite.auth.service.exceptions.InvalidPasswordResetTokenException;
import com.enterprise.ordersuite.auth.service.exceptions.PasswordReuseException;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.notifications.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {

  private UserRepository userRepository;
  private PasswordResetTokenRepository tokenRepository;
  private PasswordHistoryRepository passwordHistoryRepository;
  private PasswordEncoder passwordEncoder;
  private Clock clock;

  private EmailService emailService;
  private PasswordResetLinkBuilder linkBuilder;

  private PasswordResetService service;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    tokenRepository = mock(PasswordResetTokenRepository.class);
    passwordHistoryRepository = mock(PasswordHistoryRepository.class);
    passwordEncoder = mock(PasswordEncoder.class);
    emailService = mock(EmailService.class);
    linkBuilder = mock(PasswordResetLinkBuilder.class);

    clock = Clock.fixed(Instant.parse("2026-01-19T12:00:00Z"), ZoneOffset.UTC);

    when(linkBuilder.build(anyString())).thenReturn("http://localhost/reset-password?token=mock");

    service = new PasswordResetService(
      userRepository,
      tokenRepository,
      passwordHistoryRepository,
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
    verifyNoInteractions(emailService);
    verifyNoInteractions(linkBuilder);
  }

  @Test
  void requestPasswordReset_whenUserFound_returnsRawToken_andSavesHashedToken_withExpiry_andSendsEmail() {
    User user = new User();
    user.setEmail("gabriel@example.com");
    user.setActive(true);

    when(userRepository.findByEmailIgnoreCase("gabriel@example.com"))
      .thenReturn(Optional.of(user));

    ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);

    Optional<String> rawTokenOpt = service.requestPasswordReset("gabriel@example.com");

    assertThat(rawTokenOpt).isPresent();
    String rawToken = rawTokenOpt.get();
    assertThat(rawToken).isNotBlank();

    verify(tokenRepository).save(captor.capture());
    PasswordResetToken saved = captor.getValue();

    assertThat(saved.getTokenHash()).isNotBlank();
    assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
    assertThat(saved.getTokenHash()).hasSize(64); // SHA-256 hex

    LocalDateTime expectedNow = LocalDateTime.now(clock);
    assertThat(saved.getExpiresAt()).isEqualTo(expectedNow.plusMinutes(15));
    assertThat(saved.getUser()).isSameAs(user);

    verify(linkBuilder).build(eq(rawToken));
    verify(emailService).sendPasswordResetEmail(eq("gabriel@example.com"), anyString());
  }

  @Test
  void resetPassword_whenRawTokenBlank_throwsGenericInvalidToken() {
    assertThatThrownBy(() -> service.resetPassword("   ", "NewPass123!"))
      .isInstanceOf(InvalidPasswordResetTokenException.class);

    verifyNoInteractions(tokenRepository);
    verifyNoInteractions(userRepository);
    verifyNoInteractions(passwordHistoryRepository);
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
    when(prt.getUsedAt()).thenReturn(LocalDateTime.now(clock));

    when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(prt));

    assertThatThrownBy(() -> service.resetPassword("token", "NewPass123!"))
      .isInstanceOf(InvalidPasswordResetTokenException.class);

    verify(userRepository, never()).save(any());
    verify(tokenRepository, never()).save(any());
  }

  @Test
  void resetPassword_whenTokenExpired_throwsGenericInvalidToken() {
    PasswordResetToken prt = mock(PasswordResetToken.class);
    when(prt.getUsedAt()).thenReturn(null);
    when(prt.getExpiresAt()).thenReturn(LocalDateTime.now(clock).minusMinutes(1));

    when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(prt));

    assertThatThrownBy(() -> service.resetPassword("token", "NewPass123!"))
      .isInstanceOf(InvalidPasswordResetTokenException.class);

    verify(userRepository, never()).save(any());
  }

  @Test
  void resetPassword_whenNewPasswordMatchesCurrentActivePassword_throwsPasswordReuseException() {
    User user = new User();
    user.setId(1L);
    user.setEmail("gabriel@example.com");
    user.setActive(true);
    user.setPassword("hashed_current_password");

    PasswordResetToken prt = mock(PasswordResetToken.class);
    when(prt.getUsedAt()).thenReturn(null);
    when(prt.getExpiresAt()).thenReturn(LocalDateTime.now(clock).plusMinutes(10));
    when(prt.getUser()).thenReturn(user);

    when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(prt));
    when(passwordEncoder.matches("SamePassword123!", "hashed_current_password")).thenReturn(true);

    assertThatThrownBy(() -> service.resetPassword("raw-token", "SamePassword123!"))
      .isInstanceOf(PasswordReuseException.class);

    verify(userRepository, never()).save(any());
    verify(passwordHistoryRepository, never()).save(any());
  }

  @Test
  void resetPassword_whenNewPasswordMatchesHistoricalPassword_throwsPasswordReuseException() {
    User user = new User();
    user.setId(1L);
    user.setEmail("gabriel@example.com");
    user.setActive(true);
    user.setPassword("hashed_current_password");

    PasswordResetToken prt = mock(PasswordResetToken.class);
    when(prt.getUsedAt()).thenReturn(null);
    when(prt.getExpiresAt()).thenReturn(LocalDateTime.now(clock).plusMinutes(10));
    when(prt.getUser()).thenReturn(user);

    PasswordHistory historicalEntry = new PasswordHistory(user, "hashed_old_password", LocalDateTime.now(clock).minusDays(2));

    when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(prt));
    when(passwordEncoder.matches("OldPassword123!", "hashed_current_password")).thenReturn(false);
    when(passwordEncoder.matches("OldPassword123!", "hashed_old_password")).thenReturn(true);

    when(passwordHistoryRepository.findRecentByUserId(eq(1L), any(PageRequest.class)))
      .thenReturn(List.of(historicalEntry));

    assertThatThrownBy(() -> service.resetPassword("raw-token", "OldPassword123!"))
      .isInstanceOf(PasswordReuseException.class);

    verify(userRepository, never()).save(any());
    verify(passwordHistoryRepository, never()).pruneOldEntries(anyLong(), anyLong());
  }

  @Test
  void resetPassword_whenValidToken_updatesPassword_archivesOldHash_andPrunesOldEntries() {
    User user = new User();
    user.setId(1L);
    user.setEmail("gabriel@example.com");
    user.setActive(true);
    user.setPassword("hashed_old_active_password");

    PasswordResetToken prt = mock(PasswordResetToken.class);
    when(prt.getUsedAt()).thenReturn(null);
    when(prt.getExpiresAt()).thenReturn(LocalDateTime.now(clock).plusMinutes(10));
    when(prt.getUser()).thenReturn(user);

    when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(prt));
    when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
    when(passwordHistoryRepository.findRecentByUserId(eq(1L), any(PageRequest.class))).thenReturn(Collections.emptyList());
    when(passwordEncoder.encode("SecureNewPass1!_")).thenReturn("ENC(SecureNewPass1!_)");

    service.resetPassword("raw-token", "SecureNewPass1!_");

    assertThat(user.getPassword()).isEqualTo("ENC(SecureNewPass1!_)");

    ArgumentCaptor<PasswordHistory> historyCaptor = ArgumentCaptor.forClass(PasswordHistory.class);
    verify(passwordHistoryRepository).save(historyCaptor.capture());
    assertThat(historyCaptor.getValue().getPasswordHash()).isEqualTo("hashed_old_active_password");

    verify(userRepository).save(user);
    verify(prt).setUsedAt(LocalDateTime.now(clock));
    verify(tokenRepository).save(prt);

    // Fixed Parameter Evaluation Check to expect a primitive/object 'long'
    verify(passwordHistoryRepository).pruneOldEntries(eq(1L), eq(5L));
  }

  @Test
  void requestPasswordReset_whenUserInactive_returnsEmpty_andDoesNotSaveToken_orSendEmail() {
    User user = new User();
    user.setEmail("gabriel@example.com");
    user.setActive(false);

    when(userRepository.findByEmailIgnoreCase("gabriel@example.com"))
      .thenReturn(Optional.of(user));

    Optional<String> token = service.requestPasswordReset("gabriel@example.com");

    assertThat(token).isEmpty();
    verify(tokenRepository, never()).save(any());
    verifyNoInteractions(emailService);
  }

  @Test
  void resetPassword_whenUserInactive_throwsGenericInvalidToken_andDoesNotPersist() {
    User user = new User();
    user.setEmail("gabriel@example.com");
    user.setActive(false);
    user.setPassword("old");

    PasswordResetToken prt = mock(PasswordResetToken.class);
    when(prt.getUsedAt()).thenReturn(null);
    when(prt.getExpiresAt()).thenReturn(LocalDateTime.now(clock).plusMinutes(10));
    when(prt.getUser()).thenReturn(user);

    when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(prt));

    assertThatThrownBy(() -> service.resetPassword("raw-token", "NewPass123!"))
      .isInstanceOf(InvalidPasswordResetTokenException.class);

    verify(userRepository, never()).save(any());
    verify(tokenRepository, never()).save(any(PasswordResetToken.class));
    verify(prt, never()).setUsedAt(any());
  }
}
