package com.enterprise.ordersuite.auth.service.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PasswordReuseException extends RuntimeException {
  public PasswordReuseException() {
    super("This password has been used before. Please choose a different password.");
  }
}
