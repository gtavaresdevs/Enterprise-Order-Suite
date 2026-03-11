package com.enterprise.ordersuite.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUpdateUserRequest(

        @Size(max = 255)
        String firstName,

        @Size(max = 255)
        String lastName,

        @NotBlank
        @Email
        @Size(max = 255)
        String email
) {}
