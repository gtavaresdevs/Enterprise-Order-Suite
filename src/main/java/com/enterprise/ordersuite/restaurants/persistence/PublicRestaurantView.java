package com.enterprise.ordersuite.restaurants.persistence;

import com.enterprise.ordersuite.restaurants.domain.Currency;

// The columns an anonymous caller may see (RestaurantRepository.findPublicBySlug).
public record PublicRestaurantView(String name, String slug, String timezone, Currency currency) {
}
