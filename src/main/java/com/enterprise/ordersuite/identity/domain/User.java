package com.enterprise.ordersuite.identity.domain;

import com.enterprise.ordersuite.common.persistence.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(nullable = false)
    @NotNull
    private String firstName;

    @Column(nullable = false)
    @NotNull
    private String lastName;

    @Column(nullable = false, unique = true)
    @NotNull
    @Email
    private String email;

    // Null until an invited user sets a password.
    private String password;

    private String phone;

    // Set by seed or migration only, never by an endpoint (Tenancy & Identity D-3).
    @Column(nullable = false)
    private boolean platformAdmin = false;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "avatar_key")
    private String avatarKey;

}
