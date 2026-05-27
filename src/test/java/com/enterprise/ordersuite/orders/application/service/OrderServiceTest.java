package com.enterprise.ordersuite.orders.application.service;

import com.enterprise.ordersuite.identity.application.CurrentUserService;
import com.enterprise.ordersuite.orders.api.dto.*;
import com.enterprise.ordersuite.orders.application.mapper.OrderItemMapper;
import com.enterprise.ordersuite.orders.application.mapper.OrderMapper;
import com.enterprise.ordersuite.orders.domain.Order;
import com.enterprise.ordersuite.orders.domain.OrderHistory;
import com.enterprise.ordersuite.orders.domain.OrderItem;
import com.enterprise.ordersuite.orders.domain.OrderStatus;
import com.enterprise.ordersuite.orders.domain.exception.InvalidStatusTransitionException;
import com.enterprise.ordersuite.orders.domain.exception.ProductNotFoundException;
import com.enterprise.ordersuite.orders.persistence.OrderHistoryRepository;
import com.enterprise.ordersuite.orders.persistence.OrderRepository;
import com.enterprise.ordersuite.products.application.service.ProductService;
import com.enterprise.ordersuite.products.domain.exception.InsufficientStockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderHistoryRepository orderHistoryRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ProductService productService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private OrderService orderService;

    private static final Long CURRENT_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        MDC.put("requestId", "test-request-id");
        lenient().when(currentUserService.getUserId()).thenReturn(CURRENT_USER_ID);
        lenient().when(currentUserService.getEmail()).thenReturn("test@example.com");
    }

    @Test
    void createOrder_shouldSetCurrentUserIdAsCustomerId() {
        // Given
        OrderCreateRequest request = OrderCreateRequest.builder()
                .orderNumber("ORD-123")
                .items(List.of())
                .build();

        Order order = new Order();
        order.setId(1L);
        order.setItems(new ArrayList<>());
        
        when(orderMapper.toEntity(request)).thenReturn(order);
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(orderMapper.toResponse(any(Order.class))).thenReturn(new OrderResponse());

        // When
        orderService.createOrder(request);

        // Then
        assertThat(order.getCustomerId()).isEqualTo(CURRENT_USER_ID);
        verify(orderRepository).save(order);
    }

    @Test
    void isOrderOwner_shouldReturnTrue_whenUserIsOwner() {
        // Given
        Long orderId = 10L;
        Order order = Order.builder().customerId(CURRENT_USER_ID).build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // When
        boolean result = orderService.isOrderOwner(orderId, CURRENT_USER_ID);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isOrderOwner_shouldReturnFalse_whenUserIsNotOwner() {
        // Given
        Long orderId = 10L;
        Order order = Order.builder().customerId(99L).build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // When
        boolean result = orderService.isOrderOwner(orderId, CURRENT_USER_ID);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void createOrder_shouldCalculateTotalAndSave_whenProductsExistAndStockAvailable() {
        // Given
        OrderItemRequest itemRequest1 = OrderItemRequest.builder()
                .productId(101L).quantity(2).unitPrice(new BigDecimal("25.00"))
                .build();
        OrderItemRequest itemRequest2 = OrderItemRequest.builder()
                .productId(102L).quantity(1).unitPrice(new BigDecimal("10.00"))
                .build();
        
        OrderCreateRequest request = OrderCreateRequest.builder()
                .orderNumber("ORD-123")
                .items(List.of(itemRequest1, itemRequest2))
                .build();

        Order order = new Order();
        order.setId(1L);
        order.setItems(new ArrayList<>());
        
        OrderItem orderItem1 = OrderItem.builder().productId(101L).quantity(2).unitPrice(new BigDecimal("25.00")).build();
        OrderItem orderItem2 = OrderItem.builder().productId(102L).quantity(1).unitPrice(new BigDecimal("10.00")).build();
        
        OrderResponse expectedResponse = OrderResponse.builder().id(1L).orderNumber("ORD-123").totalAmount(new BigDecimal("60.00")).build();

        when(productService.productExists(101L)).thenReturn(true);
        when(productService.productExists(102L)).thenReturn(true);
        
        when(orderMapper.toEntity(request)).thenReturn(order);
        when(orderItemMapper.toEntity(itemRequest1)).thenReturn(orderItem1);
        when(orderItemMapper.toEntity(itemRequest2)).thenReturn(orderItem2);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any(Order.class))).thenReturn(expectedResponse);

        // When
        OrderResponse result = orderService.createOrder(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("60.00"));
        verify(productService).decrementStock(101L, 2);
        verify(productService).decrementStock(102L, 1);
        verify(orderRepository).save(order);
    }

    @Test
    void createOrder_shouldThrowException_whenProductDoesNotExist() {
        // Given
        OrderItemRequest itemRequest = OrderItemRequest.builder()
                .productId(999L).quantity(1).unitPrice(new BigDecimal("10.00"))
                .build();
        OrderCreateRequest request = OrderCreateRequest.builder()
                .orderNumber("ORD-123").items(List.of(itemRequest))
                .build();

        when(productService.productExists(999L)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void updateOrder_shouldRecalculateTotalAndUpdateItems_whenProductsExist() {
        // Given
        Long id = 1L;
        OrderItemRequest newItemRequest = OrderItemRequest.builder()
                .productId(202L).quantity(3).unitPrice(new BigDecimal("15.00"))
                .build();
        OrderUpdateRequest updateRequest = OrderUpdateRequest.builder()
                .status(OrderStatus.PROCESSING)
                .items(List.of(newItemRequest))
                .build();

        Order existingOrder = new Order();
        existingOrder.setId(id);
        existingOrder.setStatus(OrderStatus.PENDING);
        existingOrder.setItems(new ArrayList<>());
        
        OrderItem newItem = OrderItem.builder().productId(202L).quantity(3).unitPrice(new BigDecimal("15.00")).build();

        when(orderRepository.findById(id)).thenReturn(Optional.of(existingOrder));
        when(productService.productExists(202L)).thenReturn(true);
        when(orderItemMapper.toEntity(newItemRequest)).thenReturn(newItem);
        when(orderRepository.save(existingOrder)).thenReturn(existingOrder);
        when(orderMapper.toResponse(existingOrder)).thenReturn(new OrderResponse());

        // When
        orderService.updateOrder(id, updateRequest);

        // Then
        assertThat(existingOrder.getItems()).hasSize(1);
        assertThat(existingOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("45.00"));
    }
}
