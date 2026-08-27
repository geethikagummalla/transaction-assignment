package com.example.transactionstarter.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateTransactionStatusRequest {

    @NotBlank(message = "Status must not be blank")
    private String status;

    public UpdateTransactionStatusRequest() {
    }

    public UpdateTransactionStatusRequest(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
