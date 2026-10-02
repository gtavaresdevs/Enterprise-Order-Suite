package com.enterprise.ordersuite.profile.api.dto;

import java.time.Instant;

public record ProfileResponse(
  Long id,
  String email,
  String firstName,
  String lastName,
  String role,
  String phone,
  String country,
  String timezone,
  String department,
  String office,
  String bio,
  String avatarUrl,
  Instant createdAt,
  Instant updatedAt
) {
}
