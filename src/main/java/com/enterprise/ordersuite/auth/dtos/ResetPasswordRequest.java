package com.enterprise.ordersuite.auth.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ResetPasswordRequest {

    @NotBlank
    private String token;

    @NotBlank
    private String newPassword;

    public ResetPasswordRequest() {}

}
