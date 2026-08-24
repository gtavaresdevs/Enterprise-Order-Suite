package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.dtos.RegisterRequest;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class MeControllerIT {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void me_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/me"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void me_withValidAccessToken_returns200_andDoesNotLeakSensitiveFields() throws Exception {
    String email = "me-" + UUID.randomUUID() + "@test.com";
    String password = "Password123!";

    RegisterRequest registerRequest = new RegisterRequest();
    registerRequest.setFirstName("Me");
    registerRequest.setLastName("Test");
    registerRequest.setEmail(email);
    registerRequest.setPassword(password);

    mockMvc.perform(post("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(registerRequest)))
      .andExpect(status().isOk());

    AuthRequest loginRequest = new AuthRequest(email, password);

    String loginResponse = mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(loginRequest)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accessToken").exists())
      .andReturn()
      .getResponse()
      .getContentAsString();

    String accessToken = objectMapper.readTree(loginResponse)
      .get("accessToken")
      .asText();

    mockMvc.perform(get("/me")
        .header("Authorization", "Bearer " + accessToken))
      .andExpect(status().isOk())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$.email").value(email))
      .andExpect(jsonPath("$.role").exists())
      .andExpect(jsonPath("$.password").doesNotExist())
      .andExpect(jsonPath("$.active").doesNotExist());
  }
}
