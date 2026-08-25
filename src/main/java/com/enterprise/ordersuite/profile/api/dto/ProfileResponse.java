package com.enterprise.ordersuite.profile.api.dto;

import java.time.LocalDateTime;

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
  LocalDateTime createdAt,
  LocalDateTime updatedAt
) {
}
