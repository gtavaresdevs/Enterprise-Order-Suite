package com.enterprise.ordersuite.orders.application.service;

import com.enterprise.ordersuite.common.util.PagedResult;
import com.enterprise.ordersuite.identity.application.CurrentUserService;
import com.enterprise.ordersuite.orders.api.dto.OrderCreateRequest;
import com.enterprise.ordersuite.orders.api.dto.OrderItemRequest;
import com.enterprise.ordersuite.orders.api.dto.OrderResponse;
import com.enterprise.ordersuite.orders.api.dto.OrderUpdateRequest;
import com.enterprise.ordersuite.orders.application.mapper.OrderItemMapper;
import com.enterprise.ordersuite.orders.application.mapper.OrderMapper;
import com.enterprise.ordersuite.orders.domain.Order;
import com.enterprise.ordersuite.orders.domain.OrderHistory;
import com.enterprise.ordersuite.orders.domain.OrderItem;
import com.enterprise.ordersuite.orders.domain.OrderStatus;
import com.enterprise.ordersuite.orders.domain.exception.OrderNotEditableException;
import com.enterprise.ordersuite.orders.domain.exception.ProductNotFoundException;
import com.enterprise.ordersuite.orders.persistence.OrderHistoryRepository;
import com.enterprise.ordersuite.orders.persistence.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CurrentUserService currentUserService;
    // The orders-side interface, not the products class that implements it: orders must
    // compile without knowing the products module exists.
    private final ProductService productService;
    private final NotificationService notificationService;
    private final RoleHierarchy roleHierarchy;

    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request) {
        String requestId = MDC.get("requestId");
        Long currentUserId = currentUserService.getUserId();
        log.info("requestId: {} - User: {} - Creating new order with orderNumber: {}", requestId, currentUserId, request.getOrderNumber());
        
        validateProductsExist(request.getItems());
        
        Order orderEntity = orderMapper.toEntity(request);
        orderEntity.setStatus(OrderStatus.PENDING);
        orderEntity.setCustomerId(currentUserId); // Set the current user's ID as the customer ID
        if (request.getItems() != null) {
            log.debug("requestId: {} - Processing {} items for orderNumber: {}", requestId, request.getItems().size(), request.getOrderNumber());
            addItems(orderEntity, request.getItems());
        }
        
        orderEntity.setTotalAmount(calculateTotalAmount(orderEntity));
        Order savedOrder = orderRepository.save(orderEntity);

        saveOrderHistory(savedOrder.getId(), null, OrderStatus.PENDING);
        notificationService.sendOrderUpdateNotification(savedOrder);
        
        log.info("requestId: {} - User: {} - Order created successfully with ID: {} and totalAmount: {}", 
                requestId, currentUserId, savedOrder.getId(), savedOrder.getTotalAmount());
        return orderMapper.toResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN') or @orderService.isOrderOwner(#id, principal.id)")
    public Optional<OrderResponse> getOrderById(Long id) {
        String requestId = MDC.get("requestId");
        log.debug("requestId: {} - Fetching order by ID: {}", requestId, id);
        return orderRepository.findById(id)
                .map(orderMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        String requestId = MDC.get("requestId");
        log.debug("requestId: {} - Fetching all orders with pageable: {}", requestId, pageable);
        
        if (isAdmin()) {
            return orderRepository.findAll(pageable).map(orderMapper::toResponse);
        } else {
            // Asserted for the same reason as in searchOrders below. Spring Data rewrites a
            // null parameter on a derived query into IS NULL, so this only returns nothing
            // today because orders.customer_id is NOT NULL - the safety is in the schema,
            // and the restaurant-ops migration is what makes that column nullable.
            Long currentUserId = Objects.requireNonNull(
                    currentUserService.getUserId(),
                    "A non-admin list must be scoped to a customer id");
            return orderRepository.findByCustomerId(currentUserId, pageable).map(orderMapper::toResponse);
        }
    }

    @Transactional(readOnly = true)
    public PagedResult<OrderResponse> searchOrders(String orderNumber, OrderStatus status, Long customerId, Pageable pageable) {
        String requestId = MDC.get("requestId");
        
        Long effectiveCustomerId = customerId;
        if (!isAdmin()) {
            // searchOrders reads a null customerId as "no filter", so a null here would widen
            // a non-admin's search to every order in the database. The tenant boundary must
            // not depend on CurrentUserService never returning null - assert it.
            effectiveCustomerId = Objects.requireNonNull(
                    currentUserService.getUserId(),
                    "A non-admin search must be scoped to a customer id");
            log.info("requestId: {} - Non-admin user detected. Overriding search customerId with current user ID: {}", requestId, effectiveCustomerId);
        }

        log.info("requestId: {} - Searching orders with criteria: orderNumber={}, status={}, customerId={}, pageable={}", 
                requestId, orderNumber, status, effectiveCustomerId, pageable);
        Page<Order> orderPage = orderRepository.searchOrders(orderNumber, status, effectiveCustomerId, pageable);
        Page<OrderResponse> responsePage = orderPage.map(orderMapper::toResponse);
        return PagedResult.of(responsePage);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or @orderService.isOrderOwner(#id, principal.id)")
    public Optional<OrderResponse> updateOrder(Long id, OrderUpdateRequest request) {
        String requestId = MDC.get("requestId");
        Long currentUserId = currentUserService.getUserId();
        log.info("requestId: {} - User: {} - Updating order with ID: {}", requestId, currentUserId, id);
        
        return orderRepository.findById(id)
                .map(existingOrder -> {
                    // Judged on the order as it stands before this request, and before any
                    // product is looked up: a closed order answers 409 whatever the items say.
                    // A request with no items key is a status-only update and is untouched.
                    if (request.getItems() != null && !isOpen(existingOrder)) {
                        throw new OrderNotEditableException(id, existingOrder.getStatus());
                    }

                    validateProductsExist(request.getItems());

                    // Items are replaced before the status block, not after. A cancellation
                    // credits back the stock of the items the order holds, so on a request
                    // that both replaces items and cancels, the replacement has to have
                    // settled first or the credit applies to the discarded items.
                    if (request.getItems() != null) {
                        log.debug("requestId: {} - Replacing items for order ID: {}. New item count: {}", requestId, id, request.getItems().size());
                        removeAllItems(existingOrder);
                        addItems(existingOrder, request.getItems());
                    }

                    OrderStatus oldStatus = existingOrder.getStatus();
                    OrderStatus newStatus = request.getStatus();

                    if (newStatus != null && oldStatus != newStatus) {
                        existingOrder.transitionTo(newStatus);
                        handleStatusTransition(existingOrder, oldStatus, newStatus);
                        saveOrderHistory(id, oldStatus, newStatus);
                        notificationService.sendOrderUpdateNotification(existingOrder);
                    }

                    // Runs after transitionTo, never before: it copies the requested status
                    // straight onto the entity, so ahead of the transition it would make
                    // transitionTo see an unchanged status and skip the state machine.
                    orderMapper.updateEntityFromDto(request, existingOrder);

                    existingOrder.setTotalAmount(calculateTotalAmount(existingOrder));
                    Order updatedOrder = orderRepository.save(existingOrder);
                    log.info("requestId: {} - User: {} - Order with ID: {} updated successfully. New totalAmount: {}", 
                            requestId, currentUserId, id, updatedOrder.getTotalAmount());
                    return orderMapper.toResponse(updatedOrder);
                });
    }

    // Adds each requested item to the order, taking its stock and snapshotting the catalogue
    // price onto the line. Shared by createOrder and updateOrder's replacement so the two
    // paths cannot drift: an item joining an order always costs stock and is always priced
    // by the server.
    //
    // OrderItemRequest.unitPrice is accepted - the API contract still declares it required -
    // but deliberately never read. A client that sends a price is either out of date or
    // tampering, and the request cannot tell you which.
    private void addItems(Order order, List<OrderItemRequest> itemRequests) {
        boolean moveStock = isOpen(order);
        itemRequests.forEach(itemRequest -> {
            if (moveStock) {
                productService.decrementStock(itemRequest.getProductId(), itemRequest.getQuantity());
            }
            OrderItem orderItem = orderItemMapper.toEntity(itemRequest);
            orderItem.setUnitPrice(productService.getPrice(itemRequest.getProductId()));
            order.addItem(orderItem);
        });
    }

    // The mirror of addItems: an item leaving an order gives its stock back.
    private void removeAllItems(Order order) {
        if (isOpen(order)) {
            order.getItems().forEach(item ->
                    productService.incrementStock(item.getProductId(), item.getQuantity())
            );
        }
        order.getItems().clear();
    }

    // Whether the order is still open: PENDING or PROCESSING. This is the single notion of
    // an open order in this service, and it has two consequences.
    //
    // Stock: an open order's claim on stock is settleable, because it can still be cancelled
    // and cancelling is what credits stock back. A CANCELLED order already gave its stock
    // back; a SHIPPED or DELIVERED one consumed it for good. Moving stock for either would
    // mint it.
    //
    // Editability: only an open order accepts an item payload (D13). A closed order is a
    // record - replacing its lines used to rewrite totalAmount, to zero for an empty list,
    // with no history row. updateOrder refuses before reaching addItems/removeAllItems, so
    // on that path the stock check above is now a second line of defence.
    //
    // No status changes during a replacement, so this answer is stable across one. The
    // restaurant-ops rename keeps the boundary: New and Preparing stay open; Ready,
    // Completed and Cancelled do not.
    private boolean isOpen(Order order) {
        OrderStatus status = order.getStatus();
        return status == OrderStatus.PENDING || status == OrderStatus.PROCESSING;
    }

    private void handleStatusTransition(Order order, OrderStatus oldStatus, OrderStatus newStatus) {
        if (newStatus == OrderStatus.CANCELLED && oldStatus != OrderStatus.CANCELLED) {
            log.info("Order {} cancelled. Incrementing stock back.", order.getId());
            order.getItems().forEach(item -> 
                productService.incrementStock(item.getProductId(), item.getQuantity())
            );
        }
    }

    @Transactional
    // Deleting an order is ADMIN-only. This was previously enforced by the
    // controller withholding SCOPE_order:delete from regular users; stating it
    // here keeps the permission identical once that scope is removed.
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteOrder(Long id) {
        String requestId = MDC.get("requestId");
        Long currentUserId = currentUserService.getUserId();
        log.info("requestId: {} - User: {} - Deleting order with ID: {}", requestId, currentUserId, id);
        
        orderRepository.deleteById(id);
        
        log.info("requestId: {} - User: {} - Order with ID: {} deleted successfully.", requestId, currentUserId, id);
    }

    private void saveOrderHistory(Long orderId, OrderStatus from, OrderStatus to) {
        String currentUserEmail = currentUserService.getEmail();
        OrderHistory history = OrderHistory.builder()
                .orderId(orderId)
                .fromStatus(from != null ? from : OrderStatus.PENDING) // Initial status is PENDING if from is null
                .toStatus(to)
                .changedBy(currentUserEmail)
                .timestamp(Instant.now())
                .build();
        orderHistoryRepository.save(history);
    }

    private void validateProductsExist(List<OrderItemRequest> items) {
        if (items != null) {
            for (OrderItemRequest item : items) {
                if (!productService.productExists(item.getProductId())) {
                    throw new ProductNotFoundException(item.getProductId());
                }
            }
        }
    }

    // Derived from the line items' stored unit prices, which addItems resolved from the
    // catalogue - never from anything the client sent.
    private BigDecimal calculateTotalAmount(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return order.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Resolves through RoleHierarchy because getAuthorities() returns the raw, un-expanded
    // list: methodSecurityExpressionHandler applies the hierarchy to @PreAuthorize only, so
    // a SUPER_ADMIN carries ROLE_SUPER_ADMIN and nothing else. Comparing raw authorities
    // against ROLE_ADMIN silently demoted them to a regular customer here.
    // This scopes a query rather than guarding a method, which is why it is not @PreAuthorize.
    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            return false;
        }

        return roleHierarchy.getReachableGrantedAuthorities(authentication.getAuthorities())
                .stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    // Helper method for @PreAuthorize
    @Transactional(readOnly = true)
    public boolean isOrderOwner(Long orderId, Long userId) {
        return orderRepository.findById(orderId)
                .map(order -> order.getCustomerId().equals(userId))
                .orElse(false);
    }
}
