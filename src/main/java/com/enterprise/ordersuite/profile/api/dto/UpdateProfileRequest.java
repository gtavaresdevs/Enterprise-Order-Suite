package com.enterprise.ordersuite.profile.api.dto;

public record UpdateProfileRequest(
  String phone,
  String country,
  String timezone,
  String department,
  String office,
  String bio
) {
}
