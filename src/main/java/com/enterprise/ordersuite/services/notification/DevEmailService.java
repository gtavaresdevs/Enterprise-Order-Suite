package com.enterprise.ordersuite.services.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;


@Service
@Profile({"default", "dev"})
public class DevEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(DevEmailService.class);

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetUrl) {
        // This is dev only. Pwd and rst link should not be logged into prod
        log.info("[DEV EMAIL] Password reset requested for: {}", toEmail);
        log.info("[DEV EMAIL] Reset link: {}", resetUrl);
    }
}
