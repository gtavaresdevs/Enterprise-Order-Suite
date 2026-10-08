package com.enterprise.ordersuite.identity.api.dto;

import java.time.Instant;

// Draft spec, schema Member. id is the user id ({memberId}); invitationPending is true until
// the invited person sets a password; memberSince is when the membership was created.
public record MemberResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        String phone,
        String avatarUrl,
        String role,
        boolean active,
        boolean invitationPending,
        Instant memberSince
) {
}
