package com.enterprise.ordersuite.identity.api.dto;

import com.enterprise.ordersuite.identity.application.RestaurantSummary;

import java.time.Instant;

// Tenancy & Identity draft spec, schema Me. membership and restaurant are null for the
// platform admin.
public record MeResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        String phone,
        String avatarUrl,
        boolean platformAdmin,
        MembershipView membership,
        RestaurantSummary restaurant
) {

    public record MembershipView(String restaurantId, String role, Instant memberSince) {}
}
