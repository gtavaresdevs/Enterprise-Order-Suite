package com.enterprise.ordersuite.restaurants.persistence;

import com.enterprise.ordersuite.restaurants.domain.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<Restaurant, String> {

    boolean existsBySlug(String slug);

    // GET /public/r/{slug} only. Its own query and its own shape, never a staff one (§5.5, LR-4):
    // whatever the Restaurant entity gains later stays out of the public response.
    @Query("select new com.enterprise.ordersuite.restaurants.persistence.PublicRestaurantView("
            + "r.name, r.slug, r.timezone, r.currency) from Restaurant r where r.slug = :slug")
    Optional<PublicRestaurantView> findPublicBySlug(@Param("slug") String slug);
}
