package com.enterprise.ordersuite.products.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST) // Added this annotation
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(Long productId, int requestedQuantity, int availableStock) {
        super(String.format("Insufficient stock for product ID: %d. Requested: %d, Available: %d",
                productId, requestedQuantity, availableStock));
    }
}
