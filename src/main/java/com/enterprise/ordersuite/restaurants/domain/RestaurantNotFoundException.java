package com.enterprise.ordersuite.restaurants.domain;

// 404 RESTAURANT_NOT_FOUND: unknown restaurant id (support read, platform) or slug (public).
public class RestaurantNotFoundException extends RuntimeException {

    public RestaurantNotFoundException() {
        super("Restaurant not found");
    }
}
