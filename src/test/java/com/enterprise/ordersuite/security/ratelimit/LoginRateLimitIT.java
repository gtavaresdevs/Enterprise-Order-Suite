package com.enterprise.ordersuite.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LoginRateLimitIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    void login_eventuallyGetsRateLimited() throws Exception {

        boolean rateLimited = false;

        for (int i = 0; i < 10; i++) {
            try {
                var result = mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    { "email": "x@test.com", "password": "wrong" }
                                """)
                                .with(request -> {
                                    request.setRemoteAddr("1.2.3.4");
                                    return request;
                                }))
                        .andReturn();

                if (result.getResponse().getStatus() == 429) {
                    rateLimited = true;
                    break;
                }
            } catch (Exception ignored) {
                // Invalid credentials before rate limiting is expected
            }
        }

        assertThat(rateLimited).isTrue();
    }
}
