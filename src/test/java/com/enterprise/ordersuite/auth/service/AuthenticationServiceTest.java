package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.domain.RefreshToken;
import com.enterprise.ordersuite.auth.service.exceptions.InvalidRefreshTokenException;
import com.enterprise.ordersuite.identity.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

  @Mock
  private RefreshTokenService refreshTokenService;

  @InjectMocks
  private AuthenticationService authenticationService;

  @Test
  void logout_loadsTheTokenWithTheRowLock_thenRevokesItsFamily() {
    RefreshToken token = new RefreshToken();
    token.setFamilyId(UUID.randomUUID());
    when(refreshTokenService.findForRotationOrNull("raw")).thenReturn(token);

    authenticationService.logout("raw");

    // The locked lookup waits for a concurrent rotation of this token to commit, so the
    // family revocation that follows also revokes the successor that rotation inserted.
    verify(refreshTokenService).findForRotationOrNull("raw");
    verify(refreshTokenService).revokeFamily(token);
  }

  @Test
  void logout_unknownToken_revokesNothing() {
    when(refreshTokenService.findForRotationOrNull("unknown")).thenReturn(null);

    authenticationService.logout("unknown");

    verify(refreshTokenService, never()).revokeFamily(any());
  }

  // D24: used/revoked is checked before expiry, so a token that is both used and expired is
  // still treated as reuse - the whole family is revoked, not silently left alone because it
  // also happens to be expired.
  @Test
  void refresh_usedAndExpired_revokesFamilyAndThrowsInvalidRefreshToken() {
    RefreshToken token = new RefreshToken();
    token.setFamilyId(UUID.randomUUID());
    token.setUsedAt(Instant.now());
    when(refreshTokenService.findForRotationOrNull("raw")).thenReturn(token);
    // Not called under the current used/revoked-before-expiry order; stubbed leniently so the
    // test still pins the correct outcome if that order is ever reversed.
    lenient().when(refreshTokenService.isExpired(token)).thenReturn(true);

    assertThatThrownBy(() -> authenticationService.refresh("raw"))
      .isInstanceOf(InvalidRefreshTokenException.class);

    verify(refreshTokenService).revokeFamily(token);
  }

  // D16: an expired token that was never used or revoked is not reuse - it just lapsed. No
  // family revocation should happen, only the 401.
  @Test
  void refresh_expiredOnly_doesNotRevokeFamilyButStillThrows() {
    RefreshToken token = new RefreshToken();
    token.setFamilyId(UUID.randomUUID());
    when(refreshTokenService.findForRotationOrNull("raw")).thenReturn(token);
    when(refreshTokenService.isExpired(token)).thenReturn(true);

    assertThatThrownBy(() -> authenticationService.refresh("raw"))
      .isInstanceOf(InvalidRefreshTokenException.class);

    verify(refreshTokenService, never()).revokeFamily(any());
  }

  @Test
  void refresh_inactiveUser_revokesFamilyAndThrowsInvalidRefreshToken() {
    User user = new User();
    user.setActive(false);

    RefreshToken token = new RefreshToken();
    token.setFamilyId(UUID.randomUUID());
    token.setUser(user);
    when(refreshTokenService.findForRotationOrNull("raw")).thenReturn(token);
    when(refreshTokenService.isExpired(token)).thenReturn(false);

    assertThatThrownBy(() -> authenticationService.refresh("raw"))
      .isInstanceOf(InvalidRefreshTokenException.class);

    verify(refreshTokenService).revokeFamily(token);
  }
}
