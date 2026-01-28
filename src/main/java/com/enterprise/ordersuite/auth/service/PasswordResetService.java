package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.domain.PasswordResetToken;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.auth.persistence.PasswordResetTokenRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.auth.service.exceptions.InvalidPasswordResetTokenException;
import com.enterprise.ordersuite.notifications.service.EmailService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32; // 256-bit
    private static final int EXPIRY_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    // NEW (keeps original behavior, adds email + link composition)
    private final EmailService emailService;
    private final PasswordResetLinkBuilder linkBuilder;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            EmailService emailService,
            PasswordResetLinkBuilder linkBuilder
    ) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;

        this.emailService = emailService;
        this.linkBuilder = linkBuilder;
    }

    /**
     * Returns Optional raw token for existing users.
     * Caller must always respond success to avoid email enumeration.
     */
    @Transactional
    public Optional<String> requestPasswordReset(String email) {
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        String rawToken = generateRawToken();
        String tokenHash = sha256Hex(rawToken);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plusMinutes(EXPIRY_MINUTES);

        PasswordResetToken entity = new PasswordResetToken(userOpt.get(), tokenHash, expiresAt);
        passwordResetTokenRepository.save(entity);

        // NEW: compose URL and "send" email (dev logs, prod sends)
        String resetUrl = linkBuilder.build(rawToken);
        emailService.sendPasswordResetEmail(userOpt.get().getEmail(), resetUrl);

        return Optional.of(rawToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw InvalidPasswordResetTokenException.generic();
        }
        if (newPassword == null || newPassword.isBlank()) {
            // Keep this simple for now. Later we add password policy validation.
            throw new IllegalArgumentException("New password must not be blank");
        }

        String tokenHash = sha256Hex(rawToken);

        PasswordResetToken prt = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidPasswordResetTokenException::generic);

        LocalDateTime now = LocalDateTime.now(clock);

        if (prt.isUsed()) {
            throw InvalidPasswordResetTokenException.generic();
        }
        if (prt.getExpiresAt().isBefore(now)) {
            throw InvalidPasswordResetTokenException.generic();
        }

        User user = prt.getUser(); // LAZY, but inside transaction it is fine
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        prt.setUsedAt(now);
        passwordResetTokenRepository.save(prt);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash reset token", e);
        }
    }
}
