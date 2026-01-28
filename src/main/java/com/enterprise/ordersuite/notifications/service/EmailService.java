package com.enterprise.ordersuite.notifications.service;


public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetUrl);
}
