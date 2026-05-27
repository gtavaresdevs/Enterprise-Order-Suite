package com.enterprise.ordersuite.orders.domain.exception;

import com.enterprise.ordersuite.orders.domain.OrderStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(OrderStatus from, OrderStatus to) {
        super(String.format("Invalid status transition from %s to %s", from, to));
    }
}
