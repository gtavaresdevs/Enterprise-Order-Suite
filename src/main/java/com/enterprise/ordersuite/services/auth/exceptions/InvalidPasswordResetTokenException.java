package com.enterprise.ordersuite.services.auth.exceptions;

public class InvalidPasswordResetTokenException extends RuntimeException {
    public InvalidPasswordResetTokenException(String message) {
        super(message);
    }

    public static InvalidPasswordResetTokenException generic() {
        return new InvalidPasswordResetTokenException("Invalid or expired password reset token");
    }
}
