package com.roampass.mock.supplier.api;

import jakarta.validation.constraints.NotBlank;

/** 유심 수령 카드 발급 요청(시연용): 어느 주문에 내줄 카드인지 */
public record PickupCardRequest(@NotBlank String orderNo) {
}
