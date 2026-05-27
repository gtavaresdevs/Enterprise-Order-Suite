package com.enterprise.ordersuite.products.application.service;

import com.enterprise.ordersuite.products.api.dto.ProductRequest;
import com.enterprise.ordersuite.products.api.dto.ProductResponse;
import com.enterprise.ordersuite.products.application.mapper.ProductMapper;
import com.enterprise.ordersuite.products.domain.Product;
import com.enterprise.ordersuite.products.persistence.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    void setUp() {
        MDC.put("requestId", "test-request-id");
    }

    @Test
    void createProduct_shouldSaveAndReturnResponse() {
        // Given
        ProductRequest request = ProductRequest.builder()
                .name("Laptop").sku("LAP-001").price(new BigDecimal("999.99")).stockQuantity(10)
                .build();
        Product product = new Product();
        Product savedProduct = new Product();
        savedProduct.setId(1L);
        ProductResponse response = ProductResponse.builder().id(1L).sku("LAP-001").build();

        when(productMapper.toEntity(request)).thenReturn(product);
        when(productRepository.save(product)).thenReturn(savedProduct);
        when(productMapper.toResponse(savedProduct)).thenReturn(response);

        // When
        ProductResponse result = productService.createProduct(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(productRepository).save(product);
    }

    @Test
    void getProductById_shouldReturnOptionalResponse() {
        // Given
        Long id = 1L;
        Product product = new Product();
        ProductResponse response = ProductResponse.builder().id(id).build();

        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(productMapper.toResponse(product)).thenReturn(response);

        // When
        Optional<ProductResponse> result = productService.getProductById(id);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
    }

    @Test
    void updateProduct_shouldUpdateAndSave() {
        // Given
        Long id = 1L;
        ProductRequest request = ProductRequest.builder().name("Updated Laptop").build();
        Product existingProduct = new Product();
        existingProduct.setId(id);
        ProductResponse response = ProductResponse.builder().id(id).name("Updated Laptop").build();

        when(productRepository.findById(id)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(existingProduct)).thenReturn(existingProduct);
        when(productMapper.toResponse(existingProduct)).thenReturn(response);

        // When
        Optional<ProductResponse> result = productService.updateProduct(id, request);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Updated Laptop");
        verify(productMapper).updateEntityFromDto(request, existingProduct);
        verify(productRepository).save(existingProduct);
    }

    @Test
    void deleteProduct_shouldCallRepository() {
        // Given
        Long id = 1L;

        // When
        productService.deleteProduct(id);

        // Then
        verify(productRepository).deleteById(id);
    }
}
