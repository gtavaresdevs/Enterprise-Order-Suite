package com.enterprise.ordersuite.notifications.service;


public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetUrl);

    // An invited member sets their first password through this link (7 days).
    void sendInvitationEmail(String toEmail, String setupUrl);
}
