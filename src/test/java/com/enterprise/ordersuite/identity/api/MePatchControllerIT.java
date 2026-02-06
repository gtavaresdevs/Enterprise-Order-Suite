package com.enterprise.ordersuite.identity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MePatchControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void patchMe_withoutToken_returns401() throws Exception {
        mockMvc.perform(patch("/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "firstName": "John",
                              "lastName": "Doe"
                            }
                            """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchMe_invalidInput_returns400() throws Exception {
        String accessToken = loginAndGetAccessToken();

        mockMvc.perform(patch("/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "firstName": "",
                              "lastName": ""
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void patchMe_validInput_returns200AndUpdatedFields() throws Exception {
        String accessToken = loginAndGetAccessToken();

        mockMvc.perform(patch("/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "firstName": "John",
                              "lastName": "Doe"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.updatedAt").exists())
                // safety checks
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.active").doesNotExist());
    }

    private String loginAndGetAccessToken() throws Exception {
        // Adjust to your seeded test credentials
        String loginJson = """
            {
              "email": "user@test.com",
              "password": "Password123!"
            }
            """;

        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
