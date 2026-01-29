package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.domain.RefreshToken;
import com.enterprise.ordersuite.auth.service.tokens.RefreshTokenGenerator;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.auth.persistence.RefreshTokenRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    private RefreshTokenRepository repo;
    private RefreshTokenGenerator generator;
    private Clock clock;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        repo = mock(RefreshTokenRepository.class);
        generator = mock(RefreshTokenGenerator.class);

        clock = Clock.fixed(Instant.parse("2026-01-28T12:00:00Z"), ZoneOffset.UTC);

        service = new RefreshTokenService(repo, generator, clock);
    }

    @Test
    void issueFor_shouldCreateTokenAndReturnRawToken() {
        User user = new User();
        when(generator.generate()).thenReturn("raw-refresh-token");
        when(repo.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime now = LocalDateTime.now(clock);

        var issued = service.issueFor(user);

        assertThat(issued.rawToken()).isEqualTo("raw-refresh-token");

        verify(repo).save(argThat(t ->
                t.getUser() == user
                        && t.getTokenHash() != null
                        && t.getTokenHash().length() == 64
                        && t.getExpiresAt().isAfter(now)
        ));
    }

    @Test
    void getActiveTokenOrNull_shouldReturnNullWhenTokenNotFound() {
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.empty());

        RefreshToken found = service.getActiveTokenOrNull("anything");

        assertThat(found).isNull();
    }
}
