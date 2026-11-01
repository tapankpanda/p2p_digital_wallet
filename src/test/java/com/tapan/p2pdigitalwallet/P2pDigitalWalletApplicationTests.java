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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    private long countTransactionEntries(
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

        long count = 0;

        for (JsonNode entry : content) {
            if (referenceId.equals(entry.path("referenceId").asText())) {
                count++;
            }
        }

        return count;
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

        // Verify that exactly one debit entry exists for the transfer.
        assertEquals(
                1L,
                countTransactionEntries(senderAccount, originalReferenceId),
                "The sender should have exactly one debit entry"
        );

        // Verify that exactly one credit entry exists for the transfer.
        assertEquals(
                1L,
                countTransactionEntries(receiverAccount, originalReferenceId),
                "The receiver should have exactly one credit entry"
        );

        // Verify the transaction types.
        JsonNode debitEntry = findTransactionEntry(
                senderAccount,
                originalReferenceId
        );

        JsonNode creditEntry = findTransactionEntry(
                receiverAccount,
                originalReferenceId
        );

        assertEquals("DEBIT", debitEntry.path("type").asText());
        assertEquals("CREDIT", creditEntry.path("type").asText());
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

    @Test
    void transactionHistoryShouldReturnStablePaginationMetadata()
            throws Exception {

        // Arrange: create a wallet with initial funding.
        String accountNumber = createWallet(
                "Pagination Test User",
                "pagination.test@example.com",
                "1000.00"
        );

        // Act and assert: retrieve the first transaction page.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}/transactions",
                                accountNumber)
                                .param("page", "0")
                                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.numberOfElements").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.empty").value(false));
    }

    @Test
    void walletStatusShouldSupportFreezeAndReactivation()
            throws Exception {

        // Arrange: create a wallet.
        String accountNumber = createWallet(
                "Lifecycle Test User",
                "lifecycle.status@example.com",
                "0.00"
        );

        // Act: freeze the wallet.
        mockMvc.perform(
                        put("/api/wallet/{accountNumber}/status",
                                accountNumber)
                                .param("status", "FROZEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        // Assert: verify that the frozen status was persisted.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}", accountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        // Act: reactivate the frozen wallet.
        mockMvc.perform(
                        put("/api/wallet/{accountNumber}/status",
                                accountNumber)
                                .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Assert: verify that the active status was persisted.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}", accountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void walletWithZeroBalanceShouldBeClosedSuccessfully()
            throws Exception {

        // Arrange: create a wallet with zero balance.
        String accountNumber = createWallet(
                "Zero Balance Closure User",
                "zero.closure@example.com",
                "0.00"
        );

        // Act: close the wallet.
        mockMvc.perform(
                        patch("/api/wallet/{accountNumber}/close",
                                accountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        // Assert: verify that the CLOSED status was persisted.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}", accountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void walletWithNonZeroBalanceShouldNotBeClosed()
            throws Exception {

        // Arrange: create a wallet with a positive balance.
        String accountNumber = createWallet(
                "Non Zero Closure User",
                "nonzero.closure@example.com",
                "500.00"
        );

        // Act: attempt to close the wallet.
        mockMvc.perform(
                        patch("/api/wallet/{accountNumber}/close",
                                accountNumber))
                .andExpect(status().isConflict());

        // Assert: verify that the wallet remains ACTIVE.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}", accountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Assert: verify that its balance remains unchanged.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}/balance",
                                accountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500.0));
    }

    @Test
    void closedWalletShouldNotBeReactivated()
            throws Exception {

        // Arrange: create and close a zero-balance wallet.
        String accountNumber = createWallet(
                "Closed Wallet User",
                "closed.reactivation@example.com",
                "0.00"
        );

        mockMvc.perform(
                        patch("/api/wallet/{accountNumber}/close",
                                accountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        // Act: attempt to reactivate the closed wallet.
        mockMvc.perform(
                        put("/api/wallet/{accountNumber}/status",
                                accountNumber)
                                .param("status", "ACTIVE"))
                .andExpect(status().isConflict());

        // Assert: the wallet must remain CLOSED.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}", accountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void transferToClosedWalletShouldBeRejected()
            throws Exception {

        // Arrange: create a funded sender.
        String senderAccount = createWallet(
                "Closed Wallet Transfer Sender",
                "closed.transfer.sender@example.com",
                "500.00"
        );

        // Arrange: create and close the receiver.
        String receiverAccount = createWallet(
                "Closed Wallet Receiver",
                "closed.transfer.receiver@example.com",
                "0.00"
        );

        mockMvc.perform(
                        patch("/api/wallet/{accountNumber}/close",
                                receiverAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        String transferRequest = """
        {
            "fromAccountNumber": "%s",
            "toAccountNumber": "%s",
            "amount": 100.00,
            "description": "Transfer to closed wallet test"
        }
        """.formatted(senderAccount, receiverAccount);

        // Act: attempt to transfer money to the closed wallet.
        mockMvc.perform(
                        post("/api/wallet/transfer")
                                .header(
                                        "Idempotency-Key",
                                        "closed-receiver-test-001"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(transferRequest))
                .andExpect(status().isConflict());

        // Assert: neither wallet's balance should change.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}/balance",
                                senderAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500.0));

        mockMvc.perform(
                        get("/api/wallet/{accountNumber}/balance",
                                receiverAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.0));

        // Assert: the receiver must remain CLOSED.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}", receiverAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void concurrentTransfersShouldNotOverspendSenderWallet()
            throws Exception {

        // Arrange: create one sender with 100 and two empty receivers.
        String senderAccount = createWallet(
                "Concurrent Sender",
                "concurrent.sender@example.com",
                "100.00"
        );

        String receiverOne = createWallet(
                "Concurrent Receiver One",
                "concurrent.receiver.one@example.com",
                "0.00"
        );

        String receiverTwo = createWallet(
                "Concurrent Receiver Two",
                "concurrent.receiver.two@example.com",
                "0.00"
        );

        String requestOne = """
        {
            "fromAccountNumber": "%s",
            "toAccountNumber": "%s",
            "amount": 80.00,
            "description": "First concurrent transfer"
        }
        """.formatted(senderAccount, receiverOne);

        String requestTwo = """
        {
            "fromAccountNumber": "%s",
            "toAccountNumber": "%s",
            "amount": 80.00,
            "description": "Second concurrent transfer"
        }
        """.formatted(senderAccount, receiverTwo);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {

            // Arrange: prepare the first concurrent request.
            Future<MvcResult> firstTransfer = executor.submit(() -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "First transfer did not receive the start signal"
                    );
                }

                return mockMvc.perform(
                                post("/api/wallet/transfer")
                                        .header(
                                                "Idempotency-Key",
                                                "concurrent-transfer-001"
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(requestOne))
                        .andReturn();
            });

            // Arrange: prepare the second concurrent request.
            Future<MvcResult> secondTransfer = executor.submit(() -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Second transfer did not receive the start signal"
                    );
                }

                return mockMvc.perform(
                                post("/api/wallet/transfer")
                                        .header(
                                                "Idempotency-Key",
                                                "concurrent-transfer-002"
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(requestTwo))
                        .andReturn();
            });

            // Wait until both requests are ready.
            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError(
                        "Both transfer requests were not ready"
                );
            }

            // Release both requests at approximately the same time.
            start.countDown();

            // Collect both responses.
            int firstStatus = firstTransfer
                    .get(20, TimeUnit.SECONDS)
                    .getResponse()
                    .getStatus();

            int secondStatus = secondTransfer
                    .get(20, TimeUnit.SECONDS)
                    .getResponse()
                    .getStatus();

            // Exactly one transfer should succeed.
            assertTrue(
                    (firstStatus == 200 && secondStatus == 409)
                            || (firstStatus == 409 && secondStatus == 200),
                    () -> "Expected one successful transfer and one conflict, "
                            + "but received HTTP statuses: "
                            + firstStatus + " and " + secondStatus
            );

            // Assert: the sender must have exactly 20 remaining.
            MvcResult senderBalanceResult = mockMvc.perform(
                            get("/api/wallet/{accountNumber}/balance",
                                    senderAccount))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode senderBalance = jsonMapper.readTree(
                    senderBalanceResult.getResponse().getContentAsString()
            ).path("balance");

            assertMoneyEquals("20.00", senderBalance);

            // Retrieve both receiver balances.
            MvcResult receiverOneResult = mockMvc.perform(
                            get("/api/wallet/{accountNumber}/balance",
                                    receiverOne))
                    .andExpect(status().isOk())
                    .andReturn();

            MvcResult receiverTwoResult = mockMvc.perform(
                            get("/api/wallet/{accountNumber}/balance",
                                    receiverTwo))
                    .andExpect(status().isOk())
                    .andReturn();

            BigDecimal receiverOneBalance = jsonMapper.readTree(
                    receiverOneResult.getResponse().getContentAsString()
            ).path("balance").decimalValue();

            BigDecimal receiverTwoBalance = jsonMapper.readTree(
                    receiverTwoResult.getResponse().getContentAsString()
            ).path("balance").decimalValue();

            // Exactly one receiver should receive 80.
            boolean firstReceivedMoney =
                    receiverOneBalance.compareTo(new BigDecimal("80.00")) == 0;

            boolean secondReceivedMoney =
                    receiverTwoBalance.compareTo(new BigDecimal("80.00")) == 0;

            assertTrue(
                    firstReceivedMoney ^ secondReceivedMoney,
                    "Exactly one receiver should receive 80.00"
            );

            // Verify that the total money received is exactly 80.
            assertEquals(
                    0,
                    new BigDecimal("80.00").compareTo(
                            receiverOneBalance.add(receiverTwoBalance)
                    )
            );

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void frozenWalletShouldNotBeAllowedToSendMoney()
            throws Exception {

        // Arrange: create a funded sender and an empty receiver.
        String senderAccount = createWallet(
                "Frozen Sender",
                "frozen.sender@example.com",
                "500.00"
        );

        String receiverAccount = createWallet(
                "Frozen Receiver",
                "frozen.receiver@example.com",
                "0.00"
        );

        // Freeze the sender wallet.
        mockMvc.perform(
                        put("/api/wallet/{accountNumber}/status",
                                senderAccount)
                                .param("status", "FROZEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        String transferRequest = """
    {
        "fromAccountNumber": "%s",
        "toAccountNumber": "%s",
        "amount": 100.00,
        "description": "Transfer from frozen wallet test"
    }
    """.formatted(senderAccount, receiverAccount);

        // Act: attempt to transfer money from the frozen wallet.
        mockMvc.perform(
                        post("/api/wallet/transfer")
                                .header(
                                        "Idempotency-Key",
                                        "frozen-sender-test-001"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(transferRequest))
                .andExpect(status().isConflict());

        // Assert: sender balance must remain unchanged.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}/balance",
                                senderAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500.0));

        // Assert: receiver must not receive any money.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}/balance",
                                receiverAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.0));

        // Assert: sender must remain frozen.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}", senderAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
    }

    @Test
    void frozenWalletShouldNotBeAllowedToReceiveMoney()
            throws Exception {

        // Arrange: create an active sender with sufficient balance.
        String senderAccount = createWallet(
                "Active Transfer Sender",
                "active.transfer.sender@example.com",
                "500.00"
        );

        // Arrange: create an empty receiver.
        String receiverAccount = createWallet(
                "Frozen Transfer Receiver",
                "frozen.transfer.receiver@example.com",
                "0.00"
        );

        // Freeze the receiver wallet.
        mockMvc.perform(
                        put("/api/wallet/{accountNumber}/status",
                                receiverAccount)
                                .param("status", "FROZEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        String transferRequest = """
    {
        "fromAccountNumber": "%s",
        "toAccountNumber": "%s",
        "amount": 100.00,
        "description": "Transfer to frozen wallet test"
    }
    """.formatted(senderAccount, receiverAccount);

        // Act: attempt to transfer money to the frozen wallet.
        mockMvc.perform(
                        post("/api/wallet/transfer")
                                .header(
                                        "Idempotency-Key",
                                        "frozen-receiver-test-001"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(transferRequest))
                .andExpect(status().isConflict());

        // Assert: sender balance must remain unchanged.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}/balance",
                                senderAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500.0));

        // Assert: receiver balance must remain unchanged.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}/balance",
                                receiverAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.0));

        // Assert: receiver must remain frozen.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}", receiverAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
    }

    @Test
    void invalidWalletStatusShouldBeRejected()
            throws Exception {

        // Arrange: create a wallet.
        String accountNumber = createWallet(
                "Invalid Status Test User",
                "invalid.status@example.com",
                "500.00"
        );

        // Act: attempt to set an unsupported wallet status.
        mockMvc.perform(
                        put("/api/wallet/{accountNumber}/status",
                                accountNumber)
                                .param("status", "BLOCKED"))
                .andExpect(status().isBadRequest());

        // Assert: wallet status should remain ACTIVE.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}", accountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Assert: wallet balance should remain unchanged.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}/balance",
                                accountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500.0));
    }


    @Test
    void getWalletShouldReturnWalletDetails() throws Exception {

        // Arrange: create a wallet with known details.
        String accountNumber = createWallet(
                "Wallet Retrieval User",
                "wallet.retrieval@example.com",
                "750.50"
        );

        // Act and assert: retrieve the wallet and verify its details.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}", accountNumber))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.accountNumber")
                        .value(accountNumber))
                .andExpect(jsonPath("$.fullName")
                        .value("Wallet Retrieval User"))
                .andExpect(jsonPath("$.email")
                        .value("wallet.retrieval@example.com"))
                .andExpect(jsonPath("$.balance")
                        .value(750.50))
                .andExpect(jsonPath("$.status")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void getWalletShouldReturnNotFoundForInvalidAccountNumber()
            throws Exception {

        // Act and assert: request a wallet that does not exist.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}", "999999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getWalletBalanceShouldReturnCorrectBalance()
            throws Exception {

        // Arrange: create a wallet with a known balance.
        String accountNumber = createWallet(
                "Balance Retrieval User",
                "balance.retrieval@example.com",
                "925.75"
        );

        // Act and assert: retrieve and verify the wallet balance.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}/balance",
                                accountNumber))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.balance").value(925.75));
    }

    @Test
    void getWalletBalanceShouldReturnNotFoundForInvalidAccountNumber()
            throws Exception {

        // Act and assert: request the balance of a wallet that does not exist.
        mockMvc.perform(
                        get("/api/wallet/{accountNumber}/balance",
                                "999999999999"))
                .andExpect(status().isNotFound());
    }

}
