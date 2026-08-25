package com.enterprise.ordersuite.profile.domain.exception;

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
