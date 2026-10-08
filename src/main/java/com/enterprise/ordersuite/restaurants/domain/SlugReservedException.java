package com.enterprise.ordersuite.restaurants.domain;

// 400 SLUG_RESERVED: the slug is on the reserved list (Tenancy & Identity, Restaurant).
public class SlugReservedException extends RuntimeException {

    public SlugReservedException() {
        super("This slug is reserved");
    }
}
