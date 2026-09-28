package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.domain.RefreshToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

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
}
