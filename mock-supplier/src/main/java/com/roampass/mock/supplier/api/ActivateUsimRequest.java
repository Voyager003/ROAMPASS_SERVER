package com.roampass.mock.supplier.api;

import jakarta.validation.constraints.NotBlank;

public record ActivateUsimRequest(@NotBlank String iccid) {
}
