package com.example.transactionstarter.model;

import java.util.Set;

public enum TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED,
    CANCELLED;

    private static final Set<TransactionStatus> TERMINAL_STATUSES = Set.of(
            COMPLETED,
            FAILED,
            CANCELLED
    );

    public boolean isTerminal() {
        return TERMINAL_STATUSES.contains(this);
    }

    public boolean canTransitionTo(TransactionStatus target) {
        if (target == null) {
            return false;
        }
        if (this == PENDING) {
            return target == COMPLETED || target == FAILED || target == CANCELLED;
        }
        return false;
    }

    public static TransactionStatus fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Status value cannot be blank");
        }
        try {
            return TransactionStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid transaction status: '" + value + "'. Allowed statuses: PENDING, COMPLETED, FAILED, CANCELLED");
        }
    }
}
