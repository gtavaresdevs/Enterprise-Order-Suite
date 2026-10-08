package com.enterprise.ordersuite.restaurants.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// PATCH /restaurant: name only in Build 1; an absent name stays as it is.
public record UpdateRestaurantRequest(
        @Size(min = 1, max = 80) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name
) {
}
