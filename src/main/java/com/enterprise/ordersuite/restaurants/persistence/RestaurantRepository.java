package com.enterprise.ordersuite.restaurants.persistence;

import com.enterprise.ordersuite.restaurants.domain.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<Restaurant, String> {

    Optional<Restaurant> findBySlug(String slug);
}
