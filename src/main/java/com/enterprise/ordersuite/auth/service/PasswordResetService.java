package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.domain.PasswordResetToken;
import com.enterprise.ordersuite.auth.domain.PasswordResetTokenPurpose;
import com.enterprise.ordersuite.auth.persistence.PasswordResetTokenRepository;
import com.enterprise.ordersuite.auth.service.exceptions.InvalidPasswordResetTokenException;
import com.enterprise.ordersuite.common.errors.InvalidInputException;
import com.enterprise.ordersuite.identity.application.MemberInvitations;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.notifications.service.EmailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Core enterprise domain logic service handling security lifecycles for account recovery
 * and initial administrative identity provisioning password setups. Now includes dynamic
 * historical credential reuse policy validation checks.
 */
@Service
public class PasswordResetService implements MemberInvitations {

  private static final int TOKEN_BYTES = 32; // Cryptographically strong 256-bit entropy
  private static final int EXPIRY_MINUTES = 15;
  static final Duration INVITE_EXPIRY = Duration.ofDays(7);

  private final UserRepository userRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final PasswordUpdater passwordUpdater;
  private final Clock clock;
  private final EmailService emailService;
  private final PasswordResetLinkBuilder linkBuilder;
  private final RefreshTokenService refreshTokenService;

  public PasswordResetService(
    UserRepository userRepository,
    PasswordResetTokenRepository passwordResetTokenRepository,
    PasswordUpdater passwordUpdater,
    Clock clock,
    EmailService emailService,
    PasswordResetLinkBuilder linkBuilder,
    RefreshTokenService refreshTokenService
  ) {
    this.userRepository = userRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.passwordUpdater = passwordUpdater;
    this.clock = clock;
    this.emailService = emailService;
    this.linkBuilder = linkBuilder;
    this.refreshTokenService = refreshTokenService;
  }

  /**
   * Issues a high-entropy, short-lived password recovery token context for an active user.
   * Always returns an Optional container to prevent controller-layer account enumeration leaks.
   *
   * @param email Target address requesting password modification.
   * @return Optional container wrapping the raw verification token string.
   */
  @Transactional
  public Optional<String> requestPasswordReset(String email) {
    Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
    if (userOpt.isEmpty()) {
      return Optional.empty();
    }

    User user = userOpt.get();

    // Security check: Block public token lifecycle validation for disabled or suspended entities
    if (!Boolean.TRUE.equals(user.getActive())) {
      return Optional.empty();
    }

    return Optional.of(processTokenCreationAndDispatch(user));
  }

  /**
   * Issues an invite token (purpose INVITE, 7 days) for a user who has no password yet, and
   * invalidates any invite token issued before, so only the newest link works (Tenancy &
   * Identity, Invites 3). Joins the caller's transaction (restaurant creation, team invite);
   * the email goes out only after that transaction commits, so a rolled-back invite never
   * reaches anyone. The invitee completes it through POST /auth/reset-password.
   */
  @Override
  @Transactional
  public void sendInvite(User user) {
    Instant now = Instant.now(clock);
    passwordResetTokenRepository.invalidateUnused(user.getId(), PasswordResetTokenPurpose.INVITE, now);

    String rawToken = saveToken(user, PasswordResetTokenPurpose.INVITE, now.plus(INVITE_EXPIRY));
    String setupUrl = linkBuilder.build(rawToken);
    String email = user.getEmail();
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        emailService.sendInvitationEmail(email, setupUrl);
      }
    });
  }

  /**
   * Evaluates cryptographically stored recovery secrets, validates expiration bounds,
   * enforces password historic reuse limits, and performs the password mutation sequence.
   *
   * @param rawToken    The unhashed base64 web url token context received from the user interface link.
   * @param newPassword Raw unhashed plain text sequence to establish on the user account.
   */
  @Transactional
  public void resetPassword(String rawToken, String newPassword) {
    if (rawToken == null || rawToken.isBlank()) {
      throw InvalidPasswordResetTokenException.generic();
    }
    if (newPassword == null || newPassword.isBlank()) {
      throw new InvalidInputException("newPassword: must not be blank");
    }

    // Compute the deterministic hash matching the criteria utilized during persistence allocation
    String tokenHash = sha256Hex(rawToken);

    PasswordResetToken prt = passwordResetTokenRepository.findByTokenHash(tokenHash)
      .orElseThrow(InvalidPasswordResetTokenException::generic);

    Instant now = Instant.now(clock);

    // Enforce singular execution logic requirements (Replay protection)
    if (prt.getUsedAt() != null) {
      throw InvalidPasswordResetTokenException.generic();
    }

    // Enforce temporal safety validation constraints
    if (prt.getExpiresAt().isBefore(now)) {
      throw InvalidPasswordResetTokenException.generic();
    }

    User user = prt.getUser(); // Safe proxy loading execution within transactional context boundary

    // Enterprise Guard: Enforce platform locks if the account recovery target is inactive
    if (!Boolean.TRUE.equals(user.getActive())) {
      throw InvalidPasswordResetTokenException.generic();
    }

    // Same reuse rules and history as a change from /me/password.
    passwordUpdater.replace(user, newPassword);

    // D21: a reset says the credentials may be compromised - end every existing session.
    refreshTokenService.revokeAllFor(user);

    // Consume token to guarantee it can never be used again
    prt.setUsedAt(now);
    passwordResetTokenRepository.save(prt);
  }

  private String processTokenCreationAndDispatch(User user) {
    String rawToken = saveToken(user, PasswordResetTokenPurpose.RESET,
      Instant.now(clock).plus(Duration.ofMinutes(EXPIRY_MINUTES)));

    // Dispatched into the background thread pool manager asynchronously
    emailService.sendPasswordResetEmail(user.getEmail(), linkBuilder.build(rawToken));

    return rawToken;
  }

  private String saveToken(User user, PasswordResetTokenPurpose purpose, Instant expiresAt) {
    String rawToken = generateRawToken();
    PasswordResetToken entity = new PasswordResetToken(user, sha256Hex(rawToken), expiresAt);
    entity.setPurpose(purpose);
    passwordResetTokenRepository.save(entity);
    return rawToken;
  }

  /**
   * Generates a 256-bit cryptographically secure high-entropy random sequence token.
   */
  private String generateRawToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Compiles raw characters into a secure SHA-256 hex signature layout mapping.
   */
  private String sha256Hex(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (Exception e) {
      throw new IllegalStateException("Critical cryptographic component initialization anomaly encountered", e);
    }
  }
}
