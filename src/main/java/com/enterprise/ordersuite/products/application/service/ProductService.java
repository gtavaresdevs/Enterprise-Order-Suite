package com.enterprise.ordersuite.products.application.service;

import com.enterprise.ordersuite.common.util.PagedResult;
import com.enterprise.ordersuite.orders.domain.exception.ProductNotFoundException;
import com.enterprise.ordersuite.products.api.dto.ProductRequest;
import com.enterprise.ordersuite.products.api.dto.ProductResponse;
import com.enterprise.ordersuite.products.application.mapper.ProductMapper;
import com.enterprise.ordersuite.products.domain.Product;
import com.enterprise.ordersuite.products.domain.exception.InsufficientStockException;
import com.enterprise.ordersuite.products.persistence.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService implements com.enterprise.ordersuite.orders.application.service.ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        String requestId = MDC.get("requestId");
        log.info("requestId: {} - Creating product with SKU: {}", requestId, request.getSku());
        
        Product product = productMapper.toEntity(request);
        Product savedProduct = productRepository.save(product);
        
        log.info("requestId: {} - Product created with ID: {}", requestId, savedProduct.getId());
        return productMapper.toResponse(savedProduct);
    }

    @Transactional(readOnly = true)
    public Optional<ProductResponse> getProductById(Long id) {
        String requestId = MDC.get("requestId");
        log.debug("requestId: {} - Fetching product by ID: {}", requestId, id);
        return productRepository.findById(id)
                .map(productMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PagedResult<ProductResponse> getAllProducts(Pageable pageable) {
        String requestId = MDC.get("requestId");
        log.debug("requestId: {} - Fetching all products with pageable: {}", requestId, pageable);
        Page<Product> products = productRepository.findAll(pageable);
        return PagedResult.of(products.map(productMapper::toResponse));
    }

    @Transactional
    public Optional<ProductResponse> updateProduct(Long id, ProductRequest request) {
        String requestId = MDC.get("requestId");
        log.info("requestId: {} - Updating product with ID: {}", requestId, id);
        
        return productRepository.findById(id)
                .map(existingProduct -> {
                    productMapper.updateEntityFromDto(request, existingProduct);
                    Product updatedProduct = productRepository.save(existingProduct);
                    log.info("requestId: {} - Product with ID: {} updated", requestId, id);
                    return productMapper.toResponse(updatedProduct);
                });
    }

    @Transactional
    public void deleteProduct(Long id) {
        String requestId = MDC.get("requestId");
        log.info("requestId: {} - Deleting product with ID: {}", requestId, id);
        productRepository.deleteById(id);
        log.info("requestId: {} - Product with ID: {} deleted", requestId, id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean productExists(Long productId) {
        return productRepository.existsById(productId);
    }

    @Override
    @Transactional
    public void decrementStock(Long productId, int quantity) {
        String requestId = MDC.get("requestId");
        log.info("requestId: {} - Decrementing stock for product ID: {} by quantity: {}", requestId, productId, quantity);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (product.getStockQuantity() < quantity) {
            throw new InsufficientStockException(productId, quantity, product.getStockQuantity());
        }
        product.setStockQuantity(product.getStockQuantity() - quantity);
        productRepository.save(product);
        log.debug("requestId: {} - Stock for product ID: {} decremented to: {}", requestId, productId, product.getStockQuantity());
    }

    @Override
    @Transactional
    public void incrementStock(Long productId, int quantity) {
        String requestId = MDC.get("requestId");
        log.info("requestId: {} - Incrementing stock for product ID: {} by quantity: {}", requestId, productId, quantity);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        product.setStockQuantity(product.getStockQuantity() + quantity);
        productRepository.save(product);
        log.debug("requestId: {} - Stock for product ID: {} incremented to: {}", requestId, productId, product.getStockQuantity());
    }
}
