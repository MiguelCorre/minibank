package com.minibank.cucumber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Glue for all banking features. Cucumber creates a fresh instance per
 * scenario, so instance fields give natural scenario isolation.
 */
public class BankingSteps {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final Map<String, String> passwordsByEmail = new HashMap<>();
    private final Map<String, String> accountIdsByAlias = new HashMap<>();
    private final Map<String, String> ownerTokenByAlias = new HashMap<>();

    private String accessToken;
    private String refreshToken;
    private String previousRefreshToken;

    private int lastStatus;
    private JsonNode lastBody;

    private String lastTransferKey;
    private String lastTransferPayload;

    // --- authentication -------------------------------------------------

    @Given("a registered user {string} named {string} with password {string}")
    public void registeredUser(String email, String name, String password) throws Exception {
        exchange(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s","displayName":"%s"}
                        """.formatted(email, password, name)));
        assertThat(lastStatus).isEqualTo(201);
        passwordsByEmail.put(email, password);
    }

    @When("{string} logs in with password {string}")
    public void logsIn(String email, String password) throws Exception {
        exchange(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
        if (lastStatus == 200) {
            accessToken = lastBody.get("accessToken").asText();
            refreshToken = lastBody.get("refreshToken").asText();
        }
    }

    @Given("{string} is logged in")
    public void isLoggedIn(String email) throws Exception {
        logsIn(email, passwordsByEmail.get(email));
        assertThat(lastStatus).isEqualTo(200);
    }

    @Given("an authenticated session")
    public void authenticatedSession() throws Exception {
        String email = "session-" + UUID.randomUUID() + "@example.dev";
        registeredUser(email, "Session User", "session-pass-123");
        isLoggedIn(email);
    }

    @When("{string} fails to log in {int} times")
    public void failsToLogIn(String email, int attempts) throws Exception {
        for (int i = 0; i < attempts; i++) {
            logsIn(email, "definitely-wrong-password");
            assertThat(lastStatus).isEqualTo(401);
        }
    }

    @When("the session is refreshed")
    public void sessionRefreshed() throws Exception {
        previousRefreshToken = refreshToken;
        exchange(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken":"%s"}
                        """.formatted(refreshToken)));
        assertThat(lastStatus).isEqualTo(200);
        accessToken = lastBody.get("accessToken").asText();
        refreshToken = lastBody.get("refreshToken").asText();
    }

    @When("the previous refresh token is used again")
    public void previousRefreshTokenReused() throws Exception {
        exchange(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken":"%s"}
                        """.formatted(previousRefreshToken)));
    }

    @When("the accounts list is requested without a token")
    public void accountsWithoutToken() throws Exception {
        exchange(get("/api/accounts"));
    }

    @Then("the response contains an access token and a refresh token")
    public void responseContainsTokenPair() {
        assertThat(lastStatus).isEqualTo(200);
        assertThat(lastBody.get("accessToken").asText()).isNotBlank();
        assertThat(lastBody.get("refreshToken").asText()).isNotBlank();
    }

    @Then("the authenticated profile shows {string}")
    public void profileShows(String displayName) throws Exception {
        exchange(get("/api/auth/me").header(AUTHORIZATION, bearer()));
        assertThat(lastStatus).isEqualTo(200);
        assertThat(lastBody.get("displayName").asText()).isEqualTo(displayName);
    }

    // --- accounts ---------------------------------------------------------

    @When("an account is opened for {string} in {word}")
    @Given("an account for {string} in {word}")
    public void accountOpened(String alias, String currency) throws Exception {
        exchange(post("/api/accounts")
                .header(AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"holderName":"%s","currency":"%s"}
                        """.formatted(alias, currency)));
        assertThat(lastStatus).isEqualTo(201);
        accountIdsByAlias.put(alias, lastBody.get("id").asText());
        ownerTokenByAlias.put(alias, accessToken);
    }

    @Given("another customer has an account for {string} in {word} with balance {bigdecimal}")
    public void anotherCustomerAccount(String alias, String currency, BigDecimal balance) throws Exception {
        String savedAccess = accessToken;
        String savedRefresh = refreshToken;
        authenticatedSession();
        accountWithBalance(alias, currency, balance);
        accessToken = savedAccess;
        refreshToken = savedRefresh;
    }

    @When("the account of {string} is requested")
    public void accountRequested(String alias) throws Exception {
        exchange(get("/api/accounts/" + accountId(alias)).header(AUTHORIZATION, bearer()));
    }

    @Given("an account for {string} in {word} with balance {bigdecimal}")
    public void accountWithBalance(String alias, String currency, BigDecimal balance) throws Exception {
        accountOpened(alias, currency);
        if (balance.signum() > 0) {
            deposited(balance, alias);
        }
    }

    @When("{bigdecimal} is deposited into the account of {string}")
    public void deposited(BigDecimal amount, String alias) throws Exception {
        exchange(post("/api/accounts/" + accountId(alias) + "/deposits")
                .header(AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount":%s}
                        """.formatted(amount)));
        assertThat(lastStatus).isEqualTo(200);
    }

    @Then("the account of {string} has balance {bigdecimal}")
    public void accountHasBalance(String alias, BigDecimal expected) throws Exception {
        // read as the owner: balances of other customers' accounts are private
        exchange(get("/api/accounts/" + accountId(alias))
                .header(AUTHORIZATION, "Bearer " + ownerTokenByAlias.get(alias)));
        assertThat(lastStatus).isEqualTo(200);
        assertThat(lastBody.get("balance").decimalValue()).isEqualByComparingTo(expected);
    }

    @Then("the accounts list contains {string}")
    public void accountsListContains(String alias) throws Exception {
        exchange(get("/api/accounts").header(AUTHORIZATION, bearer()));
        assertThat(lastStatus).isEqualTo(200);
        assertThat(lastBody.findValuesAsText("holderName")).contains(alias);
    }

    @Then("the accounts list does not contain {string}")
    public void accountsListDoesNotContain(String alias) throws Exception {
        exchange(get("/api/accounts").header(AUTHORIZATION, bearer()));
        assertThat(lastStatus).isEqualTo(200);
        assertThat(lastBody.findValuesAsText("holderName")).doesNotContain(alias);
    }

    @Then("the ledger of {string} shows a {word} of {bigdecimal}")
    public void ledgerShows(String alias, String type, BigDecimal amount) throws Exception {
        exchange(get("/api/accounts/" + accountId(alias) + "/ledger").header(AUTHORIZATION, bearer()));
        assertThat(lastStatus).isEqualTo(200);
        boolean found = false;
        for (JsonNode entry : lastBody) {
            if (entry.get("type").asText().equals(type)
                    && entry.get("amount").decimalValue().compareTo(amount) == 0) {
                found = true;
                break;
            }
        }
        assertThat(found)
                .withFailMessage("No %s of %s in the ledger of %s", type, amount, alias)
                .isTrue();
    }

    // --- transfers ----------------------------------------------------------

    @When("{bigdecimal} is transferred from {string} to {string}")
    public void transferred(BigDecimal amount, String from, String to) throws Exception {
        transferAttempted(amount, from, to);
        assertThat(lastStatus).isEqualTo(201);
    }

    @When("a transfer of {bigdecimal} from {string} to {string} is attempted")
    public void transferAttempted(BigDecimal amount, String from, String to) throws Exception {
        lastTransferKey = UUID.randomUUID().toString();
        lastTransferPayload = """
                {"fromAccountId":"%s","toAccountId":"%s","amount":%s}
                """.formatted(accountId(from), accountId(to), amount);
        submitTransfer();
    }

    @When("the same transfer is submitted again")
    public void sameTransferAgain() throws Exception {
        submitTransfer();
        assertThat(lastStatus).isEqualTo(201);
    }

    private void submitTransfer() throws Exception {
        exchange(post("/api/transfers")
                .header(AUTHORIZATION, bearer())
                .header("Idempotency-Key", lastTransferKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(lastTransferPayload));
    }

    // --- shared assertions ---------------------------------------------------

    @Then("the request fails with status {int}")
    public void requestFailsWith(int status) {
        assertThat(lastStatus).isEqualTo(status);
    }

    // --- plumbing --------------------------------------------------------------

    private void exchange(MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
        lastStatus = response.getStatus();
        String content = response.getContentAsString();
        lastBody = content.isBlank() ? NullNode.getInstance() : objectMapper.readTree(content);
    }

    private String bearer() {
        return "Bearer " + accessToken;
    }

    private String accountId(String alias) {
        String id = accountIdsByAlias.get(alias);
        assertThat(id).withFailMessage("No account opened for alias '%s'", alias).isNotNull();
        return id;
    }
}
