package com.enterprise.ordersuite.orders.persistence;

import com.enterprise.ordersuite.orders.domain.Order;
import com.enterprise.ordersuite.orders.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);

    @Query("SELECT o FROM Order o WHERE " +
            "(:orderNumber IS NULL OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :orderNumber, '%'))) AND " +
            "(:status IS NULL OR o.status = :status) AND " +
            "(:customerId IS NULL OR o.customerId = :customerId)")
    Page<Order> searchOrders(@Param("orderNumber") String orderNumber,
                             @Param("status") OrderStatus status,
                             @Param("customerId") Long customerId,
                             Pageable pageable);
}
