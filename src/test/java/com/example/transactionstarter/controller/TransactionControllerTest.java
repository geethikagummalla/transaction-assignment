package com.example.transactionstarter.controller;

import com.example.transactionstarter.dto.CreateTransactionRequest;
import com.example.transactionstarter.dto.UpdateTransactionStatusRequest;
import com.example.transactionstarter.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
    }

    @Test
    @DisplayName("1. Successful transaction creation should return 201 and initial status PENDING")
    void createTransaction_Successful() throws Exception {
        CreateTransactionRequest request = new CreateTransactionRequest(
                "TXN-1001",
                "CUST-501",
                new BigDecimal("150.75"),
                "USD",
                "PAYMENT"
        );

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId", is("TXN-1001")))
                .andExpect(jsonPath("$.customerId", is("CUST-501")))
                .andExpect(jsonPath("$.amount", is(150.75)))
                .andExpect(jsonPath("$.currency", is("USD")))
                .andExpect(jsonPath("$.transactionType", is("PAYMENT")))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    @DisplayName("2. Creation with blank fields should return 400 Bad Request")
    void createTransaction_BlankFields_Rejected() throws Exception {
        CreateTransactionRequest request = new CreateTransactionRequest(
                "",
                "CUST-501",
                new BigDecimal("100.00"),
                "USD",
                "PAYMENT"
        );

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")));
    }

    @Test
    @DisplayName("3. Creation with negative or zero amount should return 400 Bad Request")
    void createTransaction_NegativeAmount_Rejected() throws Exception {
        CreateTransactionRequest request = new CreateTransactionRequest(
                "TXN-1002",
                "CUST-501",
                new BigDecimal("-50.00"),
                "USD",
                "PAYMENT"
        );

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("4. Creation with amount having more than 2 decimal places should return 400 Bad Request")
    void createTransaction_MoreThanTwoDecimals_Rejected() throws Exception {
        CreateTransactionRequest request = new CreateTransactionRequest(
                "TXN-1003",
                "CUST-501",
                new BigDecimal("100.555"),
                "USD",
                "PAYMENT"
        );

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("5. Creation with invalid currency should return 400 Bad Request")
    void createTransaction_InvalidCurrency_Rejected() throws Exception {
        CreateTransactionRequest request = new CreateTransactionRequest(
                "TXN-1004",
                "CUST-501",
                new BigDecimal("100.00"),
                "INVALID_CURR",
                "PAYMENT"
        );

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("6. Creation with non-PENDING initial status should return 400 Bad Request")
    void createTransaction_NonPendingInitialStatus_Rejected() throws Exception {
        CreateTransactionRequest request = new CreateTransactionRequest(
                "TXN-1005",
                "CUST-501",
                new BigDecimal("100.00"),
                "USD",
                "PAYMENT",
                "COMPLETED"
        );

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("7. Duplicate transaction ID should return 409 Conflict")
    void createTransaction_DuplicateId_RejectedWith409() throws Exception {
        CreateTransactionRequest request = new CreateTransactionRequest(
                "TXN-DUP-01",
                "CUST-501",
                new BigDecimal("200.00"),
                "EUR",
                "PAYMENT"
        );

        // First creation succeeds
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Duplicate creation fails with 409
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("TXN-DUP-01")));
    }

    @Test
    @DisplayName("8. Get transaction by ID should return 200 OK when found")
    void getTransactionById_Found() throws Exception {
        CreateTransactionRequest createRequest = new CreateTransactionRequest(
                "TXN-GET-01",
                "CUST-601",
                new BigDecimal("99.99"),
                "GBP",
                "TRANSFER"
        );

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/transactions/TXN-GET-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId", is("TXN-GET-01")))
                .andExpect(jsonPath("$.customerId", is("CUST-601")))
                .andExpect(jsonPath("$.amount", is(99.99)))
                .andExpect(jsonPath("$.currency", is("GBP")))
                .andExpect(jsonPath("$.transactionType", is("TRANSFER")))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    @DisplayName("9. Get non-existent transaction should return 404 Not Found")
    void getTransactionById_NotFound() throws Exception {
        mockMvc.perform(get("/api/transactions/NON_EXISTENT_TXN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    @Test
    @DisplayName("10. Status transition from PENDING to COMPLETED should succeed with 200 OK")
    void updateStatus_PendingToCompleted_Success() throws Exception {
        CreateTransactionRequest createRequest = new CreateTransactionRequest(
                "TXN-ST-01",
                "CUST-701",
                new BigDecimal("500.00"),
                "USD",
                "PAYMENT"
        );
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        UpdateTransactionStatusRequest updateRequest = new UpdateTransactionStatusRequest("COMPLETED");

        mockMvc.perform(patch("/api/transactions/TXN-ST-01/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId", is("TXN-ST-01")))
                .andExpect(jsonPath("$.status", is("COMPLETED")));
    }

    @Test
    @DisplayName("11. Status transition from PENDING to FAILED and CANCELLED should succeed with 200 OK")
    void updateStatus_PendingToFailedAndCancelled_Success() throws Exception {
        // Test FAILED
        CreateTransactionRequest req1 = new CreateTransactionRequest(
                "TXN-ST-02", "CUST-701", new BigDecimal("50.00"), "USD", "PAYMENT");
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/transactions/TXN-ST-02/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTransactionStatusRequest("FAILED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")));

        // Test CANCELLED
        CreateTransactionRequest req2 = new CreateTransactionRequest(
                "TXN-ST-03", "CUST-701", new BigDecimal("75.00"), "CAD", "REFUND");
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/transactions/TXN-ST-03/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTransactionStatusRequest("CANCELLED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    @DisplayName("12. Updating terminal status (COMPLETED -> CANCELLED) should return 400 Bad Request")
    void updateStatus_TerminalStatus_CannotChange() throws Exception {
        CreateTransactionRequest createRequest = new CreateTransactionRequest(
                "TXN-TERM-01",
                "CUST-801",
                new BigDecimal("300.00"),
                "USD",
                "PAYMENT"
        );
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        // First transition: PENDING -> COMPLETED
        mockMvc.perform(patch("/api/transactions/TXN-TERM-01/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTransactionStatusRequest("COMPLETED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        // Second transition: COMPLETED -> CANCELLED (Terminal status change forbidden)
        mockMvc.perform(patch("/api/transactions/TXN-TERM-01/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTransactionStatusRequest("CANCELLED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("terminal status")));
    }

    @Test
    @DisplayName("13. Status update on non-existent transaction should return 404 Not Found")
    void updateStatus_NotFound() throws Exception {
        mockMvc.perform(patch("/api/transactions/NON_EXISTENT_TXN/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTransactionStatusRequest("COMPLETED"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("14. Get all transactions for a customer ID should return customer records")
    void getTransactionsByCustomer_ReturnsMatchingTransactions() throws Exception {
        CreateTransactionRequest req1 = new CreateTransactionRequest(
                "TXN-CUST-1", "CUST-AAA", new BigDecimal("10.00"), "USD", "PAYMENT");
        CreateTransactionRequest req2 = new CreateTransactionRequest(
                "TXN-CUST-2", "CUST-AAA", new BigDecimal("20.00"), "EUR", "REFUND");
        CreateTransactionRequest req3 = new CreateTransactionRequest(
                "TXN-CUST-3", "CUST-BBB", new BigDecimal("30.00"), "GBP", "TRANSFER");

        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req1))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req2))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req3))).andExpect(status().isCreated());

        mockMvc.perform(get("/api/transactions").param("customerId", "CUST-AAA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].transactionId", containsInAnyOrder("TXN-CUST-1", "TXN-CUST-2")));

        mockMvc.perform(get("/api/transactions/customer/CUST-AAA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].transactionId", containsInAnyOrder("TXN-CUST-1", "TXN-CUST-2")));
    }

    @Test
    @DisplayName("15. Existing sample endpoint GET /api/sample remains intact and working")
    void sampleEndpoint_RemainsWorking() throws Exception {
        mockMvc.perform(get("/api/sample"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Starter project is running")));
    }
}
