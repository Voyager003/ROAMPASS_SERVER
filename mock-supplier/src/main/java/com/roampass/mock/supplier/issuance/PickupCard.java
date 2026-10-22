package com.roampass.mock.supplier.issuance;

/** 공항에서 건네는 유심 카드(시연용). ICCID는 카드 뒷면에 적힌 번호 */
public record PickupCard(String orderNo, String iccid) {
}
