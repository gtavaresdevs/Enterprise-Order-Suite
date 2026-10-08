package com.enterprise.ordersuite.identity.api.dto;

import com.enterprise.ordersuite.identity.domain.MembershipRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Draft spec, schema InviteRequest.
public record InviteMemberRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotNull MembershipRole role
) {
}
