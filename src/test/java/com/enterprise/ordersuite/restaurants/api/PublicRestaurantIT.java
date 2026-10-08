package com.enterprise.ordersuite.restaurants.api;

import com.enterprise.ordersuite.identity.domain.MembershipRole;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.restaurants.domain.Restaurant;
import com.enterprise.ordersuite.restaurants.persistence.RestaurantRepository;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.enterprise.ordersuite.support.TestUsers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GET /public/r/{slug} (Tenancy & Identity §5.5; acceptance 9). Anonymous; the leak test
// pins the exact field set (LR-4).
@IntegrationTest
@AutoConfigureMockMvc
class PublicRestaurantIT {

  private static final String PASSWORD = "Password123!";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private TestUsers testUsers;

  @Autowired
  private RestaurantRepository restaurantRepository;

  @Test
  void get_anonymous_returnsThePublicFields() throws Exception {
    Restaurant restaurant = restaurantRepository.save(new Restaurant("Cantina Nonna", uniqueSlug(), "Europe/Lisbon"));

    mockMvc.perform(get("/public/r/{slug}", restaurant.getSlug()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.name").value("Cantina Nonna"))
      .andExpect(jsonPath("$.slug").value(restaurant.getSlug()))
      .andExpect(jsonPath("$.timezone").value("Europe/Lisbon"))
      .andExpect(jsonPath("$.currency").value("BRL"));
  }

  // LR-4: exactly the public fields; no restaurant id, no member data, no staff-only field.
  @Test
  void get_leaksNothingButThePublicFields() throws Exception {
    User owner = testUsers.owner(uniqueEmail(), PASSWORD);
    String restaurantId = testUsers.restaurantOf(owner);
    User staff = testUsers.memberOf(restaurantId, uniqueEmail(), PASSWORD, MembershipRole.STAFF);
    String slug = restaurantRepository.findById(restaurantId).orElseThrow().getSlug();

    String body = mockMvc.perform(get("/public/r/{slug}", slug))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();

    JsonNode json = objectMapper.readTree(body);
    List<String> fields = new ArrayList<>();
    json.fieldNames().forEachRemaining(fields::add);
    assertThat(fields).containsExactlyInAnyOrder("name", "slug", "timezone", "currency");
    assertThat(body).doesNotContain(restaurantId, owner.getId(), owner.getEmail(), staff.getId(), staff.getEmail(),
      "storefrontUrl", "createdAt");
  }

  @Test
  void get_unknownSlug_isRestaurantNotFound() throws Exception {
    mockMvc.perform(get("/public/r/{slug}", "no-such-" + UUID.randomUUID()))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_FOUND"));
  }

  // Slugs are lowercase; another spelling is a different, unknown slug.
  @Test
  void get_slugInAnotherCase_isRestaurantNotFound() throws Exception {
    Restaurant restaurant = restaurantRepository.save(new Restaurant("Case", uniqueSlug(), "America/Sao_Paulo"));

    mockMvc.perform(get("/public/r/{slug}", restaurant.getSlug().toUpperCase()))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_FOUND"));
  }

  // Production serves under /api; the permitAll rule must hold there too.
  @Test
  void get_underTheContextPath_isPublic() throws Exception {
    Restaurant restaurant = restaurantRepository.save(new Restaurant("Context", uniqueSlug(), "America/Sao_Paulo"));

    mockMvc.perform(get("/api/public/r/{slug}", restaurant.getSlug()).contextPath("/api"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.slug").value(restaurant.getSlug()));
  }

  // A stale or forged bearer token does not turn a public read into a 401.
  @Test
  void get_withAnInvalidBearerToken_stillWorks() throws Exception {
    Restaurant restaurant = restaurantRepository.save(new Restaurant("Token", uniqueSlug(), "America/Sao_Paulo"));

    mockMvc.perform(get("/public/r/{slug}", restaurant.getSlug()).header("Authorization", "Bearer not-a-jwt"))
      .andExpect(status().isOk());
  }

  // Only GET is public under /public/**.
  @Test
  void post_anonymous_isUnauthorized() throws Exception {
    Restaurant restaurant = restaurantRepository.save(new Restaurant("Post", uniqueSlug(), "America/Sao_Paulo"));

    mockMvc.perform(post("/public/r/{slug}", restaurant.getSlug()))
      .andExpect(status().isUnauthorized());
  }

  private static String uniqueSlug() {
    return "p-" + UUID.randomUUID();
  }

  private static String uniqueEmail() {
    return "public-" + UUID.randomUUID() + "@test.com";
  }
}
