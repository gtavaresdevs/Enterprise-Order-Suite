package com.enterprise.ordersuite.restaurants.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// PATCH /platform/restaurants/{id}: name and timezone (D-14); an absent field stays as it is.
// The slug never changes (D-7), so it is an unknown field here.
public record PlatformUpdateRestaurantRequest(
        @Size(min = 1, max = 80) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") String timezone
) {
}
