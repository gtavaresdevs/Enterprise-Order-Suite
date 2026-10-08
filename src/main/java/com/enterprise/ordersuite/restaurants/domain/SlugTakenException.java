package com.enterprise.ordersuite.restaurants.domain;

// 409 SLUG_TAKEN: another restaurant already has the slug (Tenancy & Identity, Restaurant).
public class SlugTakenException extends RuntimeException {

    public SlugTakenException() {
        super("This slug is already in use");
    }
}
