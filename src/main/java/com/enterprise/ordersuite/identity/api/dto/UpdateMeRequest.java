package com.enterprise.ordersuite.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateMeRequest(

        @NotNull
        @NotBlank
        @Size(max = 100)
        String firstName,

        @NotNull
        @NotBlank
        @Size(max = 100)
        String lastName

) {}
