package com.enterprise.ordersuite.restaurants.application;

import com.enterprise.ordersuite.identity.application.RestaurantLookup;
import com.enterprise.ordersuite.identity.application.RestaurantSummary;
import com.enterprise.ordersuite.restaurants.domain.Restaurant;
import com.enterprise.ordersuite.restaurants.persistence.RestaurantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class RestaurantLookupAdapter implements RestaurantLookup {

    private final RestaurantRepository restaurantRepository;
    private final String publicBaseUrl;

    public RestaurantLookupAdapter(
            RestaurantRepository restaurantRepository,
            @Value("${app.urls.public-base}") String publicBaseUrl
    ) {
        this.restaurantRepository = restaurantRepository;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RestaurantSummary> findSummary(String restaurantId) {
        return restaurantRepository.findById(restaurantId).map(this::toSummary);
    }

    // storefrontUrl: APP_PUBLIC_BASE_URL + /r/{slug} (Tenancy & Identity, Restaurant).
    private RestaurantSummary toSummary(Restaurant restaurant) {
        return new RestaurantSummary(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getSlug(),
                publicBaseUrl + "/r/" + restaurant.getSlug(),
                restaurant.getTimezone(),
                restaurant.getCurrency().name(),
                restaurant.getCreatedAt()
        );
    }
}
