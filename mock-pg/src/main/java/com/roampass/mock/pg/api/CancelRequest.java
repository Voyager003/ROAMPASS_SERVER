package com.roampass.mock.pg.api;

import com.roampass.mock.pg.payment.CancelCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CancelRequest(@NotBlank String pgTid, @NotBlank String cancelKey, @Positive long amount) {

    CancelCommand toCommand() {
        return new CancelCommand(pgTid, cancelKey, amount);
    }
}
