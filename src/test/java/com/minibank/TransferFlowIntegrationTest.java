package com.minibank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureObservability // metrics export is disabled in tests by default
@Import(TestcontainersConfiguration.class)
class TransferFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String bearer;

    @BeforeEach
    void authenticate() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@test.dev";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","displayName":"Test User"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        bearer = "Bearer " + objectMapper.readTree(response).get("accessToken").asText();
    }

    @Test
    void transferMovesMoneyAndWritesLedgerEntries() throws Exception {
        String from = openAccountWithBalance("Alice", "500.00");
        String to = openAccountWithBalance("Bob", "100.00");

        mockMvc.perform(post("/api/transfers")
                        .header(AUTHORIZATION, bearer)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId":"%s","toAccountId":"%s","amount":150.00,"description":"rent"}
                                """.formatted(from, to)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.currency").value("EUR"));

        assertThat(balanceOf(from)).isEqualByComparingTo("350.00");
        assertThat(balanceOf(to)).isEqualByComparingTo("250.00");

        JsonNode fromLedger = getJson("/api/accounts/" + from + "/ledger").get("content");
        assertThat(fromLedger).hasSize(2); // initial deposit credit + transfer debit
        assertThat(fromLedger.get(0).get("type").asText()).isEqualTo("DEBIT");
        assertThat(fromLedger.get(0).get("balanceAfter").decimalValue()).isEqualByComparingTo("350.00");
    }

    @Test
    void ledgerIsPaginated() throws Exception {
        String account = openAccountWithBalance("Paged", "10.00");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/accounts/" + account + "/deposits")
                            .header(AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"amount":5.00}
                                    """))
                    .andExpect(status().isOk());
        }

        JsonNode page = getJson("/api/accounts/" + account + "/ledger?page=0&size=2");
        assertThat(page.get("content")).hasSize(2);
        assertThat(page.get("totalElements").asLong()).isEqualTo(4); // opening deposit + 3
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
    }

    @Test
    void statementIsGeneratedAsCsvAndPdf() throws Exception {
        String from = openAccountWithBalance("Stmt", "300.00");
        String to = openAccountWithBalance("Peer", "0.00");
        mockMvc.perform(post("/api/transfers")
                        .header(AUTHORIZATION, bearer)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId":"%s","toAccountId":"%s","amount":120.00}
                                """.formatted(from, to)))
                .andExpect(status().isCreated());

        String today = java.time.LocalDate.now().toString();
        String csv = mockMvc.perform(get("/api/accounts/" + from + "/statement?from=" + today
                                + "&to=" + today + "&format=csv")
                        .header(AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"))
                .andReturn().getResponse().getContentAsString();
        assertThat(csv).startsWith("timestamp_utc,type,amount,balance_after,transfer_id");
        assertThat(csv).contains("CREDIT,300.00").contains("DEBIT,-120.00,180.00");

        byte[] pdf = mockMvc.perform(get("/api/accounts/" + from + "/statement?from=" + today
                                + "&to=" + today + "&format=pdf")
                        .header(AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void replayingTheSameIdempotencyKeyDoesNotDebitTwice() throws Exception {
        String from = openAccountWithBalance("Carol", "300.00");
        String to = openAccountWithBalance("Dan", "0.00");
        String key = UUID.randomUUID().toString();
        String body = """
                {"fromAccountId":"%s","toAccountId":"%s","amount":50.00}
                """.formatted(from, to);

        String firstId = objectMapper.readTree(mockMvc.perform(post("/api/transfers")
                                .header(AUTHORIZATION, bearer)
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("id").asText();

        String secondId = objectMapper.readTree(mockMvc.perform(post("/api/transfers")
                                .header(AUTHORIZATION, bearer)
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("id").asText();

        assertThat(secondId).isEqualTo(firstId);
        assertThat(balanceOf(from)).isEqualByComparingTo("250.00");
        assertThat(balanceOf(to)).isEqualByComparingTo("50.00");
    }

    @Test
    void transferWithoutEnoughFundsIsRejectedWith422() throws Exception {
        String from = openAccountWithBalance("Eve", "10.00");
        String to = openAccountWithBalance("Frank", "0.00");

        mockMvc.perform(post("/api/transfers")
                        .header(AUTHORIZATION, bearer)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId":"%s","toAccountId":"%s","amount":99.99}
                                """.formatted(from, to)))
                .andExpect(status().isUnprocessableEntity());

        assertThat(balanceOf(from)).isEqualByComparingTo("10.00");
    }

    @Test
    void transferToUnknownAccountReturns404() throws Exception {
        String from = openAccountWithBalance("Grace", "100.00");

        mockMvc.perform(post("/api/transfers")
                        .header(AUTHORIZATION, bearer)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId":"%s","toAccountId":"%s","amount":10.00}
                                """.formatted(from, UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void transferWithoutIdempotencyKeyHeaderReturns400() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .header(AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId":"%s","toAccountId":"%s","amount":10.00}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferMetricsAreExposedForPrometheus() throws Exception {
        String from = openAccountWithBalance("Metric", "50.00");
        String to = openAccountWithBalance("Target", "0.00");
        mockMvc.perform(post("/api/transfers")
                        .header(AUTHORIZATION, bearer)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId":"%s","toAccountId":"%s","amount":10.00}
                                """.formatted(from, to)))
                .andExpect(status().isCreated());

        String scrape = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(scrape).contains("minibank_transfers_completed_total");
        assertThat(scrape).contains("minibank_logins_total");
    }

    @Test
    void accountsListingContainsOpenedAccounts() throws Exception {
        String id = openAccountWithBalance("Ivan", "0.00");

        JsonNode accounts = getJson("/api/accounts");
        assertThat(accounts.isArray()).isTrue();
        assertThat(accounts.findValuesAsText("id")).contains(id);
    }

    @Test
    void transferToSameAccountIsRejectedWith400() throws Exception {
        String account = openAccountWithBalance("Heidi", "100.00");

        mockMvc.perform(post("/api/transfers")
                        .header(AUTHORIZATION, bearer)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId":"%s","toAccountId":"%s","amount":10.00}
                                """.formatted(account, account)))
                .andExpect(status().isBadRequest());
    }

    private String openAccountWithBalance(String holder, String amount) throws Exception {
        String response = mockMvc.perform(post("/api/accounts")
                        .header(AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"holderName":"%s","currency":"EUR"}
                                """.formatted(holder)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(response).get("id").asText();

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

    private java.math.BigDecimal balanceOf(String accountId) throws Exception {
        return getJson("/api/accounts/" + accountId).get("balance").decimalValue();
    }

    private JsonNode getJson(String url) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(url).header(AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
