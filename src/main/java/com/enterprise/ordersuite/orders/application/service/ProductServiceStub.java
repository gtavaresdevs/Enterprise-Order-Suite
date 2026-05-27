package com.enterprise.ordersuite.orders.application.service;

// This class is no longer a Spring component and does not implement the ProductService interface.
// It is kept for historical context or if a true stub is needed in a different context.
public class ProductServiceStub {

    public boolean productExists(Long productId) {
        return productId != null && productId > 0;
    }

    public void decrementStock(Long productId, int quantity) {
        // No-op
    }

    public void incrementStock(Long productId, int quantity) {
        // No-op
    }
}
