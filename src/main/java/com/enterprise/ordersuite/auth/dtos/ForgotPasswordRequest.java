package com.enterprise.ordersuite.auth.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ForgotPasswordRequest {

    @NotBlank
    @Email
    private String email;

    public ForgotPasswordRequest() {}

}
