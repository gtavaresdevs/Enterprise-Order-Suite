package com.enterprise.ordersuite.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

// PATCH semantics: an absent field stays as it is. phone may be sent as null to remove it,
// so its presence is tracked apart from its value; names cannot be removed.
@Getter
@NoArgsConstructor
public class UpdateMeRequest {

    private static final String NOT_BLANK = ".*\\S.*";

    @Size(min = 1, max = 80)
    @Pattern(regexp = NOT_BLANK, message = "must not be blank")
    private String firstName;

    @Size(min = 1, max = 80)
    @Pattern(regexp = NOT_BLANK, message = "must not be blank")
    private String lastName;

    // E.164 (Tenancy & Identity, User and membership).
    @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$", message = "must be an E.164 number such as +5511999998888")
    private String phone;

    @JsonIgnore
    private boolean phoneSent;

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @JsonSetter("phone")
    public void setPhone(String phone) {
        this.phone = phone;
        this.phoneSent = true;
    }
}
