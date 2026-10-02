package com.enterprise.ordersuite.orders.domain.exception;

import com.enterprise.ordersuite.orders.domain.OrderStatus;

public class OrderNotEditableException extends RuntimeException {
    public OrderNotEditableException(Long orderId, OrderStatus status) {
        super(String.format("Order %d is %s; its items can no longer be changed", orderId, status));
    }
}
