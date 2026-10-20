package com.roampass.mock.supplier.api;

import com.roampass.mock.supplier.issuance.ProfileState;

import jakarta.validation.constraints.NotNull;

public record ChangeStateRequest(@NotNull ProfileState state) {
}
