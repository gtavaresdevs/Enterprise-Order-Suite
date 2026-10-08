package com.enterprise.ordersuite.restaurants.api;

import com.enterprise.ordersuite.restaurants.api.dto.PublicRestaurantResponse;
import com.enterprise.ordersuite.restaurants.application.PublicRestaurantService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// GET /public/r/{slug} (§5.5): anonymous, permitted for /public/** in SecurityConfig. An
// unknown slug is 404 RESTAURANT_NOT_FOUND.
@RestController
public class PublicRestaurantController {

    private final PublicRestaurantService publicRestaurantService;

    public PublicRestaurantController(PublicRestaurantService publicRestaurantService) {
        this.publicRestaurantService = publicRestaurantService;
    }

    @GetMapping("/public/r/{slug}")
    public ResponseEntity<PublicRestaurantResponse> get(@PathVariable String slug) {
        return ResponseEntity.ok(publicRestaurantService.get(slug));
    }
}
