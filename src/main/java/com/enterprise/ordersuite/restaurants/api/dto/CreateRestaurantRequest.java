package com.enterprise.ordersuite.restaurants.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Draft spec, schema CreateRestaurantRequest. An absent or blank timezone is America/Sao_Paulo (D-9).
public record CreateRestaurantRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull
        @Size(min = 3, max = 40)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be lowercase letters and digits in hyphen-separated groups")
        String slug,
        String timezone,
        @NotNull @Valid Owner owner
) {

    public record Owner(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName
    ) {
    }
}
