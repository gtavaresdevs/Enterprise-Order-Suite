package com.enterprise.ordersuite.orders.application.mapper;

import com.enterprise.ordersuite.orders.api.dto.OrderCreateRequest;
import com.enterprise.ordersuite.orders.api.dto.OrderResponse;
import com.enterprise.ordersuite.orders.api.dto.OrderUpdateRequest;
import com.enterprise.ordersuite.orders.domain.Order;
import com.enterprise.ordersuite.orders.domain.OrderItem;
import org.mapstruct.*;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = {OrderItemMapper.class})
public interface OrderMapper {

    @Mapping(target = "items", ignore = true)
    Order toEntity(OrderCreateRequest request);

    OrderResponse toResponse(Order order);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "items", ignore = true)
    void updateEntityFromDto(OrderUpdateRequest request, @MappingTarget Order order);

    default List<OrderItem> mapOrderItemRequestsToOrderItems(List<com.enterprise.ordersuite.orders.api.dto.OrderItemRequest> itemRequests) {
        return itemRequests.stream()
                .map(request -> OrderItem.builder()
                        .productId(request.getProductId())
                        .quantity(request.getQuantity())
                        .unitPrice(request.getUnitPrice())
                        .build())
                .collect(Collectors.toList());
    }
}
