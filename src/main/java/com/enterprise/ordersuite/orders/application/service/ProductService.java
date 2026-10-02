package com.enterprise.ordersuite.orders.application.service;

import java.math.BigDecimal;

public interface ProductService {
    boolean productExists(Long productId);

    // The catalogue price an order line is priced from. Orders never trust the price a
    // client sends; they snapshot this value onto the line at the moment the item is added,
    // so a later catalogue edit cannot rewrite a historical order either.
    BigDecimal getPrice(Long productId);

    void decrementStock(Long productId, int quantity);
    void incrementStock(Long productId, int quantity);
}
