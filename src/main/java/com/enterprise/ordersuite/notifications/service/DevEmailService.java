package com.enterprise.ordersuite.notifications.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile({"dev", "local"})
public class DevEmailService implements EmailService {

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetUrl) {
        log.info("[DEV EMAIL] Password reset requested for '{}'. Reset URL: {}", toEmail, resetUrl);
    }
}
