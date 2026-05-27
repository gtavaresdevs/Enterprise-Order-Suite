package com.enterprise.ordersuite.orders.application.service;

public interface ProductService {
    boolean productExists(Long productId);
    void decrementStock(Long productId, int quantity);
    void incrementStock(Long productId, int quantity);
}
