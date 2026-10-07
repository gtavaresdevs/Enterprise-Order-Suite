package com.enterprise.ordersuite.security.userdetails;

import java.security.Principal;

public record JwtUserPrincipal(String id, String email) implements Principal {
    @Override
    public String getName() {
        return email;
    }
}
