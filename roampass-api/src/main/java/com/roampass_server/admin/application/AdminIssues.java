package com.roampass_server.admin.application;

import java.util.List;

import com.roampass_server.admin.application.AdminOrderDetail.CallbackInfo;

/**
 * 운영자 확인이 필요한 이상 건
 *
 * @param stuckAfterPayment   결제 승인 후 기준 시간이 지나도록 발급되지 않은 주문(PAID·ISSUE_REQUESTED)
 * @param needsAttention      자동 처리를 포기한 주문(환불 재시도 소진 등)
 * @param amountMismatches    금액이 주문과 다른 결제 통지(최근)
 * @param signatureFailures   서명이 틀린 통지(최근)
 * @param stuckThresholdMinutes 결제 후 미발급 판정 기준(분)
 */
public record AdminIssues(List<AdminOrderSummary> stuckAfterPayment, List<AdminOrderSummary> needsAttention,
                          List<CallbackInfo> amountMismatches, List<CallbackInfo> signatureFailures,
                          long stuckThresholdMinutes) {
}
