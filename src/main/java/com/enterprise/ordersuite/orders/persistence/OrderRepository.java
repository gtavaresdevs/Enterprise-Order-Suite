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

    // CAST(:orderNumber AS string) is required, not cosmetic: with a null orderNumber
    // Postgres cannot infer the parameter's type inside CONCAT and falls back to bytea,
    // so the query fails with "function lower(bytea) does not exist". The cast pins the
    // type, which every caller passing a null orderNumber depends on.
    @Query("SELECT o FROM Order o WHERE " +
            "(:orderNumber IS NULL OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', CAST(:orderNumber AS string), '%'))) AND " +
            "(:status IS NULL OR o.status = :status) AND " +
            "(:customerId IS NULL OR o.customerId = :customerId)")
    Page<Order> searchOrders(@Param("orderNumber") String orderNumber,
                             @Param("status") OrderStatus status,
                             @Param("customerId") Long customerId,
                             Pageable pageable);
}
