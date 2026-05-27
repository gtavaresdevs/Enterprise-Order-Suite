package com.enterprise.ordersuite.orders.domain;

import com.enterprise.ordersuite.common.persistence.BaseEntity;
import com.enterprise.ordersuite.orders.domain.exception.InvalidStatusTransitionException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
@EqualsAndHashCode(callSuper = true, exclude = "items")
@ToString(exclude = "items")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }

    public void transitionTo(OrderStatus newStatus) {
        if (this.status == newStatus) {
            return;
        }

        if (isTerminalState(this.status)) {
            throw new InvalidStatusTransitionException(this.status, newStatus);
        }

        boolean valid = switch (this.status) {
            case PENDING -> newStatus == OrderStatus.PROCESSING || newStatus == OrderStatus.CANCELLED;
            case PROCESSING -> newStatus == OrderStatus.SHIPPED || newStatus == OrderStatus.CANCELLED;
            case SHIPPED -> newStatus == OrderStatus.DELIVERED;
            default -> false;
        };

        if (!valid) {
            throw new InvalidStatusTransitionException(this.status, newStatus);
        }

        this.status = newStatus;
    }

    private boolean isTerminalState(OrderStatus status) {
        return status == OrderStatus.CANCELLED || status == OrderStatus.DELIVERED;
    }
}
