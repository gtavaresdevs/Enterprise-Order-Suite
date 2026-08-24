package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.dtos.RegisterRequest;
import com.enterprise.ordersuite.identity.api.dto.UpdateMeRequest;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class MePatchControllerIT {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private UserRepository userRepository;

  @Test
  void patchMe_invalidInput_returns400() throws Exception {
    String token = registerAndLoginUser();

    var request = new UpdateMeRequest(" ", " ");

    mockMvc.perform(patch("/me")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isBadRequest());
  }

  @Test
  void patchMe_validInput_returns200AndPersistsUpdatedFields() throws Exception {
    String email = "me-patch-" + UUID.randomUUID() + "@test.com";
    String password = "Password123!";

    String token = registerAndLoginUser(email, password);

    var request = new UpdateMeRequest("UpdatedFirst", "UpdatedLast");

    mockMvc.perform(patch("/me")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.firstName").value("UpdatedFirst"))
      .andExpect(jsonPath("$.lastName").value("UpdatedLast"));

    User updatedUser = userRepository.findByEmailIgnoreCase(email)
      .orElseThrow();

    assertThat(updatedUser.getFirstName())
      .isEqualTo("UpdatedFirst");

    assertThat(updatedUser.getLastName())
      .isEqualTo("UpdatedLast");

    assertThat(updatedUser.getEmail())
      .isEqualTo(email);

    assertThat(updatedUser.getPassword())
      .isNotBlank();
  }

  private String registerAndLoginUser() throws Exception {
    String email = "me-patch-" + UUID.randomUUID() + "@test.com";
    String password = "Password123!";

    return registerAndLoginUser(email, password);
  }

  private String registerAndLoginUser(
    String email,
    String password
  ) throws Exception {

    RegisterRequest registerRequest = new RegisterRequest();
    registerRequest.setFirstName("OriginalFirst");
    registerRequest.setLastName("OriginalLast");
    registerRequest.setEmail(email);
    registerRequest.setPassword(password);

    mockMvc.perform(post("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(registerRequest)))
      .andExpect(status().isOk());

    AuthRequest loginRequest = new AuthRequest(email, password);

    String response = mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(loginRequest)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accessToken").exists())
      .andReturn()
      .getResponse()
      .getContentAsString();

    return objectMapper.readTree(response)
      .get("accessToken")
      .asText();
  }
}
