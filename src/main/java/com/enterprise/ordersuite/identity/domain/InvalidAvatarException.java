package com.enterprise.ordersuite.identity.domain;

public class InvalidAvatarException extends RuntimeException {

  public InvalidAvatarException(String message) {
    super(message);
  }

  public InvalidAvatarException(
    String message,
    Throwable cause
  ) {
    super(message, cause);
  }
}
