package com.roampass.mock.pg.api;

import com.roampass.mock.pg.payment.ApproveCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ApproveRequest(@NotBlank String idempotencyKey, @NotBlank String orderNo, @Positive long amount,
                             @NotBlank String currency) {

    ApproveCommand toCommand() {
        return new ApproveCommand(idempotencyKey, orderNo, amount, currency);
    }
}
