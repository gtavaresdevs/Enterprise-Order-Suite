package com.enterprise.ordersuite.orders.application.service;

import com.enterprise.ordersuite.common.util.PagedResult;
import com.enterprise.ordersuite.identity.application.CurrentUserService;
import com.enterprise.ordersuite.orders.api.dto.OrderCreateRequest;
import com.enterprise.ordersuite.orders.api.dto.OrderItemRequest;
import com.enterprise.ordersuite.orders.api.dto.OrderResponse;
import com.enterprise.ordersuite.orders.api.dto.OrderUpdateRequest;
import com.enterprise.ordersuite.orders.application.mapper.OrderItemMapper;
import com.enterprise.ordersuite.orders.application.mapper.OrderMapper;
import com.enterprise.ordersuite.orders.domain.Order;
import com.enterprise.ordersuite.orders.domain.OrderHistory;
import com.enterprise.ordersuite.orders.domain.OrderItem;
import com.enterprise.ordersuite.orders.domain.OrderStatus;
import com.enterprise.ordersuite.orders.domain.exception.ProductNotFoundException;
import com.enterprise.ordersuite.orders.persistence.OrderHistoryRepository;
import com.enterprise.ordersuite.orders.persistence.OrderRepository;
import com.enterprise.ordersuite.products.application.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  private static final Long CURRENT_USER_ID = 1L;
  private static final String CURRENT_USER_EMAIL = "test@example.com";

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

  @BeforeEach
  void setUp() {
    MDC.put("requestId", "test-request-id");

    lenient()
      .when(currentUserService.getUserId())
      .thenReturn(CURRENT_USER_ID);

    lenient()
      .when(currentUserService.getEmail())
      .thenReturn(CURRENT_USER_EMAIL);
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void createOrder_setsCurrentUserAsCustomer_calculatesTotal_savesHistoryAndSendsNotification() {
    OrderItemRequest itemRequest1 = OrderItemRequest.builder()
      .productId(101L)
      .quantity(2)
      .unitPrice(new BigDecimal("25.00"))
      .build();

    OrderItemRequest itemRequest2 = OrderItemRequest.builder()
      .productId(102L)
      .quantity(1)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    OrderCreateRequest request = OrderCreateRequest.builder()
      .orderNumber("ORD-123")
      .items(List.of(itemRequest1, itemRequest2))
      .build();

    Order order = new Order();
    order.setId(1L);
    order.setStatus(OrderStatus.DELIVERED);
    order.setItems(new ArrayList<>());

    OrderItem orderItem1 = OrderItem.builder()
      .productId(101L)
      .quantity(2)
      .unitPrice(new BigDecimal("25.00"))
      .build();

    OrderItem orderItem2 = OrderItem.builder()
      .productId(102L)
      .quantity(1)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    OrderResponse response = OrderResponse.builder()
      .id(1L)
      .orderNumber("ORD-123")
      .totalAmount(new BigDecimal("60.00"))
      .build();

    when(productService.productExists(101L)).thenReturn(true);
    when(productService.productExists(102L)).thenReturn(true);
    when(orderMapper.toEntity(request)).thenReturn(order);
    when(orderItemMapper.toEntity(itemRequest1)).thenReturn(orderItem1);
    when(orderItemMapper.toEntity(itemRequest2)).thenReturn(orderItem2);
    when(orderRepository.save(order)).thenReturn(order);
    when(orderMapper.toResponse(order)).thenReturn(response);

    OrderResponse result = orderService.createOrder(request);

    assertThat(result).isSameAs(response);
    assertThat(order.getCustomerId()).isEqualTo(CURRENT_USER_ID);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(order.getItems()).containsExactly(orderItem1, orderItem2);
    assertThat(order.getTotalAmount())
      .isEqualByComparingTo(new BigDecimal("60.00"));

    verify(productService).productExists(101L);
    verify(productService).productExists(102L);
    verify(productService).decrementStock(101L, 2);
    verify(productService).decrementStock(102L, 1);

    verify(orderRepository).save(order);
    verify(orderMapper).toResponse(order);

    ArgumentCaptor<OrderHistory> historyCaptor =
      ArgumentCaptor.forClass(OrderHistory.class);

    verify(orderHistoryRepository).save(historyCaptor.capture());

    OrderHistory history = historyCaptor.getValue();

    assertThat(history.getOrderId()).isEqualTo(1L);
    assertThat(history.getFromStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(history.getToStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(history.getChangedBy()).isEqualTo(CURRENT_USER_EMAIL);
    assertThat(history.getTimestamp()).isNotNull();

    verify(notificationService).sendOrderUpdateNotification(order);
  }

  @Test
  void createOrder_whenProductDoesNotExist_throwsExceptionAndDoesNotModifyOrder() {
    OrderItemRequest itemRequest = OrderItemRequest.builder()
      .productId(999L)
      .quantity(1)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    OrderCreateRequest request = OrderCreateRequest.builder()
      .orderNumber("ORD-123")
      .items(List.of(itemRequest))
      .build();

    when(productService.productExists(999L)).thenReturn(false);

    assertThatThrownBy(() -> orderService.createOrder(request))
      .isInstanceOf(ProductNotFoundException.class);

    verify(productService).productExists(999L);
    verify(productService, never()).decrementStock(anyLong(), anyInt());
    verify(orderMapper, never()).toEntity(any());
    verify(orderRepository, never()).save(any());
    verify(orderHistoryRepository, never()).save(any());
    verify(notificationService, never()).sendOrderUpdateNotification(any());
  }

  @Test
  void createOrder_whenNoItems_setsTotalToZero() {
    OrderCreateRequest request = OrderCreateRequest.builder()
      .orderNumber("ORD-EMPTY")
      .items(List.of())
      .build();

    Order order = new Order();
    order.setId(1L);
    order.setItems(new ArrayList<>());

    OrderResponse response = new OrderResponse();

    when(orderMapper.toEntity(request)).thenReturn(order);
    when(orderRepository.save(order)).thenReturn(order);
    when(orderMapper.toResponse(order)).thenReturn(response);

    OrderResponse result = orderService.createOrder(request);

    assertThat(result).isSameAs(response);
    assertThat(order.getCustomerId()).isEqualTo(CURRENT_USER_ID);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(order.getTotalAmount())
      .isEqualByComparingTo(BigDecimal.ZERO);

    verify(orderRepository).save(order);
    verify(orderHistoryRepository).save(any(OrderHistory.class));
    verify(notificationService).sendOrderUpdateNotification(order);
    verifyNoInteractions(productService);
  }

  @Test
  void getOrderById_whenOrderExists_returnsMappedResponse() {
    Long orderId = 10L;

    Order order = Order.builder()
      .customerId(CURRENT_USER_ID)
      .build();

    order.setId(orderId);

    OrderResponse response = OrderResponse.builder()
      .id(orderId)
      .orderNumber("ORD-123")
      .build();

    when(orderRepository.findById(orderId))
      .thenReturn(Optional.of(order));

    when(orderMapper.toResponse(order))
      .thenReturn(response);

    Optional<OrderResponse> result =
      orderService.getOrderById(orderId);

    assertThat(result)
      .isPresent()
      .containsSame(response);

    verify(orderRepository).findById(orderId);
    verify(orderMapper).toResponse(order);
  }

  @Test
  void getOrderById_whenOrderDoesNotExist_returnsEmpty() {
    Long orderId = 999L;

    when(orderRepository.findById(orderId))
      .thenReturn(Optional.empty());

    Optional<OrderResponse> result =
      orderService.getOrderById(orderId);

    assertThat(result).isEmpty();

    verify(orderRepository).findById(orderId);
    verifyNoInteractions(orderMapper);
  }

  @Test
  void getAllOrders_asAdmin_usesFindAll() {
    setAuthenticatedUserAsAdmin();

    PageRequest pageable = PageRequest.of(0, 10);

    Order order = Order.builder()
      .customerId(10L)
      .build();

    order.setId(1L);

    OrderResponse response = OrderResponse.builder()
      .id(1L)
      .build();

    Page<Order> orderPage =
      new PageImpl<>(List.of(order), pageable, 1);

    when(orderRepository.findAll(pageable))
      .thenReturn(orderPage);

    when(orderMapper.toResponse(order))
      .thenReturn(response);

    Page<OrderResponse> result =
      orderService.getAllOrders(pageable);

    assertThat(result.getContent())
      .containsExactly(response);

    verify(orderRepository).findAll(pageable);
    verify(orderRepository, never())
      .searchOrders(any(), any(), any(), any());
    verify(orderMapper).toResponse(order);
  }

  @Test
  void getAllOrders_asRegularUser_searchesOnlyCurrentUsersOrders() {
    setAuthenticatedUserAsRegularUser();

    PageRequest pageable = PageRequest.of(0, 10);

    Order order = Order.builder()
      .customerId(CURRENT_USER_ID)
      .build();

    order.setId(1L);

    OrderResponse response = OrderResponse.builder()
      .id(1L)
      .build();

    Page<Order> orderPage =
      new PageImpl<>(List.of(order), pageable, 1);

    when(orderRepository.searchOrders(
      null,
      null,
      CURRENT_USER_ID,
      pageable
    )).thenReturn(orderPage);

    when(orderMapper.toResponse(order))
      .thenReturn(response);

    Page<OrderResponse> result =
      orderService.getAllOrders(pageable);

    assertThat(result.getContent())
      .containsExactly(response);

    verify(orderRepository)
      .searchOrders(null, null, CURRENT_USER_ID, pageable);

    verify(orderRepository, never()).findAll(any(Pageable.class));
    verify(orderMapper).toResponse(order);
  }

  @Test
  void searchOrders_asAdmin_preservesProvidedCustomerId() {
    setAuthenticatedUserAsAdmin();

    PageRequest pageable = PageRequest.of(0, 10);
    Long customerId = 50L;

    Order order = Order.builder()
      .customerId(customerId)
      .build();

    order.setId(1L);

    OrderResponse response = OrderResponse.builder()
      .id(1L)
      .build();

    Page<Order> orderPage =
      new PageImpl<>(List.of(order), pageable, 1);

    when(orderRepository.searchOrders(
      "ORD-123",
      OrderStatus.PROCESSING,
      customerId,
      pageable
    )).thenReturn(orderPage);

    when(orderMapper.toResponse(order))
      .thenReturn(response);

    PagedResult<OrderResponse> result =
      orderService.searchOrders(
        "ORD-123",
        OrderStatus.PROCESSING,
        customerId,
        pageable
      );

    assertThat(result).isNotNull();

    verify(orderRepository).searchOrders(
      "ORD-123",
      OrderStatus.PROCESSING,
      customerId,
      pageable
    );
  }

  @Test
  void searchOrders_asRegularUser_overridesProvidedCustomerIdWithCurrentUser() {
    setAuthenticatedUserAsRegularUser();

    PageRequest pageable = PageRequest.of(0, 10);
    Long requestedCustomerId = 999L;

    Page<Order> orderPage =
      new PageImpl<>(List.of(), pageable, 0);

    when(orderRepository.searchOrders(
      "ORD-123",
      OrderStatus.PENDING,
      CURRENT_USER_ID,
      pageable
    )).thenReturn(orderPage);

    PagedResult<OrderResponse> result =
      orderService.searchOrders(
        "ORD-123",
        OrderStatus.PENDING,
        requestedCustomerId,
        pageable
      );

    assertThat(result).isNotNull();

    verify(orderRepository).searchOrders(
      "ORD-123",
      OrderStatus.PENDING,
      CURRENT_USER_ID,
      pageable
    );
  }

  @Test
  void updateOrder_whenTransitioningToCancelled_restoresStockAndSavesHistory() {
    Long orderId = 1L;

    OrderItem existingItem = OrderItem.builder()
      .productId(101L)
      .quantity(3)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    Order existingOrder = Order.builder()
      .customerId(CURRENT_USER_ID)
      .status(OrderStatus.PENDING)
      .items(new ArrayList<>(List.of(existingItem)))
      .build();

    existingOrder.setId(orderId);

    OrderUpdateRequest request = OrderUpdateRequest.builder()
      .status(OrderStatus.CANCELLED)
      .build();

    OrderResponse response = OrderResponse.builder()
      .id(orderId)
      .build();

    when(orderRepository.findById(orderId))
      .thenReturn(Optional.of(existingOrder));

    when(orderRepository.save(existingOrder))
      .thenReturn(existingOrder);

    when(orderMapper.toResponse(existingOrder))
      .thenReturn(response);

    OrderResponse result =
      orderService.updateOrder(orderId, request)
        .orElseThrow();

    assertThat(result).isSameAs(response);
    assertThat(existingOrder.getStatus())
      .isEqualTo(OrderStatus.CANCELLED);

    verify(productService)
      .incrementStock(101L, 3);

    verify(orderRepository)
      .save(existingOrder);

    ArgumentCaptor<OrderHistory> historyCaptor =
      ArgumentCaptor.forClass(OrderHistory.class);

    verify(orderHistoryRepository)
      .save(historyCaptor.capture());

    OrderHistory history = historyCaptor.getValue();

    assertThat(history.getOrderId()).isEqualTo(orderId);
    assertThat(history.getFromStatus())
      .isEqualTo(OrderStatus.PENDING);
    assertThat(history.getToStatus())
      .isEqualTo(OrderStatus.CANCELLED);
    assertThat(history.getChangedBy())
      .isEqualTo(CURRENT_USER_EMAIL);
    assertThat(history.getTimestamp())
      .isNotNull();

    verify(notificationService)
      .sendOrderUpdateNotification(existingOrder);
  }

  @Test
  void updateOrder_normalStatusTransition_doesNotRestoreStock() {
    Long orderId = 1L;

    OrderItem existingItem = OrderItem.builder()
      .productId(101L)
      .quantity(3)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    Order existingOrder = Order.builder()
      .customerId(CURRENT_USER_ID)
      .status(OrderStatus.PENDING)
      .items(new ArrayList<>(List.of(existingItem)))
      .build();

    existingOrder.setId(orderId);

    OrderUpdateRequest request = OrderUpdateRequest.builder()
      .status(OrderStatus.PROCESSING)
      .build();

    when(orderRepository.findById(orderId))
      .thenReturn(Optional.of(existingOrder));

    when(orderRepository.save(existingOrder))
      .thenReturn(existingOrder);

    when(orderMapper.toResponse(existingOrder))
      .thenReturn(new OrderResponse());

    orderService.updateOrder(orderId, request);

    assertThat(existingOrder.getStatus())
      .isEqualTo(OrderStatus.PROCESSING);

    verify(productService, never())
      .incrementStock(anyLong(), anyInt());

    verify(orderHistoryRepository)
      .save(any(OrderHistory.class));

    verify(notificationService)
      .sendOrderUpdateNotification(existingOrder);

    verify(orderRepository)
      .save(existingOrder);
  }

  @Test
  void updateOrder_whenItemsProvided_replacesItemsAndRecalculatesTotal() {
    Long orderId = 1L;

    Order existingOrder = new Order();
    existingOrder.setId(orderId);
    existingOrder.setCustomerId(CURRENT_USER_ID);
    existingOrder.setStatus(OrderStatus.PENDING);
    existingOrder.setItems(new ArrayList<>());

    OrderItemRequest itemRequest = OrderItemRequest.builder()
      .productId(202L)
      .quantity(3)
      .unitPrice(new BigDecimal("15.00"))
      .build();

    OrderUpdateRequest request = OrderUpdateRequest.builder()
      .items(List.of(itemRequest))
      .build();

    OrderItem newItem = OrderItem.builder()
      .productId(202L)
      .quantity(3)
      .unitPrice(new BigDecimal("15.00"))
      .build();

    when(orderRepository.findById(orderId))
      .thenReturn(Optional.of(existingOrder));

    when(productService.productExists(202L))
      .thenReturn(true);

    when(orderItemMapper.toEntity(itemRequest))
      .thenReturn(newItem);

    when(orderRepository.save(existingOrder))
      .thenReturn(existingOrder);

    when(orderMapper.toResponse(existingOrder))
      .thenReturn(new OrderResponse());

    orderService.updateOrder(orderId, request);

    assertThat(existingOrder.getItems())
      .containsExactly(newItem);

    assertThat(existingOrder.getTotalAmount())
      .isEqualByComparingTo(new BigDecimal("45.00"));

    verify(productService)
      .productExists(202L);

    verify(orderItemMapper)
      .toEntity(itemRequest);

    verify(orderRepository)
      .save(existingOrder);
  }

  @Test
  void updateOrder_whenProductDoesNotExist_throwsExceptionBeforeSaving() {
    Long orderId = 1L;

    Order existingOrder = Order.builder()
      .customerId(CURRENT_USER_ID)
      .status(OrderStatus.PENDING)
      .items(new ArrayList<>())
      .build();

    existingOrder.setId(orderId);

    OrderItemRequest itemRequest = OrderItemRequest.builder()
      .productId(999L)
      .quantity(1)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    OrderUpdateRequest request = OrderUpdateRequest.builder()
      .items(List.of(itemRequest))
      .build();

    when(orderRepository.findById(orderId))
      .thenReturn(Optional.of(existingOrder));

    when(productService.productExists(999L))
      .thenReturn(false);

    assertThatThrownBy(() ->
      orderService.updateOrder(orderId, request)
    ).isInstanceOf(ProductNotFoundException.class);

    verify(productService)
      .productExists(999L);

    verify(orderRepository, never())
      .save(any(Order.class));

    verify(orderItemMapper, never())
      .toEntity(any(OrderItemRequest.class));
  }

  @Test
  void updateOrder_whenOrderDoesNotExist_returnsEmpty() {
    Long orderId = 999L;

    OrderUpdateRequest request = OrderUpdateRequest.builder()
      .status(OrderStatus.PROCESSING)
      .build();

    when(orderRepository.findById(orderId))
      .thenReturn(Optional.empty());

    Optional<OrderResponse> result =
      orderService.updateOrder(orderId, request);

    assertThat(result).isEmpty();

    verify(orderRepository).findById(orderId);
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void deleteOrder_deletesOrderById() {
    Long orderId = 10L;

    orderService.deleteOrder(orderId);

    verify(orderRepository)
      .deleteById(orderId);
  }

  @Test
  void isOrderOwner_whenUserOwnsOrder_returnsTrue() {
    Long orderId = 10L;

    Order order = Order.builder()
      .customerId(CURRENT_USER_ID)
      .build();

    when(orderRepository.findById(orderId))
      .thenReturn(Optional.of(order));

    boolean result =
      orderService.isOrderOwner(orderId, CURRENT_USER_ID);

    assertThat(result).isTrue();

    verify(orderRepository)
      .findById(orderId);
  }

  @Test
  void isOrderOwner_whenUserDoesNotOwnOrder_returnsFalse() {
    Long orderId = 10L;

    Order order = Order.builder()
      .customerId(99L)
      .build();

    when(orderRepository.findById(orderId))
      .thenReturn(Optional.of(order));

    boolean result =
      orderService.isOrderOwner(orderId, CURRENT_USER_ID);

    assertThat(result).isFalse();

    verify(orderRepository)
      .findById(orderId);
  }

  @Test
  void isOrderOwner_whenOrderDoesNotExist_returnsFalse() {
    Long orderId = 999L;

    when(orderRepository.findById(orderId))
      .thenReturn(Optional.empty());

    boolean result =
      orderService.isOrderOwner(orderId, CURRENT_USER_ID);

    assertThat(result).isFalse();

    verify(orderRepository)
      .findById(orderId);
  }

  private void setAuthenticatedUserAsAdmin() {
    SecurityContextHolder.getContext().setAuthentication(
      new UsernamePasswordAuthenticationToken(
        CURRENT_USER_ID,
        null,
        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
      )
    );
  }

  private void setAuthenticatedUserAsRegularUser() {
    SecurityContextHolder.getContext().setAuthentication(
      new UsernamePasswordAuthenticationToken(
        CURRENT_USER_ID,
        null,
        List.of(new SimpleGrantedAuthority("ROLE_USER"))
      )
    );
  }
}
