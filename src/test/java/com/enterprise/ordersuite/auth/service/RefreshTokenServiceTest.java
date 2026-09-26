package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.domain.RefreshToken;
import com.enterprise.ordersuite.auth.persistence.RefreshTokenRepository;
import com.enterprise.ordersuite.auth.service.tokens.RefreshTokenGenerator;
import com.enterprise.ordersuite.identity.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-28T12:00:00Z");

    private RefreshTokenRepository repo;
    private RefreshTokenGenerator generator;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        repo = mock(RefreshTokenRepository.class);
        generator = mock(RefreshTokenGenerator.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new RefreshTokenService(repo, generator, clock);
        when(repo.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void issueFor_createsTokenAndReturnsRawToken() {
        User user = new User();
        when(generator.generate()).thenReturn("raw-refresh-token");

        var issued = service.issueFor(user);

        assertThat(issued.rawToken()).isEqualTo("raw-refresh-token");
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(RefreshTokenService.REFRESH_TTL));
        verify(repo).save(argThat(t ->
                t.getUser() == user
                        && t.getTokenHash() != null
                        && t.getTokenHash().length() == 64
                        && t.getFamilyId() != null));
    }

    @Test
    void issueFor_startsANewFamilyOnEveryCall() {
        when(generator.generate()).thenReturn("a", "b");

        service.issueFor(new User());
        service.issueFor(new User());

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo, times(2)).save(saved.capture());
        List<RefreshToken> tokens = saved.getAllValues();
        assertThat(tokens.get(0).getFamilyId())
                .as("two logins are two sessions; sharing a family would let reuse in one kill the other")
                .isNotEqualTo(tokens.get(1).getFamilyId());
    }

    @Test
    void rotate_marksCurrentUsed_andIssuesSuccessorInTheSameFamily() {
        User user = new User();
        UUID family = UUID.randomUUID();
        RefreshToken current = new RefreshToken();
        current.setUser(user);
        current.setFamilyId(family);
        when(generator.generate()).thenReturn("successor");

        var issued = service.rotate(current);

        assertThat(issued.rawToken()).isEqualTo("successor");
        assertThat(current.getUsedAt()).isEqualTo(NOW);
        verify(repo).save(current);
        verify(repo).save(argThat(t -> t != current && t.getUser() == user && family.equals(t.getFamilyId())));
    }

    @Test
    void findForRotationOrNull_blankInput_returnsNullWithoutQuerying() {
        assertThat(service.findForRotationOrNull(null)).isNull();
        assertThat(service.findForRotationOrNull("  ")).isNull();
        verifyNoInteractions(repo);
    }

    @Test
    void findForRotationOrNull_unknownToken_returnsNull() {
        when(repo.findByTokenHashForUpdate(anyString())).thenReturn(java.util.Optional.empty());

        assertThat(service.findForRotationOrNull("anything")).isNull();
    }

    @Test
    void isExpired_comparesAgainstTheClock() {
        RefreshToken live = new RefreshToken();
        live.setExpiresAt(NOW.plus(Duration.ofSeconds(1)));
        RefreshToken dead = new RefreshToken();
        dead.setExpiresAt(NOW.minus(Duration.ofSeconds(1)));

        assertThat(service.isExpired(live)).isFalse();
        assertThat(service.isExpired(dead)).isTrue();
    }

    @Test
    void revokeFamily_revokesEveryTokenOfThatFamilyAtNow() {
        RefreshToken token = new RefreshToken();
        UUID family = UUID.randomUUID();
        token.setFamilyId(family);

        service.revokeFamily(token);

        verify(repo).revokeFamily(family, NOW);
    }

    @Test
    void revokeAllFor_revokesEveryTokenOfThatUserAtNow() {
        User user = new User();
        user.setId(42L);

        service.revokeAllFor(user);

        verify(repo).revokeAllForUser(42L, NOW);
    }
}
