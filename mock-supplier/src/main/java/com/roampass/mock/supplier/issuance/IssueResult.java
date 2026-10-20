package com.roampass.mock.supplier.issuance;

/**
 * 접수 응답. 결과(ICCID·활성화 코드 또는 실패)는 콜백으로
 */
public record IssueResult(String supplierOrderNo, String orderNo, String status) {
}
