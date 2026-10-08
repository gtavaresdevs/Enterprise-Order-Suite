package com.enterprise.ordersuite.restaurants.application;

import com.enterprise.ordersuite.identity.application.RestaurantLookup;
import com.enterprise.ordersuite.identity.application.RestaurantSummary;
import com.enterprise.ordersuite.restaurants.domain.Restaurant;
import com.enterprise.ordersuite.restaurants.persistence.RestaurantRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class RestaurantLookupAdapter implements RestaurantLookup {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantService restaurantService;

    public RestaurantLookupAdapter(RestaurantRepository restaurantRepository, RestaurantService restaurantService) {
        this.restaurantRepository = restaurantRepository;
        this.restaurantService = restaurantService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RestaurantSummary> findSummary(String restaurantId) {
        return restaurantRepository.findById(restaurantId).map(this::toSummary);
    }

    private RestaurantSummary toSummary(Restaurant restaurant) {
        return new RestaurantSummary(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getSlug(),
                restaurantService.storefrontUrl(restaurant.getSlug()),
                restaurant.getTimezone(),
                restaurant.getCurrency().name(),
                restaurant.getCreatedAt()
        );
    }
}
