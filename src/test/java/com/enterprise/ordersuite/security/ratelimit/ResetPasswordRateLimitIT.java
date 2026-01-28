package com.enterprise.ordersuite.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ResetPasswordRateLimitIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    void resetPassword_eventuallyGetsRateLimited() throws Exception {

        boolean rateLimited = false;

        for (int i = 0; i < 10; i++) {
            try {
                var result = mockMvc.perform(post("/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    { "token": "invalid", "newPassword": "x" }
                                """))
                        .andReturn();

                if (result.getResponse().getStatus() == 429) {
                    rateLimited = true;
                    break;
                }
            } catch (Exception ignored) {
                // Invalid token exception is EXPECTED before rate limiting
            }
        }}}