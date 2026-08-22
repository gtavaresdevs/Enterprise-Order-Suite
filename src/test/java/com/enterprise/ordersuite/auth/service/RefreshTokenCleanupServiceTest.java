package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.persistence.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupServiceTest {

  @Mock
  private RefreshTokenRepository repo;

  private Clock clock;
  private RefreshTokenCleanupService service;

  @BeforeEach
  void setUp() {
    // Fix the clock to a specific UTC time to ensure tests are highly predictable
    clock = Clock.fixed(Instant.parse("2026-01-29T12:00:00Z"), ZoneOffset.UTC);
    service = new RefreshTokenCleanupService(repo, clock);
  }

  @Test
  void cleanupNow_ShouldTriggerDeletions_WithCorrectCalculatedTimestamps() {
    // Arrange
    when(repo.deleteExpired(any())).thenReturn(3);
    when(repo.deleteUsedOrRevokedBefore(any())).thenReturn(5);

    // Act
    var result = service.cleanupNow();

    // Assert
    assertThat(result.expiredDeleted()).isEqualTo(3);
    assertThat(result.usedRevokedDeleted()).isEqualTo(5);

    // Verify exact dates were calculated and passed to the repository.
    // We use explicit expected values rather than recalculating `LocalDateTime.now(clock)`
    // in the test, ensuring the math in the service is actually verified.
    LocalDateTime expectedNow = LocalDateTime.of(2026, 1, 29, 12, 0, 0);
    LocalDateTime expectedCutoff = LocalDateTime.of(2026, 1, 22, 12, 0, 0); // 7 days prior

    verify(repo).deleteExpired(expectedNow);
    verify(repo).deleteUsedOrRevokedBefore(expectedCutoff);
    verifyNoMoreInteractions(repo);
  }

  @Test
  void cleanupNow_WhenNoTokensMatch_ShouldReturnZeroCounts() {
    // Arrange
    when(repo.deleteExpired(any())).thenReturn(0);
    when(repo.deleteUsedOrRevokedBefore(any())).thenReturn(0);

    // Act
    var result = service.cleanupNow();

    // Assert
    assertThat(result.expiredDeleted()).isZero();
    assertThat(result.usedRevokedDeleted()).isZero();

    verify(repo).deleteExpired(any());
    verify(repo).deleteUsedOrRevokedBefore(any());
    verifyNoMoreInteractions(repo);
  }
}
