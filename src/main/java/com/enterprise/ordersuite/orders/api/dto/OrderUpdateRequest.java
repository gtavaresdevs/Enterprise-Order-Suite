package com.enterprise.ordersuite.orders.api.dto;

import com.enterprise.ordersuite.orders.domain.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderUpdateRequest {

    @NotNull(message = "Order status is required")
    private OrderStatus status;

    @Valid
    private List<OrderItemRequest> items;
}
