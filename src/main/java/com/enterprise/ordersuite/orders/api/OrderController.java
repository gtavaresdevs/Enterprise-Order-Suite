package com.enterprise.ordersuite.orders.api;

import com.enterprise.ordersuite.common.util.PagedResult;
import com.enterprise.ordersuite.identity.application.CurrentUserService;
import com.enterprise.ordersuite.orders.api.dto.OrderCreateRequest;
import com.enterprise.ordersuite.orders.api.dto.OrderResponse;
import com.enterprise.ordersuite.orders.api.dto.OrderUpdateRequest;
import com.enterprise.ordersuite.orders.application.service.OrderService;
import com.enterprise.ordersuite.orders.domain.OrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Orders", description = "Order Management APIs")
public class OrderController {

    private final OrderService orderService;
    private final CurrentUserService currentUserService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCOPE_order:write')")
    @Operation(summary = "Create a new order", description = "Creates a new order in the system.")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        String requestId = MDC.get("requestId");
        log.info("requestId: {} - Received request to create order with orderNumber: {}", requestId, request.getOrderNumber());
        OrderResponse response = orderService.createOrder(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCOPE_order:read')")
    @PostAuthorize("hasRole('ADMIN') or returnObject.body.customerId == authentication.principal.id")
    @Operation(summary = "Get order by ID", description = "Retrieves a specific order by its ID. Users can only access their own orders unless they are an admin.")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        String requestId = MDC.get("requestId");
        log.debug("requestId: {} - Received request to get order by ID: {}", requestId, id);
        return orderService.getOrderById(id)
                .map(orderResponse -> new ResponseEntity<>(orderResponse, HttpStatus.OK))
                .orElseGet(() -> {
                    log.warn("requestId: {} - Order with ID: {} not found.", requestId, id);
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                });
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCOPE_order:read')")
    @Operation(summary = "Get all orders", description = "Retrieves a paginated list of all orders. Non-admin users will only see their own orders.")
    public ResponseEntity<PagedResult<OrderResponse>> getAllOrders(Pageable pageable) {
        String requestId = MDC.get("requestId");
        log.debug("requestId: {} - Received request to get all orders with pageable: {}", requestId, pageable);
        return new ResponseEntity<>(PagedResult.of(orderService.getAllOrders(pageable)), HttpStatus.OK);
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCOPE_order:read')")
    @Operation(summary = "Search orders", description = "Retrieves a paginated list of orders based on search criteria. Non-admin users will only see their own orders.")
    public ResponseEntity<PagedResult<OrderResponse>> searchOrders(
            @RequestParam(required = false) String orderNumber,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long customerId,
            @Parameter(description = "Pagination parameters") Pageable pageable) {
        String requestId = MDC.get("requestId");
        log.info("requestId: {} - Received request to search orders with criteria: orderNumber={}, status={}, customerId={}, pageable={}",
                requestId, orderNumber, status, customerId, pageable);
        PagedResult<OrderResponse> result = orderService.searchOrders(orderNumber, status, customerId, pageable);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCOPE_order:write')")
    @Operation(summary = "Update an existing order", description = "Updates an existing order identified by its ID. Users can only update their own orders unless they are an admin.")
    public ResponseEntity<OrderResponse> updateOrder(@PathVariable Long id, @Valid @RequestBody OrderUpdateRequest request) {
        String requestId = MDC.get("requestId");
        log.info("requestId: {} - Received request to update order with ID: {}", requestId, id);

        return orderService.updateOrder(id, request)
                .map(orderResponse -> {
                    if (!isAdmin() && !orderResponse.getCustomerId().equals(currentUserService.getUserId())) {
                         return new ResponseEntity<OrderResponse>(HttpStatus.FORBIDDEN);
                    }
                    return new ResponseEntity<>(orderResponse, HttpStatus.OK);
                })
                .orElseGet(() -> {
                    log.warn("requestId: {} - Order with ID: {} not found for update.", requestId, id);
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                });
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCOPE_order:delete')")
    @Operation(summary = "Delete an order", description = "Deletes an order identified by its ID. Users can only delete their own orders unless they are an admin.")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        String requestId = MDC.get("requestId");
        log.info("requestId: {} - Received request to delete order with ID: {}", requestId, id);

        return orderService.getOrderById(id)
                .map(order -> {
                    if (!isAdmin() && !order.getCustomerId().equals(currentUserService.getUserId())) {
                        return new ResponseEntity<Void>(HttpStatus.FORBIDDEN);
                    }
                    orderService.deleteOrder(id);
                    return new ResponseEntity<Void>(HttpStatus.NO_CONTENT);
                })
                .orElseGet(() -> {
                    log.warn("requestId: {} - Order with ID: {} not found for deletion.", requestId, id);
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                });
    }

    private boolean isAdmin() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
