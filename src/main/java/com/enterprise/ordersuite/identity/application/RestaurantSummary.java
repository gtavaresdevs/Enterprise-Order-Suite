package com.enterprise.ordersuite.identity.application;

import java.time.Instant;

// Tenancy & Identity draft spec, schema Restaurant: what GET /me shows of the member's restaurant.
public record RestaurantSummary(
        String id,
        String name,
        String slug,
        String storefrontUrl,
        String timezone,
        String currency,
        Instant createdAt
) {}
