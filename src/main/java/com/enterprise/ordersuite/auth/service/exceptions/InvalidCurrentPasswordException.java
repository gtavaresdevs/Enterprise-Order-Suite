package com.enterprise.ordersuite.auth.service.exceptions;

public class InvalidCurrentPasswordException extends RuntimeException {
    public InvalidCurrentPasswordException() {
        super("The current password is incorrect");
    }
}
