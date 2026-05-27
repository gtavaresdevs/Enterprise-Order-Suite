package com.enterprise.ordersuite.orders.application.mapper;

import com.enterprise.ordersuite.orders.api.dto.OrderItemRequest;
import com.enterprise.ordersuite.orders.api.dto.OrderItemResponse;
import com.enterprise.ordersuite.orders.domain.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, imports = {BigDecimal.class})
public interface OrderItemMapper {

    @Mapping(target = "subtotal", expression = "java(item.getUnitPrice().multiply(new BigDecimal(item.getQuantity())))")
    OrderItemResponse toResponse(OrderItem item);

    OrderItem toEntity(OrderItemRequest request);
}
