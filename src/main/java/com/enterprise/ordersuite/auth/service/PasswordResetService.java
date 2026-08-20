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
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Core enterprise domain logic service handling security lifecycles for account recovery
 * and initial administrative identity provisioning password setups. Now includes dynamic
 * historical credential reuse policy validation checks.
 */
@Service
public class PasswordResetService {

  private static final int TOKEN_BYTES = 32; // Cryptographically strong 256-bit entropy
  private static final int EXPIRY_MINUTES = 15;
  private static final int HISTORY_LIMIT = 5; // Track up to the last 5 passwords

  private final UserRepository userRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final PasswordHistoryRepository passwordHistoryRepository;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;
  private final EmailService emailService;
  private final PasswordResetLinkBuilder linkBuilder;

  public PasswordResetService(
    UserRepository userRepository,
    PasswordResetTokenRepository passwordResetTokenRepository,
    PasswordHistoryRepository passwordHistoryRepository,
    PasswordEncoder passwordEncoder,
    Clock clock,
    EmailService emailService,
    PasswordResetLinkBuilder linkBuilder
  ) {
    this.userRepository = userRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.passwordHistoryRepository = passwordHistoryRepository;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
    this.emailService = emailService;
    this.linkBuilder = linkBuilder;
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
   * Triggers an isolated database transaction to initialize administrative provisioning flows
   * for brand new platform users. Bypasses active status checks to allow initialization
   * of pending/inactive provisioned administrator profiles.
   *
   * @param email Target destination account address.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void sendPasswordSetupForNewUser(String email) {
    User user = userRepository.findByEmailIgnoreCase(email)
      .orElseThrow(() -> new IllegalArgumentException("Cannot provision password setup: User profile not found for email provided."));

    // Process token generation directly, bypassing the public recovery active checking constraints
    processTokenCreationAndDispatch(user);
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
      throw new IllegalArgumentException("New password string constraints failed validation: must not be blank");
    }

    // Compute the deterministic hash matching the criteria utilized during persistence allocation
    String tokenHash = sha256Hex(rawToken);

    PasswordResetToken prt = passwordResetTokenRepository.findByTokenHash(tokenHash)
      .orElseThrow(InvalidPasswordResetTokenException::generic);

    LocalDateTime now = LocalDateTime.now(clock);

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

    // ==========================================
    // 1. EVALUATE PASSWORD REUSE RESTRICTIONS
    // ==========================================

    // Step A: Compare against current active password hash (if one exists)
    if (user.getPassword() != null && !user.getPassword().isBlank() && passwordEncoder.matches(newPassword, user.getPassword())) {
      throw new PasswordReuseException();
    }

    // Step B: Compare against recent historical snapshots (limited strictly to the policy context)
    List<PasswordHistory> historyList = passwordHistoryRepository.findRecentByUserId(
      user.getId(), PageRequest.of(0, HISTORY_LIMIT)
    );

    for (PasswordHistory oldEntry : historyList) {
      if (passwordEncoder.matches(newPassword, oldEntry.getPasswordHash())) {
        throw new PasswordReuseException();
      }
    }

    // ==========================================
    // 2. PERSIST NEW PASSWORD & ARCHIVE OLD HASH
    // ==========================================

    // Capture the existing active password into history before overwriting it
    if (user.getPassword() != null && !user.getPassword().isBlank()) {
      PasswordHistory newHistoryEntry = new PasswordHistory(user, user.getPassword(), now);
      passwordHistoryRepository.save(newHistoryEntry);
    }

    // Safely map and hash raw password string using configured encoders
    user.setPassword(passwordEncoder.encode(newPassword));

    // Ensure that upon setting the password, the user is explicitly flagged active if they were a provisioned user
    if (!Boolean.TRUE.equals(user.getActive())) {
      user.setActive(true);
    }

    userRepository.save(user);

    // Consume token to guarantee it can never be used again
    prt.setUsedAt(now);
    passwordResetTokenRepository.save(prt);

    // ==========================================
    // 3. CAP COMPLIANCE CLEANUP (Housekeeping)
    // ==========================================

    // High-performance query truncation delegates pruning to database layer instantly
    passwordHistoryRepository.pruneOldEntries(user.getId(), (long) HISTORY_LIMIT);
  }

  /**
   * Shared private business logic handling extraction of raw crypto entropy, token record allocations,
   * and background mailing dispatches.
   */
  private String processTokenCreationAndDispatch(User user) {
    // Generate strong secure random string token sequence
    String rawToken = generateRawToken();
    String tokenHash = sha256Hex(rawToken);

    LocalDateTime now = LocalDateTime.now(clock);
    LocalDateTime expiresAt = now.plusMinutes(EXPIRY_MINUTES);

    // Create the token mapping entity context
    PasswordResetToken entity = new PasswordResetToken(user, tokenHash, expiresAt);
    passwordResetTokenRepository.save(entity);

    // Build the frontend link string mapped out via properties configuration
    String resetUrl = linkBuilder.build(rawToken);

    // Dispatched into the background thread pool manager asynchronously
    emailService.sendPasswordResetEmail(user.getEmail(), resetUrl);

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
