package com.enterprise.ordersuite.restaurants.api.dto;

// GET /public/r/{slug}: public fields only, no ids (§5.5). The Storefront contract extends it.
public record PublicRestaurantResponse(String name, String slug, String timezone, String currency) {
}
