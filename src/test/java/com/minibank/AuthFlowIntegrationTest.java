package com.minibank;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void apiRejectsRequestsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void registerLoginAndMeRoundTrip() throws Exception {
        String email = "miguel-" + UUID.randomUUID() + "@test.dev";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","displayName":"Miguel"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));

        String token = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email":"%s","password":"password123"}
                                        """.formatted(email)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.displayName").value("Miguel"))
                        .andReturn().getResponse().getContentAsString())
                .get("token").asText();

        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Miguel"));
    }

    @Test
    void duplicateRegistrationReturns409() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@test.dev";
        String body = """
                {"email":"%s","password":"password123","displayName":"Dup"}
                """.formatted(email);

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        String email = "wrong-" + UUID.randomUUID() + "@test.dev";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","displayName":"W"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"nope-nope"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());
    }
}
