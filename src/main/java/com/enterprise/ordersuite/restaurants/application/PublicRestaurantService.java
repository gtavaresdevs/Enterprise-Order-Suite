package com.enterprise.ordersuite.restaurants.application;

import com.enterprise.ordersuite.restaurants.api.dto.PublicRestaurantResponse;
import com.enterprise.ordersuite.restaurants.domain.RestaurantNotFoundException;
import com.enterprise.ordersuite.restaurants.persistence.RestaurantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// GET /public/r/{slug} (§5.5): anonymous, so it has its own query and its own shape, and
// shares nothing with the staff and platform reads (LR-4).
@Service
public class PublicRestaurantService {

    private final RestaurantRepository restaurantRepository;

    public PublicRestaurantService(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    @Transactional(readOnly = true)
    public PublicRestaurantResponse get(String slug) {
        return restaurantRepository.findPublicBySlug(slug)
                .map(view -> new PublicRestaurantResponse(view.name(), view.slug(), view.timezone(), view.currency().name()))
                .orElseThrow(RestaurantNotFoundException::new);
    }
}
