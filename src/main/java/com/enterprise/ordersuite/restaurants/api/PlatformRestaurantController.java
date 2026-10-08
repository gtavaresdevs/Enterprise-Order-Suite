package com.enterprise.ordersuite.restaurants.api;

import com.enterprise.ordersuite.common.util.PagedResult;
import com.enterprise.ordersuite.identity.api.dto.InviteMemberRequest;
import com.enterprise.ordersuite.identity.api.dto.MemberResponse;
import com.enterprise.ordersuite.restaurants.api.dto.CreateRestaurantRequest;
import com.enterprise.ordersuite.restaurants.api.dto.PlatformUpdateRestaurantRequest;
import com.enterprise.ordersuite.restaurants.api.dto.RestaurantResponse;
import com.enterprise.ordersuite.restaurants.application.PlatformRestaurantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// /platform/restaurants* (§5.3, D-4): the platform admin only, every operation. On the class,
// so a new method cannot be added without it. The restaurant comes from the path (D-6).
@RestController
@RequestMapping("/platform/restaurants")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformRestaurantController {

    private static final String ULID = "^[0-9A-HJKMNP-TV-Z]{26}$";

    private final PlatformRestaurantService platformRestaurantService;

    public PlatformRestaurantController(PlatformRestaurantService platformRestaurantService) {
        this.platformRestaurantService = platformRestaurantService;
    }

    @GetMapping
    public ResponseEntity<PagedResult<RestaurantResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String sort
    ) {
        return ResponseEntity.ok(platformRestaurantService.list(page, size, sort));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RestaurantResponse> create(@Valid @RequestBody CreateRestaurantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(platformRestaurantService.create(request));
    }

    @GetMapping("/{restaurantId}")
    public ResponseEntity<RestaurantResponse> get(@PathVariable @Pattern(regexp = ULID) String restaurantId) {
        return ResponseEntity.ok(platformRestaurantService.get(restaurantId));
    }

    @PatchMapping(value = "/{restaurantId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RestaurantResponse> update(
            @PathVariable @Pattern(regexp = ULID) String restaurantId,
            @Valid @RequestBody PlatformUpdateRestaurantRequest request
    ) {
        return ResponseEntity.ok(platformRestaurantService.update(restaurantId, request));
    }

    @GetMapping("/{restaurantId}/members")
    public ResponseEntity<PagedResult<MemberResponse>> listMembers(
            @PathVariable @Pattern(regexp = ULID) String restaurantId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(platformRestaurantService.listMembers(restaurantId, page, size));
    }

    @PostMapping(value = "/{restaurantId}/members", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MemberResponse> inviteMember(
            @PathVariable @Pattern(regexp = ULID) String restaurantId,
            @Valid @RequestBody InviteMemberRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(platformRestaurantService.inviteMember(restaurantId, request));
    }
}
