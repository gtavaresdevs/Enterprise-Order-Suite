package com.enterprise.ordersuite.security.userdetails;

import java.security.Principal;

// From the access token claims: sub, rid (null for the platform admin), role.
public record JwtUserPrincipal(String id, String restaurantId, String role) implements Principal {
    @Override
    public String getName() {
        return id;
    }
}
