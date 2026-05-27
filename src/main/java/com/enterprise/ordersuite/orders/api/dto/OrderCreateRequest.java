package com.enterprise.ordersuite.orders.api.dto;

import com.enterprise.ordersuite.orders.domain.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateRequest {

    @NotBlank(message = "Order number is required")
    @Size(min = 3, max = 50, message = "Order number must be between 3 and 50 characters")
    private String orderNumber;

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    @NotNull(message = "Order status is required")
    private OrderStatus status;

    @NotEmpty(message = "Order must have at least one item")
    @Valid
    private List<OrderItemRequest> items;
}
