package com.enterprise.ordersuite.common.errors;

// 400 INVALID_INPUT for a request value no annotation can check (a sort expression, say).
// The message goes to the client as is, so it is written for the client and never carries
// internal detail. Anything else unexpected, an IllegalArgumentException included, is a 500.
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }
}
