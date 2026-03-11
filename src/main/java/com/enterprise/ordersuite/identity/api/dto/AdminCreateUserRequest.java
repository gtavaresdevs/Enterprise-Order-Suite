package com.enterprise.ordersuite.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminCreateUserRequest(

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(max = 120)
        String firstName,

        @NotBlank
        @Size(max = 120)
        String lastName,

        // Optional: default USER if null/blank
        @Size(max = 60)
        String role,

        // Optional: default true if null
        Boolean sendPasswordSetupEmail

) { }