package com.enterprise.ordersuite.support;

import com.enterprise.ordersuite.notifications.service.EmailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@TestConfiguration
public class TestEmailServiceConfig {

    @Bean
    @Primary
    public CapturingEmailService capturingEmailService() {
        return new CapturingEmailService();
    }

    public static class CapturingEmailService implements EmailService {

        private final List<SentEmail> sent = new CopyOnWriteArrayList<>();
        private final AtomicBoolean fail = new AtomicBoolean(false);

        @Override
        public void sendPasswordResetEmail(String toEmail, String resetUrl) {
            if (fail.get()) {
                throw new RuntimeException("Simulated email provider failure");
            }
            sent.add(new SentEmail(toEmail, resetUrl));
        }

        public List<SentEmail> sent() {
            return sent;
        }

        public void clear() {
            sent.clear();
            fail.set(false);
        }

        public void failNext(boolean enabled) {
            fail.set(enabled);
        }

        public record SentEmail(String toEmail, String resetUrl) { }
    }
}