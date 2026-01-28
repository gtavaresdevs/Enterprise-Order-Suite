package com.enterprise.ordersuite.auth.service.exceptions;

public class InvalidPasswordResetTokenException extends RuntimeException {
    public InvalidPasswordResetTokenException(String message) {
        super(message);
    }

    public static InvalidPasswordResetTokenException generic() {
        return new InvalidPasswordResetTokenException("Invalid or expired password reset token");
    }
}
