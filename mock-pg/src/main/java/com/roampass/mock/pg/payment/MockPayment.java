package com.roampass.mock.pg.payment;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * PG 쪽 결제 한 건. 취소 상태가 바뀌므로 변경·조회는 모두 synchronized
 */
class MockPayment {

    private final String idempotencyKey;
    private final String orderNo;
    private final long amount;
    private final String currency;
    private final String pgTid;
    private final Instant approvedAt;
    private final Map<String, CancelResult> cancels = new HashMap<>();
    private MockPaymentStatus status;
    private long cancelledAmount;

    MockPayment(ApproveCommand command, String pgTid, MockPaymentStatus status, Instant approvedAt) {
        this.idempotencyKey = command.idempotencyKey();
        this.orderNo = command.orderNo();
        this.amount = command.amount();
        this.currency = command.currency();
        this.pgTid = pgTid;
        this.status = status;
        this.approvedAt = approvedAt;
    }

    String pgTid() {
        return pgTid;
    }

    /** 같은 멱등 키 재요청이 처음 요청과 같은 내용인지 여부 */
    boolean isSameRequest(ApproveCommand command) {
        return orderNo.equals(command.orderNo()) && amount == command.amount()
                && currency.equals(command.currency());
    }

    synchronized CancelResult cancel(CancelCommand command) {
        CancelResult previous = cancels.get(command.cancelKey());
        if (previous != null) {
            if (previous.cancelAmount() != command.amount()) {
                throw new MockPgException(HttpStatus.CONFLICT, "CANCEL_KEY_REUSED",
                        "Cancel key already used with a different amount");
            }
            return previous;
        }
        if (status != MockPaymentStatus.APPROVED && status != MockPaymentStatus.PARTIALLY_CANCELLED) {
            throw new MockPgException(HttpStatus.CONFLICT, "NOT_CANCELLABLE", "Payment is " + status);
        }
        if (command.amount() <= 0 || command.amount() > amount - cancelledAmount) {
            throw new MockPgException(HttpStatus.BAD_REQUEST, "CANCEL_AMOUNT_EXCEEDED",
                    "Cancel amount exceeds the remaining amount");
        }
        cancelledAmount += command.amount();
        status = cancelledAmount == amount ? MockPaymentStatus.CANCELLED : MockPaymentStatus.PARTIALLY_CANCELLED;
        CancelResult result = new CancelResult(pgTid, command.cancelKey(), command.amount(), cancelledAmount, status);
        cancels.put(command.cancelKey(), result);
        return result;
    }

    synchronized PaymentView view() {
        return new PaymentView(idempotencyKey, pgTid, orderNo, amount, currency, status, cancelledAmount, approvedAt);
    }
}
