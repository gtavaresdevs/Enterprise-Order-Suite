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
import com.enterprise.ordersuite.orders.domain.exception.ProductNotFoundException;
import com.enterprise.ordersuite.orders.persistence.OrderHistoryRepository;
import com.enterprise.ordersuite.orders.persistence.OrderRepository;
import com.enterprise.ordersuite.products.application.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
    private final ProductService productService;
    private final NotificationService notificationService;

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
            request.getItems().forEach(itemRequest -> {
                productService.decrementStock(itemRequest.getProductId(), itemRequest.getQuantity());
                OrderItem orderItem = orderItemMapper.toEntity(itemRequest);
                orderEntity.addItem(orderItem);
            });
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
            Long currentUserId = currentUserService.getUserId();
            return orderRepository.searchOrders(null, null, currentUserId, pageable).map(orderMapper::toResponse);
        }
    }

    @Transactional(readOnly = true)
    public PagedResult<OrderResponse> searchOrders(String orderNumber, OrderStatus status, Long customerId, Pageable pageable) {
        String requestId = MDC.get("requestId");
        
        Long effectiveCustomerId = customerId;
        if (!isAdmin()) {
            effectiveCustomerId = currentUserService.getUserId();
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
                    OrderStatus oldStatus = existingOrder.getStatus();
                    OrderStatus newStatus = request.getStatus();
                    
                    if (newStatus != null && oldStatus != newStatus) {
                        existingOrder.transitionTo(newStatus);
                        handleStatusTransition(existingOrder, oldStatus, newStatus);
                        saveOrderHistory(id, oldStatus, newStatus);
                        notificationService.sendOrderUpdateNotification(existingOrder);
                    }

                    validateProductsExist(request.getItems());
                    
                    orderMapper.updateEntityFromDto(request, existingOrder);
                    
                    if (request.getItems() != null) {
                        log.debug("requestId: {} - Replacing items for order ID: {}. New item count: {}", requestId, id, request.getItems().size());
                        existingOrder.getItems().clear();
                        request.getItems().forEach(itemRequest -> {
                            OrderItem orderItem = orderItemMapper.toEntity(itemRequest);
                            existingOrder.addItem(orderItem);
                        });
                    }

                    existingOrder.setTotalAmount(calculateTotalAmount(existingOrder));
                    Order updatedOrder = orderRepository.save(existingOrder);
                    log.info("requestId: {} - User: {} - Order with ID: {} updated successfully. New totalAmount: {}", 
                            requestId, currentUserId, id, updatedOrder.getTotalAmount());
                    return orderMapper.toResponse(updatedOrder);
                });
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
    @PreAuthorize("hasRole('ADMIN') or @orderService.isOrderOwner(#id, principal.id)")
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

    private BigDecimal calculateTotalAmount(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return order.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    // Helper method for @PreAuthorize
    @Transactional(readOnly = true)
    public boolean isOrderOwner(Long orderId, Long userId) {
        return orderRepository.findById(orderId)
                .map(order -> order.getCustomerId().equals(userId))
                .orElse(false);
    }
}
