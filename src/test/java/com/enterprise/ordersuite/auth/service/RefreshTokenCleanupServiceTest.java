package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.persistence.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
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
  void cleanupNow_deletesOnlyExpiredTokens() {
    when(repo.deleteExpired(any())).thenReturn(3);

    var result = service.cleanupNow();

    assertThat(result.expiredDeleted()).isEqualTo(3);
    verify(repo).deleteExpired(Instant.parse("2026-01-29T12:00:00Z"));
    verifyNoMoreInteractions(repo);
  }

  @Test
  void cleanupNow_whenNothingExpired_returnsZero() {
    when(repo.deleteExpired(any())).thenReturn(0);

    assertThat(service.cleanupNow().expiredDeleted()).isZero();
  }
}
