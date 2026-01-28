package com.enterprise.ordersuite.notifications.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("!dev & !local")
public class NoopEmailService implements EmailService {

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetUrl) {
        // Intentionally do nothing. Safe default for prod until a real provider exists.
        log.debug("[NOOP EMAIL] Password reset email suppressed for '{}'.", toEmail);
    }
}
