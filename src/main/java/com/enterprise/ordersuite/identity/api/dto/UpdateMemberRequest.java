package com.enterprise.ordersuite.identity.api.dto;

import com.enterprise.ordersuite.identity.domain.MembershipRole;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// PATCH /team/members/{memberId}: an absent field stays as it is, like UpdateMeRequest.
public record UpdateMemberRequest(
        @Size(min = 1, max = 80) @Pattern(regexp = NOT_BLANK, message = "must not be blank") String firstName,
        @Size(min = 1, max = 80) @Pattern(regexp = NOT_BLANK, message = "must not be blank") String lastName,
        MembershipRole role
) {
    private static final String NOT_BLANK = ".*\\S.*";
}
