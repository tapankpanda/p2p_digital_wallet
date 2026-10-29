package com.tapan.p2pdigitalwallet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class P2pDigitalWalletApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    private String createWallet(
            String fullName,
            String email,
            String initialBalance
    ) throws Exception {

        String requestBody = """
            {
                "fullName": "%s",
                "email": "%s",
                "initialBalance": %s
            }
            """.formatted(fullName, email, initialBalance);

        MvcResult result = mockMvc.perform(post("/api/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        return jsonMapper
                .readTree(result.getResponse().getContentAsString())
                .get("accountNumber")
                .asText();
    }

    private JsonNode findTransactionEntry(
            String accountNumber,
            String referenceId
    ) throws Exception {

        MvcResult result = mockMvc.perform(
                        get("/api/wallet/{accountNumber}/transactions",
                                accountNumber)
                                .param("page", "0")
                                .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = jsonMapper
                .readTree(result.getResponse().getContentAsString())
                .path("content");

        for (JsonNode entry : content) {
            if (referenceId.equals(entry.path("referenceId").asText())) {
                return entry;
            }
        }

        throw new AssertionError(
                "No transaction found for reference ID: " + referenceId
        );
    }

    private void assertMoneyEquals(String expected, JsonNode actual) {
        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual.decimalValue()),
                () -> "Expected monetary value " + expected + " but was " + actual
        );
    }

    @Test
    void createWalletShouldReturnCreatedWalletDetails() throws Exception {

        String requestBody = """
                {
                    "fullName": "Integration Test User",
                    "email": "integration.test@example.com",
                    "initialBalance": 1000.00
                }
                """;

        mockMvc.perform(post("/api/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.accountNumber")
                        .value(matchesPattern("^[1-9][0-9]{11}$")))
                .andExpect(jsonPath("$.fullName")
                        .value("Integration Test User"))
                .andExpect(jsonPath("$.email")
                        .value("integration.test@example.com"))
                .andExpect(jsonPath("$.balance")
                        .value(1000.0))
                .andExpect(jsonPath("$.status")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }


    @Test
    void createWalletShouldRejectNegativeInitialBalance() throws Exception {

        String requestBody = """
            {
                "fullName": "Invalid Balance User",
                "email": "negative.balance@example.com",
                "initialBalance": -100.00
            }
            """;

        mockMvc.perform(post("/api/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferShouldUpdateBothWalletBalances() throws Exception {

        // Arrange: create two wallets with known starting balances.
        String senderAccount = createWallet(
                "Transfer Sender",
                "transfer.sender@example.com",
                "1000.00"
        );

        String receiverAccount = createWallet(
                "Transfer Receiver",
                "transfer.receiver@example.com",
                "500.00"
        );

        String transferRequest = """
            {
                "fromAccountNumber": "%s",
                "toAccountNumber": "%s",
                "amount": 200.00,
                "description": "Integration test transfer"
            }
            """.formatted(senderAccount, receiverAccount);

        // Act: transfer 200 from the sender to the receiver.
        mockMvc.perform(post("/api/wallet/transfer")
                        .header("Idempotency-Key", "integration-transfer-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.amount").value(200.0))
                .andExpect(jsonPath("$.referenceId").isNotEmpty());

        // Assert: the sender's balance is now 800.
        mockMvc.perform(get("/api/wallet/{accountNumber}/balance",
                        senderAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(800.0));

        // Assert: the receiver's balance is now 700.
        mockMvc.perform(get("/api/wallet/{accountNumber}/balance",
                        receiverAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(700.0));
    }

    @Test
    void repeatingTransferWithSameKeyShouldNotTransferMoneyTwice()
            throws Exception {

        // Arrange: create two wallets.
        String senderAccount = createWallet(
                "Idempotency Sender",
                "idempotency.sender@example.com",
                "1000.00"
        );

        String receiverAccount = createWallet(
                "Idempotency Receiver",
                "idempotency.receiver@example.com",
                "500.00"
        );

        String transferRequest = """
            {
                "fromAccountNumber": "%s",
                "toAccountNumber": "%s",
                "amount": 200.00,
                "description": "Idempotency integration test"
            }
            """.formatted(senderAccount, receiverAccount);

        String idempotencyKey = "idempotency-repeat-test-001";

        // Act: submit the original transfer.
        MvcResult firstResult = mockMvc.perform(
                        post("/api/wallet/transfer")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(transferRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn();

        String originalReferenceId = jsonMapper
                .readTree(firstResult.getResponse().getContentAsString())
                .get("referenceId")
                .asText();

        // Act: retry the identical request with the same key.
        mockMvc.perform(post("/api/wallet/transfer")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.referenceId")
                        .value(originalReferenceId));

        // Assert: the transfer happened only once.
        mockMvc.perform(get("/api/wallet/{accountNumber}/balance",
                        senderAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(800.0));

        mockMvc.perform(get("/api/wallet/{accountNumber}/balance",
                        receiverAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(700.0));
    }

    @Test
    void transferWithInsufficientFundsShouldNotChangeBalances()
            throws Exception {

        // Arrange: create wallets with known balances.
        String senderAccount = createWallet(
                "Insufficient Funds Sender",
                "insufficient.sender@example.com",
                "100.00"
        );

        String receiverAccount = createWallet(
                "Insufficient Funds Receiver",
                "insufficient.receiver@example.com",
                "50.00"
        );

        String transferRequest = """
            {
                "fromAccountNumber": "%s",
                "toAccountNumber": "%s",
                "amount": 200.00,
                "description": "Insufficient funds test"
            }
            """.formatted(senderAccount, receiverAccount);

        // Act: attempt to transfer more money than the sender has.
        mockMvc.perform(post("/api/wallet/transfer")
                        .header(
                                "Idempotency-Key",
                                "insufficient-funds-test-001"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest))
                .andExpect(status().isConflict());

        // Assert: neither wallet's balance changed.
        mockMvc.perform(get("/api/wallet/{accountNumber}/balance",
                        senderAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.0));

        mockMvc.perform(get("/api/wallet/{accountNumber}/balance",
                        receiverAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50.0));
    }

    @Test
    void reusingIdempotencyKeyWithDifferentRequestShouldReturnConflict()
            throws Exception {

        // Arrange: create sender and receiver wallets.
        String senderAccount = createWallet(
                "Changed Request Sender",
                "changed.request.sender@example.com",
                "1000.00"
        );

        String receiverAccount = createWallet(
                "Changed Request Receiver",
                "changed.request.receiver@example.com",
                "500.00"
        );

        String idempotencyKey = "changed-request-test-001";

        String originalRequest = """
            {
                "fromAccountNumber": "%s",
                "toAccountNumber": "%s",
                "amount": 200.00,
                "description": "Original transfer"
            }
            """.formatted(senderAccount, receiverAccount);

        String changedRequest = """
            {
                "fromAccountNumber": "%s",
                "toAccountNumber": "%s",
                "amount": 100.00,
                "description": "Changed transfer"
            }
            """.formatted(senderAccount, receiverAccount);

        // Act: submit the original request.
        mockMvc.perform(post("/api/wallet/transfer")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(originalRequest))
                .andExpect(status().isOk());

        // Act: reuse the key with a different amount and description.
        mockMvc.perform(post("/api/wallet/transfer")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changedRequest))
                .andExpect(status().isConflict());

        // Assert: only the original transfer affected the balances.
        mockMvc.perform(get("/api/wallet/{accountNumber}/balance",
                        senderAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(800.0));

        mockMvc.perform(get("/api/wallet/{accountNumber}/balance",
                        receiverAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(700.0));
    }

    @Test
    void successfulTransferShouldCreateMatchingDebitAndCreditEntries()
            throws Exception {

        // Arrange: create two wallets.
        String senderAccount = createWallet(
                "Ledger Sender",
                "ledger.sender@example.com",
                "1000.00"
        );

        String receiverAccount = createWallet(
                "Ledger Receiver",
                "ledger.receiver@example.com",
                "500.00"
        );

        String transferRequest = """
            {
                "fromAccountNumber": "%s",
                "toAccountNumber": "%s",
                "amount": 200.00,
                "description": "Ledger integration test"
            }
            """.formatted(senderAccount, receiverAccount);

        // Act: perform the transfer.
        MvcResult result = mockMvc.perform(
                        post("/api/wallet/transfer")
                                .header("Idempotency-Key", "ledger-test-001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(transferRequest))
                .andExpect(status().isOk())
                .andReturn();

        String referenceId = jsonMapper
                .readTree(result.getResponse().getContentAsString())
                .path("referenceId")
                .asText();

        // Retrieve both entries from persisted transaction history.
        JsonNode debit = findTransactionEntry(senderAccount, referenceId);
        JsonNode credit = findTransactionEntry(receiverAccount, referenceId);

        // Verify that both entries belong to the same transfer.
        assertEquals(referenceId, debit.path("referenceId").asText());
        assertEquals(referenceId, credit.path("referenceId").asText());

        // Verify the sender's debit entry.
        assertEquals("DEBIT", debit.path("type").asText());
        assertMoneyEquals("200.00", debit.path("amount"));
        assertMoneyEquals("1000.00", debit.path("balanceBefore"));
        assertMoneyEquals("800.00", debit.path("balanceAfter"));

        // Verify the receiver's credit entry.
        assertEquals("CREDIT", credit.path("type").asText());
        assertMoneyEquals("200.00", credit.path("amount"));
        assertMoneyEquals("500.00", credit.path("balanceBefore"));
        assertMoneyEquals("700.00", credit.path("balanceAfter"));
    }

}
