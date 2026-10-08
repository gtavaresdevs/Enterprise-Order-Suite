package com.enterprise.ordersuite.api.errors;

import com.enterprise.ordersuite.support.IntegrationTest;
import com.enterprise.ordersuite.support.TestUsers;
import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.common.errors.InvalidInputException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// API conventions §10.2: an unexpected failure is 500 with a generic body, never a query, a
// stack trace or an internal message. Spring matches an advice's handler against an
// exception's causes too, so an IllegalArgumentException buried in a data-access error used
// to be answered as 400 INVALID_INPUT with the HQL in message. Only InvalidInputException,
// thrown on purpose with a message written for the client, is a 400 that echoes its message.
@IntegrationTest
@AutoConfigureMockMvc
@Import(ErrorDisclosureIT.ProbeController.class)
class ErrorDisclosureIT {

  private static final String PASSWORD = "Password123!";
  private static final String QUERY = "select m from Membership m where m.secretColumn = :x";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private TestUsers testUsers;

  @Test
  void illegalArgumentInsideADataAccessError_is500_withoutTheQuery() throws Exception {
    String body = mockMvc.perform(get("/test/errors/wrapped-query").header("Authorization", bearer()))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
      .andReturn().getResponse().getContentAsString();

    assertThat(body).doesNotContain("select", "Membership", "secretColumn");
  }

  @Test
  void bareIllegalArgument_is500_withoutItsMessage() throws Exception {
    String body = mockMvc.perform(get("/test/errors/bare").header("Authorization", bearer()))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
      .andReturn().getResponse().getContentAsString();

    assertThat(body).doesNotContain("internal detail");
  }

  @Test
  void invalidInput_is400_withItsMessage() throws Exception {
    mockMvc.perform(get("/test/errors/invalid-input").header("Authorization", bearer()))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
      .andExpect(jsonPath("$.message").value("sort: unknown field email"));
  }

  private String bearer() throws Exception {
    String email = "errors-" + UUID.randomUUID() + "@test.com";
    testUsers.owner(email, PASSWORD);
    String response = mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AuthRequest(email, PASSWORD))))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();
    return "Bearer " + objectMapper.readTree(response).get("accessToken").asText();
  }

  @RestController
  static class ProbeController {

    // The shape Spring Data produced for a broken sort expression in Build 1 slice 6.
    @GetMapping("/test/errors/wrapped-query")
    void wrappedQuery() {
      throw new InvalidDataAccessApiUsageException(QUERY, new IllegalArgumentException(QUERY));
    }

    @GetMapping("/test/errors/bare")
    void bare() {
      throw new IllegalArgumentException("internal detail");
    }

    @GetMapping("/test/errors/invalid-input")
    void invalidInput() {
      throw new InvalidInputException("sort: unknown field email");
    }
  }
}
