package com.enterprise.ordersuite.restaurants.api;

import com.enterprise.ordersuite.restaurants.api.dto.RestaurantResponse;
import com.enterprise.ordersuite.restaurants.api.dto.UpdateRestaurantRequest;
import com.enterprise.ordersuite.restaurants.application.RestaurantService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// §5.3: every member reads their restaurant (STAFF covers MANAGER and OWNER through the
// hierarchy), the platform admin by support read; only the owner edits it here. The platform
// admin edits through /platform/restaurants/{id}.
@RestController
public class RestaurantController {

    private final RestaurantService restaurantService;

    public RestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @GetMapping("/restaurant")
    @PreAuthorize("hasRole('STAFF') or @tenantAccess.supportRead()")
    public ResponseEntity<RestaurantResponse> get() {
        return ResponseEntity.ok(restaurantService.getMine());
    }

    @PatchMapping(value = "/restaurant", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<RestaurantResponse> update(@Valid @RequestBody UpdateRestaurantRequest request) {
        return ResponseEntity.ok(restaurantService.updateMine(request));
    }
}
