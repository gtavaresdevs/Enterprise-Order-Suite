package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.domain.PasswordHistory;
import com.enterprise.ordersuite.auth.persistence.PasswordHistoryRepository;
import com.enterprise.ordersuite.auth.service.exceptions.PasswordReuseException;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

// The one place a password is replaced, so reset and change apply the same rules: no reuse of
// the current password or the last HISTORY_LIMIT ones (PASSWORD_REUSE_ERROR), and the
// replaced hash goes into the history.
@Component
@RequiredArgsConstructor
public class PasswordUpdater {

    static final int HISTORY_LIMIT = 5;

    private final UserRepository userRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public void replace(User user, String newPassword) {
        String current = user.getPassword();
        boolean hasCurrent = current != null && !current.isBlank();

        if (hasCurrent && passwordEncoder.matches(newPassword, current)) {
            throw new PasswordReuseException();
        }
        for (PasswordHistory old : passwordHistoryRepository.findRecentByUserId(
                user.getId(), PageRequest.of(0, HISTORY_LIMIT))) {
            if (passwordEncoder.matches(newPassword, old.getPasswordHash())) {
                throw new PasswordReuseException();
            }
        }

        if (hasCurrent) {
            passwordHistoryRepository.save(new PasswordHistory(user, current, Instant.now(clock)));
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        passwordHistoryRepository.pruneOldEntries(user.getId(), (long) HISTORY_LIMIT);
    }
}
