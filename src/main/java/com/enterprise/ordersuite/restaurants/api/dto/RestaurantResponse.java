package com.enterprise.ordersuite.restaurants.api.dto;

import java.time.Instant;

// Draft spec, schema Restaurant.
public record RestaurantResponse(
        String id,
        String name,
        String slug,
        String storefrontUrl,
        String timezone,
        String currency,
        Instant createdAt
) {
}
