package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.common.tenancy.TenantContextHolder;
import com.enterprise.ordersuite.common.util.PagedResult;
import com.enterprise.ordersuite.identity.api.dto.AuditEventResponse;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEvent;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.IdentityAuditEventRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityAuditService {

    private final IdentityAuditEventRepository repository;
    private final UserRepository userRepository;

    public IdentityAuditService(IdentityAuditEventRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional
    public IdentityAuditEvent recordEvent(
            IdentityAuditEventType type,
            String restaurantId,
            String actorUserId,
            String targetUserId,
            Map<String, Object> details
    ) {
        IdentityAuditEvent event = new IdentityAuditEvent(
                type,
                restaurantId,
                actorUserId,
                targetUserId,
                details
        );

        return repository.save(event);
    }

    // GET /audit-events: the caller's restaurant only. Platform-level events (restaurant_id
    // null) never appear here.
    @Transactional(readOnly = true)
    public PagedResult<AuditEventResponse> list(IdentityAuditEventType type, int page, int size) {
        Page<IdentityAuditEvent> events = repository.findForRestaurant(
                TenantContextHolder.requireRestaurantId(), type, PageRequest.of(page, size));

        Set<String> userIds = new HashSet<>();
        events.forEach(event -> {
            userIds.add(event.getActorUserId());
            userIds.add(event.getTargetUserId());
        });
        userIds.remove(null);
        Map<String, String> names = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user.getFirstName() + " " + user.getLastName()));

        return PagedResult.of(events, events.map(event -> toResponse(event, names::get)).getContent());
    }

    private static AuditEventResponse toResponse(IdentityAuditEvent event, Function<String, String> nameOf) {
        return new AuditEventResponse(
                event.getId(),
                event.getRestaurantId(),
                event.getType().name(),
                event.getActorUserId(),
                event.getActorUserId() == null ? null : nameOf.apply(event.getActorUserId()),
                event.getTargetUserId(),
                event.getTargetUserId() == null ? null : nameOf.apply(event.getTargetUserId()),
                event.getDetails(),
                event.getCreatedAt()
        );
    }
}
