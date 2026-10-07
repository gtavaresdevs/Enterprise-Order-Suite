package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionUserFactory {

    private final RestaurantLookup restaurantLookup;
    private final ObjectStorageService objectStorageService;

    // restaurantId is the one the access token carries (null for the platform admin).
    public SessionUser of(User user, String restaurantId) {
        String restaurantName = restaurantId == null
                ? null
                : restaurantLookup.findSummary(restaurantId).map(RestaurantSummary::name).orElse(null);

        return new SessionUser(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getAvatarKey() == null ? null : objectStorageService.getUrl(user.getAvatarKey()),
                restaurantId,
                restaurantName
        );
    }
}
