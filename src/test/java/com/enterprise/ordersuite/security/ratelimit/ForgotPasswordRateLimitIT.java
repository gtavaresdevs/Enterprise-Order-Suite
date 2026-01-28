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
class ForgotPasswordRateLimitIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    void forgotPassword_eventuallyGetsRateLimited() throws Exception {

        boolean rateLimited = false;

        for (int i = 0; i < 10; i++) {
            var result = mockMvc.perform(post("/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                { "email": "test@test.com" }
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
        }

        assertThat(rateLimited).isTrue();
    }
}
