package com.enterprise.ordersuite.identity.application;

import java.util.Optional;

// Implemented by the restaurants module, which owns the Restaurant entity (dependency inversion).
public interface RestaurantLookup {

    Optional<RestaurantSummary> findSummary(String restaurantId);
}
