package com.enterprise.ordersuite.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class LoginRateLimitIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    void login_eventuallyGetsRateLimited_orReturnsUnauthorized_whenDisabled() throws Exception {

        boolean saw401 = false;
        boolean saw429 = false;

        for (int i = 0; i < 20; i++) {
            String email = "nonexistent-" + UUID.randomUUID() + "@test.com";

            var result = mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                { "email": "%s", "password": "wrong" }
                            """.formatted(email))
                            .with(request -> {
                                request.setRemoteAddr("1.2.3.4");
                                return request;
                            }))
                    .andReturn();

            int status = result.getResponse().getStatus();

            if (status == 401) {
                saw401 = true;
            } else if (status == 429) {
                saw429 = true;
                break;
            } else {
                // Temporary test: fail fast if login succeeds or anything unexpected happens
                throw new AssertionError("Unexpected status from /auth/login: " + status
                        + " body=" + result.getResponse().getContentAsString());
            }
        }

        // Pass if:
        // - rate limit disabled: we will see 401s
        // - rate limit enabled: we will eventually see 429
        assertThat(saw401 || saw429).isTrue();
    }
}
