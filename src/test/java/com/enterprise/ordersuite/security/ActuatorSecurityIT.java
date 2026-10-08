package com.enterprise.ordersuite.security;

import com.enterprise.ordersuite.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class ActuatorSecurityIT {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void health_isPublic() throws Exception {
    mockMvc.perform(get("/actuator/health"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void liveness_isPublic() throws Exception {
    mockMvc.perform(get("/actuator/health/liveness"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void readiness_isPublic() throws Exception {
    mockMvc.perform(get("/actuator/health/readiness"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void info_requiresAuthentication() throws Exception {
    mockMvc.perform(get("/actuator/info"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "STAFF")
  void info_forbiddenForStaff() throws Exception {
    mockMvc.perform(get("/actuator/info"))
      .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void info_forbiddenForOwner() throws Exception {
    mockMvc.perform(get("/actuator/info"))
      .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "PLATFORM_ADMIN")
  void info_accessibleForPlatformAdmin() throws Exception {
    mockMvc.perform(get("/actuator/info"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.app.name").value("enterprise-order-suite"));
  }

  @Test
  void metrics_requiresAuthentication() throws Exception {
    mockMvc.perform(get("/actuator/metrics"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "STAFF")
  void metrics_forbiddenForStaff() throws Exception {
    mockMvc.perform(get("/actuator/metrics"))
      .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void metrics_forbiddenForOwner() throws Exception {
    mockMvc.perform(get("/actuator/metrics"))
      .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "PLATFORM_ADMIN")
  void metrics_accessibleForPlatformAdmin() throws Exception {
    mockMvc.perform(get("/actuator/metrics"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.names").isArray());
  }
}
