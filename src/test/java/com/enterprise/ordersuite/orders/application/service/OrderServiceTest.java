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
import com.enterprise.ordersuite.orders.domain.exception.OrderNotEditableException;
import com.enterprise.ordersuite.orders.domain.exception.ProductNotFoundException;
import com.enterprise.ordersuite.orders.persistence.OrderHistoryRepository;
import com.enterprise.ordersuite.orders.persistence.OrderRepository;
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
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
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

  // The orders-side interface, in this same package - not the products class that
  // implements it. OrderService is not allowed to know that class exists.
  @Mock
  private ProductService productService;

  @Mock
  private NotificationService notificationService;

  @Mock
  private RoleHierarchy roleHierarchy;

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

    // Identity expansion: these unit tests assert the admin/non-admin branches from the
    // authorities they set directly. The hierarchy's real expansion is proven against the
    // actual bean in OrderControllerIT.getAllOrders_asSuperAdmin_seesOrdersFromEveryCustomer.
    lenient()
      .when(roleHierarchy.getReachableGrantedAuthorities(any()))
      .thenAnswer(invocation -> invocation.getArgument(0));
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
    when(productService.getPrice(101L)).thenReturn(new BigDecimal("25.00"));
    when(productService.getPrice(102L)).thenReturn(new BigDecimal("10.00"));
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

    // The line prices are the catalogue's, not the request's - both itemRequests carried
    // the same numbers here, so the proof that they were re-derived is these lookups.
    verify(productService).getPrice(101L);
    verify(productService).getPrice(102L);

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
    verify(productService, never()).getPrice(anyLong());
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
  void getAllOrders_asRegularUser_listsOnlyCurrentUsersOrders() {
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

    when(orderRepository.findByCustomerId(CURRENT_USER_ID, pageable))
      .thenReturn(orderPage);

    when(orderMapper.toResponse(order))
      .thenReturn(response);

    Page<OrderResponse> result =
      orderService.getAllOrders(pageable);

    assertThat(result.getContent())
      .containsExactly(response);

    verify(orderRepository)
      .findByCustomerId(CURRENT_USER_ID, pageable);

    verify(orderRepository, never()).findAll(any(Pageable.class));

    // searchOrders treats a null customerId as "no filter"; a non-admin's list must never
    // be scoped by it, or one null user id away it returns every order in the database.
    verify(orderRepository, never())
      .searchOrders(any(), any(), any(), any());

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

    when(productService.getPrice(202L))
      .thenReturn(new BigDecimal("15.00"));

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

    verify(productService)
      .decrementStock(202L, 3);

    verify(orderItemMapper)
      .toEntity(itemRequest);

    verify(orderRepository)
      .save(existingOrder);
  }

  @Test
  void updateOrder_replacingItems_creditsTheOldItemsAndDebitsTheNewOnes() {
    Long orderId = 1L;

    OrderItem existingItem = OrderItem.builder()
      .productId(101L)
      .quantity(2)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    Order existingOrder = Order.builder()
      .customerId(CURRENT_USER_ID)
      .status(OrderStatus.PENDING)
      .items(new ArrayList<>(List.of(existingItem)))
      .build();

    existingOrder.setId(orderId);

    OrderItemRequest itemRequest = OrderItemRequest.builder()
      .productId(101L)
      .quantity(5)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    // Same status, so transitionTo returns early - the item replacement still runs, and
    // is the path that used to hand out stock the order had never paid for.
    OrderUpdateRequest request = OrderUpdateRequest.builder()
      .status(OrderStatus.PENDING)
      .items(List.of(itemRequest))
      .build();

    OrderItem newItem = OrderItem.builder()
      .productId(101L)
      .quantity(5)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    when(orderRepository.findById(orderId))
      .thenReturn(Optional.of(existingOrder));

    when(productService.productExists(101L))
      .thenReturn(true);

    when(productService.getPrice(101L))
      .thenReturn(new BigDecimal("10.00"));

    when(orderItemMapper.toEntity(itemRequest))
      .thenReturn(newItem);

    when(orderRepository.save(existingOrder))
      .thenReturn(existingOrder);

    when(orderMapper.toResponse(existingOrder))
      .thenReturn(new OrderResponse());

    orderService.updateOrder(orderId, request);

    verify(productService)
      .incrementStock(101L, 2);

    verify(productService)
      .decrementStock(101L, 5);
  }

  @Test
  void updateOrder_replacingItemsOnAShippedOrder_throwsOrderNotEditable() {
    // Also asks for a legal transition, SHIPPED -> DELIVERED: the rejection must win before
    // any of it - history, notification, save - happens.
    assertReplacingItemsIsRejected(OrderStatus.SHIPPED, OrderStatus.DELIVERED);
  }

  @Test
  void updateOrder_replacingItemsOnADeliveredOrder_throwsOrderNotEditable() {
    assertReplacingItemsIsRejected(OrderStatus.DELIVERED, OrderStatus.DELIVERED);
  }

  @Test
  void updateOrder_replacingItemsOnACancelledOrder_throwsOrderNotEditable() {
    assertReplacingItemsIsRejected(OrderStatus.CANCELLED, OrderStatus.CANCELLED);
  }

  // A SHIPPED or DELIVERED order consumed its stock for good and a CANCELLED one gave it back:
  // none of them is open. Replacing their items used to be accepted, and with an empty list
  // it rewrote totalAmount to zero with no history row. These tests used to assert only that
  // no stock moved; now the request is refused before anything is looked up, moved or saved,
  // which covers that and more. Product 999 does not exist: a closed order answers 409, not
  // PRODUCT_NOT_FOUND.
  private void assertReplacingItemsIsRejected(OrderStatus current, OrderStatus requested) {
    Long orderId = 1L;

    OrderItem existingItem = OrderItem.builder()
      .productId(101L)
      .quantity(2)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    Order existingOrder = Order.builder()
      .customerId(CURRENT_USER_ID)
      .status(current)
      .totalAmount(new BigDecimal("20.00"))
      .items(new ArrayList<>(List.of(existingItem)))
      .build();

    existingOrder.setId(orderId);

    OrderUpdateRequest request = OrderUpdateRequest.builder()
      .status(requested)
      .items(List.of(OrderItemRequest.builder()
        .productId(999L)
        .quantity(5)
        .unitPrice(new BigDecimal("10.00"))
        .build()))
      .build();

    when(orderRepository.findById(orderId))
      .thenReturn(Optional.of(existingOrder));

    assertThatThrownBy(() -> orderService.updateOrder(orderId, request))
      .isInstanceOf(OrderNotEditableException.class);

    assertThat(existingOrder.getItems())
      .as("the stored lines must survive a rejected request")
      .containsExactly(existingItem);

    assertThat(existingOrder.getTotalAmount())
      .as("the silent zeroing of totalAmount is the defect")
      .isEqualByComparingTo("20.00");

    assertThat(existingOrder.getStatus())
      .isEqualTo(current);

    verifyNoInteractions(productService, orderItemMapper, orderHistoryRepository, notificationService);

    verify(orderRepository, never())
      .save(any(Order.class));
  }

  @Test
  void updateOrder_replacingItemsOnAProcessingOrder_isAccepted() {
    Long orderId = 1L;

    OrderItem existingItem = OrderItem.builder()
      .productId(101L)
      .quantity(2)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    Order existingOrder = Order.builder()
      .customerId(CURRENT_USER_ID)
      .status(OrderStatus.PROCESSING)
      .items(new ArrayList<>(List.of(existingItem)))
      .build();

    existingOrder.setId(orderId);

    OrderItemRequest itemRequest = OrderItemRequest.builder()
      .productId(202L)
      .quantity(3)
      .unitPrice(new BigDecimal("15.00"))
      .build();

    OrderUpdateRequest request = OrderUpdateRequest.builder()
      .status(OrderStatus.PROCESSING)
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

    when(productService.getPrice(202L))
      .thenReturn(new BigDecimal("15.00"));

    when(orderItemMapper.toEntity(itemRequest))
      .thenReturn(newItem);

    when(orderRepository.save(existingOrder))
      .thenReturn(existingOrder);

    when(orderMapper.toResponse(existingOrder))
      .thenReturn(new OrderResponse());

    orderService.updateOrder(orderId, request);

    // A kitchen can still add a drink to an order being prepared.
    assertThat(existingOrder.getItems())
      .containsExactly(newItem);

    assertThat(existingOrder.getTotalAmount())
      .isEqualByComparingTo("45.00");

    verify(productService)
      .incrementStock(101L, 2);

    verify(productService)
      .decrementStock(202L, 3);
  }

  @Test
  void updateOrder_replacingItemsWhileShippingAProcessingOrder_isAccepted() {
    Long orderId = 1L;

    OrderItem existingItem = OrderItem.builder()
      .productId(101L)
      .quantity(2)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    Order existingOrder = Order.builder()
      .customerId(CURRENT_USER_ID)
      .status(OrderStatus.PROCESSING)
      .items(new ArrayList<>(List.of(existingItem)))
      .build();

    existingOrder.setId(orderId);

    OrderItemRequest itemRequest = OrderItemRequest.builder()
      .productId(202L)
      .quantity(3)
      .unitPrice(new BigDecimal("15.00"))
      .build();

    // Editability is judged on the order as it stands before the request. It is open, so the
    // replacement settles stock first and the transition to SHIPPED runs after it.
    OrderUpdateRequest request = OrderUpdateRequest.builder()
      .status(OrderStatus.SHIPPED)
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

    when(productService.getPrice(202L))
      .thenReturn(new BigDecimal("15.00"));

    when(orderItemMapper.toEntity(itemRequest))
      .thenReturn(newItem);

    when(orderRepository.save(existingOrder))
      .thenReturn(existingOrder);

    when(orderMapper.toResponse(existingOrder))
      .thenReturn(new OrderResponse());

    orderService.updateOrder(orderId, request);

    assertThat(existingOrder.getStatus())
      .isEqualTo(OrderStatus.SHIPPED);

    assertThat(existingOrder.getItems())
      .containsExactly(newItem);

    verify(productService)
      .incrementStock(101L, 2);

    verify(productService)
      .decrementStock(202L, 3);

    verify(orderHistoryRepository)
      .save(any(OrderHistory.class));
  }

  @Test
  void updateOrder_statusOnlyOnADeliveredOrder_isNotRejected() {
    Long orderId = 1L;

    OrderItem existingItem = OrderItem.builder()
      .productId(101L)
      .quantity(2)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    Order existingOrder = Order.builder()
      .customerId(CURRENT_USER_ID)
      .status(OrderStatus.DELIVERED)
      .items(new ArrayList<>(List.of(existingItem)))
      .build();

    existingOrder.setId(orderId);

    // No items key at all - what every status-only PUT in OrderControllerIT sends.
    OrderUpdateRequest request = OrderUpdateRequest.builder()
      .status(OrderStatus.DELIVERED)
      .build();

    when(orderRepository.findById(orderId))
      .thenReturn(Optional.of(existingOrder));

    when(orderRepository.save(existingOrder))
      .thenReturn(existingOrder);

    when(orderMapper.toResponse(existingOrder))
      .thenReturn(new OrderResponse());

    assertThat(orderService.updateOrder(orderId, request))
      .isPresent();

    assertThat(existingOrder.getItems())
      .containsExactly(existingItem);

    verifyNoInteractions(productService);
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

    verify(productService, never())
      .incrementStock(anyLong(), anyInt());

    verify(productService, never())
      .decrementStock(anyLong(), anyInt());

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
