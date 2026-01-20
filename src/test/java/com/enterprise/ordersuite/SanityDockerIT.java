package com.enterprise.ordersuite;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class SanityDockerIT {   // <-- make it public

    @Container
    static GenericContainer<?> alpine = new GenericContainer<>("alpine:3.20")
            .withCommand("sh", "-c", "echo hello && sleep 1");

    @Test
    void containerStarts() {
        assertTrue(alpine.isRunning());
    }
}
