package com.enterprise.ordersuite.orders.application.mapper;

import com.enterprise.ordersuite.orders.api.dto.OrderCreateRequest;
import com.enterprise.ordersuite.orders.api.dto.OrderResponse;
import com.enterprise.ordersuite.orders.api.dto.OrderUpdateRequest;
import com.enterprise.ordersuite.orders.domain.Order;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = {OrderItemMapper.class})
public interface OrderMapper {

    @Mapping(target = "items", ignore = true)
    Order toEntity(OrderCreateRequest request);

    OrderResponse toResponse(Order order);

    // Items are mapped by OrderService, never here. It is the only place that can take the
    // stock an item costs and resolve its price from the catalogue, and a mapper that built
    // OrderItems from an OrderItemRequest would carry the client's unitPrice straight onto
    // the order.
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "items", ignore = true)
    void updateEntityFromDto(OrderUpdateRequest request, @MappingTarget Order order);
}
