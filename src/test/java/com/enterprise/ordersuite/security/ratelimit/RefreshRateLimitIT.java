package com.enterprise.ordersuite.security.ratelimit;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.dtos.RefreshRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshRateLimitIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void refresh_is_rate_limited() throws Exception {
        String ip = "10.10.10.99";

        // IMPORTANT: set valid seeded user credentials
        AuthRequest login = new AuthRequest("gtavaresdev+enterpriseordersuitetestrefreshtokentest@gmail.com", "Test123!");

        var loginRes = mockMvc.perform(post("/auth/login")
                        .with(req -> { req.setRemoteAddr(ip); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        String refreshToken = objectMapper.readTree(loginRes.getResponse().getContentAsString())
                .get("refreshToken").asText();

        // Hammer /auth/refresh until we see 429 (RATE_LIMITED).
        // Note: refresh token rotation may cause some calls to return 400 after the first success,
        // but the rate limiter should still eventually deny and return 429.
        for (int i = 0; i < 100; i++) {
            var result = mockMvc.perform(post("/auth/refresh")
                            .with(req -> { req.setRemoteAddr(ip); return req; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                    .andReturn();

            int status = result.getResponse().getStatus();

            if (status == 429) {
                String body = result.getResponse().getContentAsString();
                String retryAfter = result.getResponse().getHeader("Retry-After");

                assertThat(retryAfter).isNotBlank();
                assertThat(body).contains("\"code\":\"RATE_LIMITED\"");

                return;
            }
        }

        throw new AssertionError("Expected to eventually receive 429 RATE_LIMITED but did not");
    }
}
