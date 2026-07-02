package com.minibank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.minibank.outbox.OutboxEventRepository;
import com.minibank.outbox.OutboxRelay;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OutboxIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxEventRepository outbox;

    @Autowired
    private OutboxRelay relay;

    @Test
    void completedTransfersLandInTheOutboxAndAreRelayed() throws Exception {
        String email = "outbox-" + UUID.randomUUID() + "@test.dev";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","displayName":"Outbox"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        String bearer = "Bearer " + objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email":"%s","password":"password123"}
                                        """.formatted(email)))
                        .andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();

        String from = openAccount(bearer, "Out", "200.00");
        String to = openAccount(bearer, "In", "0.00");

        String transferId = objectMapper.readTree(mockMvc.perform(post("/api/transfers")
                                .header(AUTHORIZATION, bearer)
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"fromAccountId":"%s","toAccountId":"%s","amount":75.00}
                                        """.formatted(from, to)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("id").asText();

        // the event was written in the same transaction as the transfer
        var event = outbox.findAll().stream()
                .filter(e -> e.getAggregateId().equals(UUID.fromString(transferId)))
                .findFirst().orElseThrow();
        assertThat(event.getEventType()).isEqualTo("TransferCompleted");
        assertThat(event.getPayload()).contains(transferId).contains("75.00");
        assertThat(event.getPublishedAt()).isNull();

        // the relay marks it as published
        relay.publishPending();
        var relayed = outbox.findById(event.getId()).orElseThrow();
        assertThat(relayed.getPublishedAt()).isNotNull();
    }

    private String openAccount(String bearer, String holder, String amount) throws Exception {
        String id = objectMapper.readTree(mockMvc.perform(post("/api/accounts")
                                .header(AUTHORIZATION, bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"holderName":"%s","currency":"EUR"}
                                        """.formatted(holder)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("id").asText();
        if (new java.math.BigDecimal(amount).signum() > 0) {
            mockMvc.perform(post("/api/accounts/" + id + "/deposits")
                            .header(AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"amount":%s}
                                    """.formatted(amount)))
                    .andExpect(status().isOk());
        }
        return id;
    }
}
