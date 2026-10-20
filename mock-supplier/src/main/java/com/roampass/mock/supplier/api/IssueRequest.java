package com.roampass.mock.supplier.api;

import com.roampass.mock.supplier.issuance.IssueCommand;

import jakarta.validation.constraints.NotBlank;

public record IssueRequest(@NotBlank String idempotencyKey, @NotBlank String orderNo) {

    IssueCommand toCommand() {
        return new IssueCommand(idempotencyKey, orderNo);
    }
}
