package com.enterprise.ordersuite.profile.domain.exception;

public class ProfileNotFoundException extends RuntimeException {

  public ProfileNotFoundException(Long userId) {
    super("Profile not found for user with ID: " + userId);
  }
}
