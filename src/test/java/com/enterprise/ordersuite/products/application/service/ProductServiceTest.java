package com.enterprise.ordersuite.products.application.service;

import com.enterprise.ordersuite.common.util.PagedResult;
import com.enterprise.ordersuite.orders.domain.exception.ProductNotFoundException;
import com.enterprise.ordersuite.products.api.dto.ProductRequest;
import com.enterprise.ordersuite.products.api.dto.ProductResponse;
import com.enterprise.ordersuite.products.application.mapper.ProductMapper;
import com.enterprise.ordersuite.products.domain.Product;
import com.enterprise.ordersuite.products.domain.exception.InsufficientStockException;
import com.enterprise.ordersuite.products.persistence.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

  @AfterEach
  void tearDown() {
    MDC.remove("requestId");
  }

  @Test
  void createProduct_shouldSaveAndReturnResponse() {
    ProductRequest request = ProductRequest.builder()
      .name("Laptop")
      .sku("LAP-001")
      .price(new BigDecimal("999.99"))
      .stockQuantity(10)
      .build();

    Product product = new Product();

    Product savedProduct = new Product();
    savedProduct.setId(1L);

    ProductResponse response = ProductResponse.builder()
      .id(1L)
      .sku("LAP-001")
      .build();

    when(productMapper.toEntity(request)).thenReturn(product);
    when(productRepository.save(product)).thenReturn(savedProduct);
    when(productMapper.toResponse(savedProduct)).thenReturn(response);

    ProductResponse result = productService.createProduct(request);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(1L);
    assertThat(result.getSku()).isEqualTo("LAP-001");

    verify(productMapper).toEntity(request);
    verify(productRepository).save(product);
    verify(productMapper).toResponse(savedProduct);
  }

  @Test
  void getProductById_shouldReturnResponseWhenProductExists() {
    Long id = 1L;

    Product product = new Product();
    product.setId(id);

    ProductResponse response = ProductResponse.builder()
      .id(id)
      .build();

    when(productRepository.findById(id)).thenReturn(Optional.of(product));
    when(productMapper.toResponse(product)).thenReturn(response);

    Optional<ProductResponse> result = productService.getProductById(id);

    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(id);

    verify(productRepository).findById(id);
    verify(productMapper).toResponse(product);
  }

  @Test
  void getProductById_shouldReturnEmptyWhenProductDoesNotExist() {
    Long id = 999L;

    when(productRepository.findById(id)).thenReturn(Optional.empty());

    Optional<ProductResponse> result = productService.getProductById(id);

    assertThat(result).isEmpty();

    verify(productRepository).findById(id);
    verifyNoInteractions(productMapper);
  }

  @Test
  void getAllProducts_shouldReturnPagedResultWithMappedProducts() {
    Pageable pageable = PageRequest.of(0, 10);

    Product firstProduct = Product.builder()
      .name("Laptop")
      .sku("LAP-001")
      .price(new BigDecimal("999.99"))
      .stockQuantity(10)
      .build();

    Product secondProduct = Product.builder()
      .name("Mouse")
      .sku("MOU-001")
      .price(new BigDecimal("29.99"))
      .stockQuantity(50)
      .build();

    ProductResponse firstResponse = ProductResponse.builder()
      .sku("LAP-001")
      .build();

    ProductResponse secondResponse = ProductResponse.builder()
      .sku("MOU-001")
      .build();

    Page<Product> productPage = new PageImpl<>(
      List.of(firstProduct, secondProduct),
      pageable,
      2
    );

    when(productRepository.findAll(pageable)).thenReturn(productPage);
    when(productMapper.toResponse(firstProduct)).thenReturn(firstResponse);
    when(productMapper.toResponse(secondProduct)).thenReturn(secondResponse);

    PagedResult<ProductResponse> result = productService.getAllProducts(pageable);

    assertThat(result).isNotNull();

    verify(productRepository).findAll(pageable);
    verify(productMapper).toResponse(firstProduct);
    verify(productMapper).toResponse(secondProduct);
  }

  @Test
  void updateProduct_shouldUpdateAndSaveWhenProductExists() {
    Long id = 1L;

    ProductRequest request = ProductRequest.builder()
      .name("Updated Laptop")
      .build();

    Product existingProduct = new Product();
    existingProduct.setId(id);

    ProductResponse response = ProductResponse.builder()
      .id(id)
      .name("Updated Laptop")
      .build();

    when(productRepository.findById(id)).thenReturn(Optional.of(existingProduct));
    when(productRepository.save(existingProduct)).thenReturn(existingProduct);
    when(productMapper.toResponse(existingProduct)).thenReturn(response);

    Optional<ProductResponse> result =
      productService.updateProduct(id, request);

    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(id);
    assertThat(result.get().getName()).isEqualTo("Updated Laptop");

    verify(productRepository).findById(id);
    verify(productMapper).updateEntityFromDto(request, existingProduct);
    verify(productRepository).save(existingProduct);
    verify(productMapper).toResponse(existingProduct);
  }

  @Test
  void updateProduct_shouldReturnEmptyWhenProductDoesNotExist() {
    Long id = 999L;

    ProductRequest request = ProductRequest.builder()
      .name("Updated Laptop")
      .build();

    when(productRepository.findById(id)).thenReturn(Optional.empty());

    Optional<ProductResponse> result =
      productService.updateProduct(id, request);

    assertThat(result).isEmpty();

    verify(productRepository).findById(id);
    verifyNoInteractions(productMapper);
  }

  @Test
  void deleteProduct_shouldCallRepository() {
    Long id = 1L;

    productService.deleteProduct(id);

    verify(productRepository).deleteById(id);
  }

  @Test
  void productExists_shouldReturnTrueWhenProductExists() {
    Long productId = 1L;

    when(productRepository.existsById(productId)).thenReturn(true);

    boolean result = productService.productExists(productId);

    assertThat(result).isTrue();

    verify(productRepository).existsById(productId);
  }

  @Test
  void productExists_shouldReturnFalseWhenProductDoesNotExist() {
    Long productId = 999L;

    when(productRepository.existsById(productId)).thenReturn(false);

    boolean result = productService.productExists(productId);

    assertThat(result).isFalse();

    verify(productRepository).existsById(productId);
  }

  @Test
  void decrementStock_shouldDecreaseStockAndSaveProduct() {
    Long productId = 1L;
    int initialStock = 10;
    int quantity = 3;

    Product product = Product.builder()
      .name("Laptop")
      .sku("LAP-001")
      .price(new BigDecimal("999.99"))
      .stockQuantity(initialStock)
      .build();

    when(productRepository.findById(productId))
      .thenReturn(Optional.of(product));

    productService.decrementStock(productId, quantity);

    assertThat(product.getStockQuantity()).isEqualTo(7);

    verify(productRepository).findById(productId);
    verify(productRepository).save(product);
  }

  @Test
  void decrementStock_shouldThrowProductNotFoundExceptionWhenProductDoesNotExist() {
    Long productId = 999L;
    int quantity = 3;

    when(productRepository.findById(productId))
      .thenReturn(Optional.empty());

    assertThatThrownBy(() ->
      productService.decrementStock(productId, quantity)
    )
      .isInstanceOf(ProductNotFoundException.class);

    verify(productRepository).findById(productId);
  }

  @Test
  void decrementStock_shouldThrowInsufficientStockExceptionWhenQuantityExceedsStock() {
    Long productId = 1L;
    int initialStock = 2;
    int quantity = 5;

    Product product = Product.builder()
      .name("Laptop")
      .sku("LAP-001")
      .price(new BigDecimal("999.99"))
      .stockQuantity(initialStock)
      .build();

    when(productRepository.findById(productId))
      .thenReturn(Optional.of(product));

    assertThatThrownBy(() ->
      productService.decrementStock(productId, quantity)
    )
      .isInstanceOf(InsufficientStockException.class);

    assertThat(product.getStockQuantity()).isEqualTo(initialStock);

    verify(productRepository).findById(productId);
  }

  @Test
  void incrementStock_shouldIncreaseStockAndSaveProduct() {
    Long productId = 1L;
    int initialStock = 10;
    int quantity = 5;

    Product product = Product.builder()
      .name("Laptop")
      .sku("LAP-001")
      .price(new BigDecimal("999.99"))
      .stockQuantity(initialStock)
      .build();

    when(productRepository.findById(productId))
      .thenReturn(Optional.of(product));

    productService.incrementStock(productId, quantity);

    assertThat(product.getStockQuantity()).isEqualTo(15);

    verify(productRepository).findById(productId);
    verify(productRepository).save(product);
  }

  @Test
  void incrementStock_shouldThrowProductNotFoundExceptionWhenProductDoesNotExist() {
    Long productId = 999L;
    int quantity = 5;

    when(productRepository.findById(productId))
      .thenReturn(Optional.empty());

    assertThatThrownBy(() ->
      productService.incrementStock(productId, quantity)
    )
      .isInstanceOf(ProductNotFoundException.class);

    verify(productRepository).findById(productId);
  }
}
