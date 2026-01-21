package com.enterprise.ordersuite.services.notification;


public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetUrl);
}
