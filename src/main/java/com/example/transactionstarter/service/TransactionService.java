package com.example.transactionstarter.service;

import com.example.transactionstarter.dto.CreateTransactionRequest;
import com.example.transactionstarter.dto.TransactionResponse;
import com.example.transactionstarter.dto.UpdateTransactionStatusRequest;
import com.example.transactionstarter.exception.DuplicateTransactionException;
import com.example.transactionstarter.exception.InvalidStatusTransitionException;
import com.example.transactionstarter.exception.InvalidTransactionException;
import com.example.transactionstarter.exception.TransactionNotFoundException;
import com.example.transactionstarter.model.Transaction;
import com.example.transactionstarter.model.TransactionStatus;
import com.example.transactionstarter.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class TransactionService {

    public static final Set<String> ALLOWED_CURRENCIES = Set.of(
            "USD", "EUR", "GBP", "CAD", "JPY", "INR"
    );

    public static final Set<String> ALLOWED_TRANSACTION_TYPES = Set.of(
            "PAYMENT", "REFUND", "TRANSFER", "DEPOSIT", "WITHDRAWAL"
    );

    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public TransactionResponse createTransaction(CreateTransactionRequest request) {
        validateCreateRequest(request);

        String trimmedTxnId = request.getTransactionId().trim();

        if (transactionRepository.existsById(trimmedTxnId)) {
            throw new DuplicateTransactionException(trimmedTxnId, true);
        }

        Transaction transaction = new Transaction(
                trimmedTxnId,
                request.getCustomerId().trim(),
                request.getAmount(),
                request.getCurrency().trim().toUpperCase(),
                request.getTransactionType().trim().toUpperCase(),
                TransactionStatus.PENDING
        );

        Transaction saved = transactionRepository.save(transaction);
        return TransactionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(String transactionId) {
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new InvalidTransactionException("Transaction ID must not be blank");
        }

        Transaction transaction = transactionRepository.findById(transactionId.trim())
                .orElseThrow(() -> new TransactionNotFoundException(transactionId.trim(), true));

        return TransactionResponse.from(transaction);
    }

    public TransactionResponse updateTransactionStatus(String transactionId, UpdateTransactionStatusRequest request) {
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new InvalidTransactionException("Transaction ID must not be blank");
        }

        if (request == null || request.getStatus() == null || request.getStatus().trim().isEmpty()) {
            throw new InvalidTransactionException("New status must not be blank");
        }

        Transaction transaction = transactionRepository.findById(transactionId.trim())
                .orElseThrow(() -> new TransactionNotFoundException(transactionId.trim(), true));

        TransactionStatus targetStatus;
        try {
            targetStatus = TransactionStatus.fromString(request.getStatus());
        } catch (IllegalArgumentException ex) {
            throw new InvalidStatusTransitionException(ex.getMessage());
        }

        TransactionStatus currentStatus = transaction.getStatus();

        if (currentStatus.isTerminal()) {
            throw new InvalidStatusTransitionException(
                    "Cannot update transaction in terminal status: " + currentStatus
            );
        }

        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new InvalidStatusTransitionException(
                    "Invalid status transition from " + currentStatus + " to " + targetStatus +
                    ". Permitted transitions from PENDING are: COMPLETED, FAILED, CANCELLED"
            );
        }

        transaction.setStatus(targetStatus);
        Transaction updated = transactionRepository.save(transaction);
        return TransactionResponse.from(updated);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByCustomer(String customerId) {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new InvalidTransactionException("Customer ID must not be blank");
        }

        return transactionRepository.findByCustomerId(customerId.trim()).stream()
                .map(TransactionResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getAllTransactions() {
        return transactionRepository.findAll().stream()
                .map(TransactionResponse::from)
                .collect(Collectors.toList());
    }

    private void validateCreateRequest(CreateTransactionRequest request) {
        if (request == null) {
            throw new InvalidTransactionException("Request body must not be null");
        }

        if (request.getTransactionId() == null || request.getTransactionId().trim().isEmpty()) {
            throw new InvalidTransactionException("Transaction ID must not be blank");
        }

        if (request.getCustomerId() == null || request.getCustomerId().trim().isEmpty()) {
            throw new InvalidTransactionException("Customer ID must not be blank");
        }

        if (request.getTransactionId().trim().equalsIgnoreCase(request.getCustomerId().trim())) {
            throw new InvalidTransactionException("Transaction ID and Customer ID must not be identical");
        }

        if (request.getAmount() == null) {
            throw new InvalidTransactionException("Amount must not be null");
        }

        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Amount must be greater than zero");
        }

        if (request.getAmount().scale() > 2 || request.getAmount().stripTrailingZeros().scale() > 2) {
            throw new InvalidTransactionException("Amount must have at most 2 decimal places");
        }

        if (request.getAmount().compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidTransactionException("Amount cannot exceed maximum allowed amount of " + MAX_AMOUNT);
        }

        if (request.getCurrency() == null || request.getCurrency().trim().isEmpty()) {
            throw new InvalidTransactionException("Currency must not be blank");
        }

        String normalizedCurrency = request.getCurrency().trim().toUpperCase();
        if (!ALLOWED_CURRENCIES.contains(normalizedCurrency)) {
            throw new InvalidTransactionException(
                    "Invalid currency: '" + request.getCurrency() + "'. Allowed currencies: " + ALLOWED_CURRENCIES
            );
        }

        if (request.getTransactionType() == null || request.getTransactionType().trim().isEmpty()) {
            throw new InvalidTransactionException("Transaction type must not be blank");
        }

        String normalizedType = request.getTransactionType().trim().toUpperCase();
        if (!ALLOWED_TRANSACTION_TYPES.contains(normalizedType)) {
            throw new InvalidTransactionException(
                    "Invalid transaction type: '" + request.getTransactionType() + "'. Allowed types: " + ALLOWED_TRANSACTION_TYPES
            );
        }

        if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
            String initialStatus = request.getStatus().trim().toUpperCase();
            if (!TransactionStatus.PENDING.name().equals(initialStatus)) {
                throw new InvalidTransactionException(
                        "New transactions must begin with status PENDING. Provided: '" + request.getStatus() + "'"
                );
            }
        }
    }
}
