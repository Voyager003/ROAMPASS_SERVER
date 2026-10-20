-- 결제 시도. 시도 1건 = 멱등 키 1개
CREATE TABLE payment (
    id               BIGSERIAL PRIMARY KEY,
    idempotency_key  VARCHAR(40)  NOT NULL UNIQUE,
    order_no         VARCHAR(30)  NOT NULL REFERENCES orders (order_no),
    amount           BIGINT       NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3)   NOT NULL,
    status           VARCHAR(30)  NOT NULL,
    pg_tid           VARCHAR(100),
    cancelled_amount BIGINT       NOT NULL CHECK (cancelled_amount >= 0 AND cancelled_amount <= amount),
    failure_reason   VARCHAR(500),
    requested_at     TIMESTAMPTZ  NOT NULL,
    approved_at      TIMESTAMPTZ
);

-- 주문당 승인된 결제는 최대 1건(애플리케이션 검사가 뚫려도 DB가 이중 승인 반영을 거부)
CREATE UNIQUE INDEX uq_payment_order_approved ON payment (order_no)
    WHERE status IN ('APPROVED', 'PARTIALLY_CANCELLED', 'CANCELLED');
-- 주문의 시도 목록
CREATE INDEX idx_payment_order_requested ON payment (order_no, requested_at);
-- 응답 유실 정리 스케줄러: 오래된 REQUESTED
CREATE INDEX idx_payment_status_requested ON payment (status, requested_at);

-- 외부 통지(PG 웹훅·공급사 콜백) 수신 기록
-- event_key UNIQUE로 중복 판정, 서명 실패 기록은 event_key NULL(유니크 제약 대상 아님)
CREATE TABLE callback_log (
    id           BIGSERIAL PRIMARY KEY,
    source       VARCHAR(20)  NOT NULL,
    event_key    VARCHAR(200) UNIQUE,
    signature_ok BOOLEAN      NOT NULL,
    payload      TEXT         NOT NULL,
    outcome      VARCHAR(30)  NOT NULL,
    note         VARCHAR(500),
    received_at  TIMESTAMPTZ  NOT NULL
);
