package com.enterprise.ordersuite.restaurants.application;

import com.enterprise.ordersuite.common.tenancy.TenantContextHolder;
import com.enterprise.ordersuite.identity.application.CurrentUserService;
import com.enterprise.ordersuite.identity.application.IdentityAuditService;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import com.enterprise.ordersuite.restaurants.api.dto.RestaurantResponse;
import com.enterprise.ordersuite.restaurants.api.dto.UpdateRestaurantRequest;
import com.enterprise.ordersuite.restaurants.domain.Restaurant;
import com.enterprise.ordersuite.restaurants.domain.RestaurantNotFoundException;
import com.enterprise.ordersuite.restaurants.persistence.RestaurantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

// The caller's restaurant (GET/PATCH /restaurant), taken from the tenant context (D-6). A
// support read of an unknown restaurant id is 404 RESTAURANT_NOT_FOUND.
@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final CurrentUserService currentUserService;
    private final IdentityAuditService identityAuditService;
    private final String publicBaseUrl;

    public RestaurantService(
            RestaurantRepository restaurantRepository,
            CurrentUserService currentUserService,
            IdentityAuditService identityAuditService,
            @Value("${app.urls.public-base}") String publicBaseUrl
    ) {
        this.restaurantRepository = restaurantRepository;
        this.currentUserService = currentUserService;
        this.identityAuditService = identityAuditService;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    @Transactional(readOnly = true)
    public RestaurantResponse getMine() {
        return toResponse(requireMine());
    }

    // Build 1 edits the name only. A real change writes RESTAURANT_UPDATED with { name: { from, to } }.
    @Transactional
    public RestaurantResponse updateMine(UpdateRestaurantRequest request) {
        Restaurant restaurant = requireMine();
        if (request.name() != null) {
            String from = restaurant.getName();
            String to = request.name().trim();
            if (!to.equals(from)) {
                restaurant.setName(to);
                identityAuditService.recordEvent(
                        IdentityAuditEventType.RESTAURANT_UPDATED,
                        restaurant.getId(),
                        currentUserService.getUserId(),
                        null,
                        Map.of("name", Map.of("from", from, "to", to)));
            }
        }
        return toResponse(restaurant);
    }

    // storefrontUrl: APP_PUBLIC_BASE_URL + /r/{slug} (Tenancy & Identity, Restaurant).
    public String storefrontUrl(String slug) {
        return publicBaseUrl + "/r/" + slug;
    }

    private Restaurant requireMine() {
        return restaurantRepository.findById(TenantContextHolder.requireRestaurantId())
                .orElseThrow(RestaurantNotFoundException::new);
    }

    private RestaurantResponse toResponse(Restaurant restaurant) {
        return new RestaurantResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getSlug(),
                storefrontUrl(restaurant.getSlug()),
                restaurant.getTimezone(),
                restaurant.getCurrency().name(),
                restaurant.getCreatedAt()
        );
    }
}
