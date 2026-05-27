package com.enterprise.ordersuite.repository;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@ActiveProfiles("test")
public abstract class AbstractPostgresRepositoryTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("ordersuite_test")
            .withUsername("test")
            .withPassword("test")
            .withStartupAttempts(3)
            .withReuse(true); // Added for robustness and reuse across tests

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void registerDataSourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        // Ensures Flyway runs and aligns schema with your migrations
        registry.add("spring.flyway.enabled", () -> "true");
    }
}
