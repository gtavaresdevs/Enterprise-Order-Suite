package com.enterprise.ordersuite.restaurants.application;

import com.enterprise.ordersuite.common.errors.InvalidInputException;
import com.enterprise.ordersuite.common.util.PagedResult;
import com.enterprise.ordersuite.identity.api.dto.InviteMemberRequest;
import com.enterprise.ordersuite.identity.api.dto.MemberResponse;
import com.enterprise.ordersuite.identity.application.CurrentUserService;
import com.enterprise.ordersuite.identity.application.IdentityAuditService;
import com.enterprise.ordersuite.identity.application.TeamService;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import com.enterprise.ordersuite.identity.domain.MembershipRole;
import com.enterprise.ordersuite.restaurants.api.dto.CreateRestaurantRequest;
import com.enterprise.ordersuite.restaurants.api.dto.PlatformUpdateRestaurantRequest;
import com.enterprise.ordersuite.restaurants.api.dto.RestaurantResponse;
import com.enterprise.ordersuite.restaurants.domain.Restaurant;
import com.enterprise.ordersuite.restaurants.domain.RestaurantNotFoundException;
import com.enterprise.ordersuite.restaurants.domain.SlugReservedException;
import com.enterprise.ordersuite.restaurants.domain.SlugTakenException;
import com.enterprise.ordersuite.restaurants.persistence.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The platform admin's restaurant management (/platform/restaurants*, Tenancy & Identity
 * D-4, D-6). The restaurant always comes from the path, never from a tenant context: the
 * platform admin has no membership. Only PLATFORM_ADMIN reaches this class
 * (PlatformRestaurantController).
 */
@Service
@RequiredArgsConstructor
public class PlatformRestaurantService {

    static final String DEFAULT_TIMEZONE = "America/Sao_Paulo";

    // Tenancy & Identity, Restaurant: path words of the app and the storefront.
    private static final Set<String> RESERVED_SLUGS =
            Set.of("admin", "api", "app", "auth", "login", "platform", "public", "r", "static", "www");

    private final RestaurantRepository restaurantRepository;
    private final RestaurantService restaurantService;
    private final TeamService teamService;
    private final IdentityAuditService identityAuditService;
    private final CurrentUserService currentUserService;

    // Default order: name, then id (x-default-order).
    @Transactional(readOnly = true)
    public PagedResult<RestaurantResponse> list(int page, int size, String sort) {
        Page<Restaurant> restaurants = restaurantRepository.findAll(PageRequest.of(page, size, order(sort)));
        return PagedResult.of(restaurants, restaurants.map(restaurantService::toResponse).getContent());
    }

    @Transactional(readOnly = true)
    public RestaurantResponse get(String restaurantId) {
        return restaurantService.toResponse(require(restaurantId));
    }

    // Restaurant creation 1-3: the restaurant, its owner's invite and both audit events in one
    // transaction; the invite email goes out after commit. A taken owner email rolls the
    // restaurant back too.
    @Transactional
    public RestaurantResponse create(CreateRestaurantRequest request) {
        String slug = request.slug();
        if (RESERVED_SLUGS.contains(slug)) {
            throw new SlugReservedException();
        }
        String timezone = request.timezone() == null || request.timezone().isBlank()
                ? DEFAULT_TIMEZONE
                : zone(request.timezone());
        if (restaurantRepository.existsBySlug(slug)) {
            throw new SlugTakenException();
        }

        Restaurant restaurant;
        try {
            restaurant = restaurantRepository.saveAndFlush(new Restaurant(request.name().trim(), slug, timezone));
        } catch (DataIntegrityViolationException concurrentCreate) {
            // The same slug committed between the check and the insert.
            throw new SlugTakenException();
        }

        // Platform-level (restaurantId null, contract Audit event); the new id is in details.
        identityAuditService.recordEvent(
                IdentityAuditEventType.RESTAURANT_CREATED,
                null,
                currentUserService.getUserId(),
                null,
                Map.of("restaurantId", restaurant.getId(), "name", restaurant.getName(), "slug", slug,
                        "timezone", timezone));

        CreateRestaurantRequest.Owner owner = request.owner();
        teamService.inviteTo(restaurant.getId(),
                new InviteMemberRequest(owner.email(), owner.firstName(), owner.lastName(), MembershipRole.OWNER));
        return restaurantService.toResponse(restaurant);
    }

    // Name and timezone (D-14). A real change writes RESTAURANT_UPDATED into the restaurant's
    // own log, with { field: { from, to } } for each changed field.
    @Transactional
    public RestaurantResponse update(String restaurantId, PlatformUpdateRestaurantRequest request) {
        Restaurant restaurant = require(restaurantId);
        Map<String, Object> changes = new LinkedHashMap<>();
        if (request.name() != null) {
            String to = request.name().trim();
            if (!to.equals(restaurant.getName())) {
                changes.put("name", Map.of("from", restaurant.getName(), "to", to));
                restaurant.setName(to);
            }
        }
        if (request.timezone() != null) {
            String to = zone(request.timezone());
            if (!to.equals(restaurant.getTimezone())) {
                changes.put("timezone", Map.of("from", restaurant.getTimezone(), "to", to));
                restaurant.setTimezone(to);
            }
        }
        if (!changes.isEmpty()) {
            identityAuditService.recordEvent(IdentityAuditEventType.RESTAURANT_UPDATED, restaurant.getId(),
                    currentUserService.getUserId(), null, changes);
        }
        return restaurantService.toResponse(restaurant);
    }

    // The team's default order (TeamService).
    @Transactional(readOnly = true)
    public PagedResult<MemberResponse> listMembers(String restaurantId, int page, int size) {
        require(restaurantId);
        return teamService.listIn(restaurantId, null, null, page, size, null);
    }

    // Any role, OWNER included (§5.3).
    @Transactional
    public MemberResponse inviteMember(String restaurantId, InviteMemberRequest request) {
        require(restaurantId);
        return teamService.inviteTo(restaurantId, request);
    }

    private Restaurant require(String restaurantId) {
        return restaurantRepository.findById(restaurantId).orElseThrow(RestaurantNotFoundException::new);
    }

    // An IANA name from the JVM's zone list (contract, Restaurant).
    private static String zone(String value) {
        String zone = value.trim();
        if (!ZoneId.getAvailableZoneIds().contains(zone)) {
            throw new InvalidInputException("timezone: unknown time zone " + zone);
        }
        return zone;
    }

    // sort=<field>,<asc|desc> for the draft's x-sort fields; anything else is INVALID_INPUT.
    private static Sort order(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by("name", "id");
        }
        String[] parts = sort.split(",", -1);
        if (parts.length > 2) {
            throw new InvalidInputException("sort: expected <field>,<asc|desc>");
        }
        String field = parts[0].trim();
        if (!field.equals("name") && !field.equals("createdAt")) {
            throw new InvalidInputException("sort: unknown field " + field);
        }
        Sort.Direction direction = switch (parts.length == 1 ? "asc" : parts[1].trim().toLowerCase(Locale.ROOT)) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new InvalidInputException("sort: direction must be asc or desc");
        };
        return Sort.by(direction, field).and(Sort.by("id"));
    }
}
