package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.persistence.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RefreshTokenCleanupServiceTest {

    private RefreshTokenRepository repo;
    private Clock clock;
    private RefreshTokenCleanupService service;

    @BeforeEach
    void setUp() {
        repo = mock(RefreshTokenRepository.class);
        clock = Clock.fixed(Instant.parse("2026-01-29T12:00:00Z"), ZoneOffset.UTC);
        service = new RefreshTokenCleanupService(repo, clock);
    }

    @Test
    void cleanupNow_deletesExpired_and_usedOrRevokedBeforeCutoff() {
        when(repo.deleteExpired(any())).thenReturn(3);
        when(repo.deleteUsedOrRevokedBefore(any())).thenReturn(5);

        var result = service.cleanupNow();

        assertThat(result.expiredDeleted()).isEqualTo(3);
        assertThat(result.usedRevokedDeleted()).isEqualTo(5);

        verify(repo).deleteExpired(eq(LocalDateTime.now(clock)));
        verify(repo).deleteUsedOrRevokedBefore(eq(LocalDateTime.now(clock).minusDays(7)));
    }
}
