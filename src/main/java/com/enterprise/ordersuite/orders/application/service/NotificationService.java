package com.enterprise.ordersuite.orders.application.service;

import com.enterprise.ordersuite.orders.domain.Order;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationService {

    @Async
    public void sendOrderUpdateNotification(Order order) {
        String requestId = MDC.get("requestId");
        log.info("requestId: {} - Sending notification for order update: ID={}, Status={}", 
                requestId, order.getId(), order.getStatus());

        // KAFKA_HOOK: Future Kafka producer for 'order-events' topic goes here
        // producer.send("order-events", new OrderEvent(order.getId(), order.getStatus()));
        
        try {
            // Simulate processing time
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Notification processing interrupted", e);
        }

        log.info("requestId: {} - Notification sent successfully for order: {}", requestId, order.getId());
    }
}
