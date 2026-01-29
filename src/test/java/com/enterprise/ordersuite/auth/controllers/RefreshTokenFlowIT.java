package com.enterprise.ordersuite.auth.controllers;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.dtos.LogoutRequest;
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
class RefreshTokenFlowIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void login_then_refresh_rotates_and_old_token_fails_and_logout_revokes() throws Exception {
        // 1) login (NOTE: you must set valid credentials here)
        var login = new AuthRequest("gtavaresdev+enterpriseordersuitetestrefreshtokentest@gmail.com","Test123!"); // TODO: set email/password for a real seeded user

        var loginRes = mockMvc.perform(post("/auth/login")
                        .with(req -> { req.setRemoteAddr("10.10.10.10"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        String loginBody = loginRes.getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(loginBody).get("refreshToken").asText();

        // 2) refresh once, should succeed and give new refresh token
        var refreshReq = new RefreshRequest(refreshToken);

        var refreshRes = mockMvc.perform(post("/auth/refresh")
                        .with(req -> { req.setRemoteAddr("10.10.10.10"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        String refreshBody = refreshRes.getResponse().getContentAsString();
        String newRefreshToken = objectMapper.readTree(refreshBody).get("refreshToken").asText();

        assertThat(newRefreshToken).isNotEqualTo(refreshToken);

        // 3) reuse old refresh token, must fail with INVALID_REFRESH_TOKEN
        mockMvc.perform(post("/auth/refresh")
                        .with(req -> { req.setRemoteAddr("10.10.10.10"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // 4) refresh with the new token should still work (and returns another rotated token)
        var refreshRes2 = mockMvc.perform(post("/auth/refresh")
                        .with(req -> { req.setRemoteAddr("10.10.10.10"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(newRefreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        String refreshBody2 = refreshRes2.getResponse().getContentAsString();
        String newestRefreshToken = objectMapper.readTree(refreshBody2).get("refreshToken").asText();

        assertThat(newestRefreshToken).isNotEqualTo(newRefreshToken);

        // 5) logout using the latest refresh token, must always return 200
        mockMvc.perform(post("/auth/logout")
                        .with(req -> { req.setRemoteAddr("10.10.10.10"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest(newestRefreshToken))))
                .andExpect(status().isOk());

        // 6) refresh after logout must fail with INVALID_REFRESH_TOKEN (revoked)
        mockMvc.perform(post("/auth/refresh")
                        .with(req -> { req.setRemoteAddr("10.10.10.10"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(newestRefreshToken))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // 7) logout should be idempotent, calling again should still return 200
        mockMvc.perform(post("/auth/logout")
                        .with(req -> { req.setRemoteAddr("10.10.10.10"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest(newestRefreshToken))))
                .andExpect(status().isOk());
    }
}
