-- 공급사 발급 요청. 주문의 시도마다 1건(재발급 대비), 접수 번호는 접수 응답 또는 콜백으로 채움
CREATE TABLE issuance_request (
    id                BIGSERIAL PRIMARY KEY,
    order_no          VARCHAR(30)  NOT NULL REFERENCES orders (order_no),
    attempt           INT          NOT NULL CHECK (attempt >= 1),
    supplier_order_no VARCHAR(100) UNIQUE,
    status            VARCHAR(20)  NOT NULL,
    failure_reason    VARCHAR(500),
    requested_at      TIMESTAMPTZ  NOT NULL,
    completed_at      TIMESTAMPTZ,
    UNIQUE (order_no, attempt)
);
-- 접수 재전송 대상(PENDING)·콜백 대기 시간 초과 대상(PENDING·ACCEPTED)
CREATE INDEX idx_issuance_request_status_requested ON issuance_request (status, requested_at);

-- SIM 프로필. 한 주문에 1개, ICCID·접수 번호도 유일(중복 콜백의 최후 방어)
CREATE TABLE sim_profile (
    id                BIGSERIAL PRIMARY KEY,
    order_no          VARCHAR(30)  NOT NULL UNIQUE REFERENCES orders (order_no),
    sim_type          VARCHAR(10)  NOT NULL,
    iccid             VARCHAR(20)  NOT NULL UNIQUE,
    activation_code   VARCHAR(255),
    supplier_order_no VARCHAR(100) UNIQUE,
    usage_days        INT          NOT NULL CHECK (usage_days >= 1),
    install_status    VARCHAR(20)  NOT NULL,
    issued_at         TIMESTAMPTZ  NOT NULL,
    qr_viewed_at      TIMESTAMPTZ,
    installed_at      TIMESTAMPTZ,
    activated_at      TIMESTAMPTZ,
    deleted_at        TIMESTAMPTZ
);

-- 환불 요청. PG 취소 재시도는 이 행 하나에서 추적(시도 횟수·다음 시도 시각·마지막 에러)
CREATE TABLE refund_attempt (
    id            BIGSERIAL PRIMARY KEY,
    payment_key   VARCHAR(40)  NOT NULL REFERENCES payment (idempotency_key),
    order_no      VARCHAR(30)  NOT NULL REFERENCES orders (order_no),
    amount        BIGINT       NOT NULL CHECK (amount > 0),
    currency      VARCHAR(3)   NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    attempt_count INT          NOT NULL CHECK (attempt_count >= 0),
    next_retry_at TIMESTAMPTZ,
    last_error    VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL,
    completed_at  TIMESTAMPTZ
);
-- 환불 실행 스케줄러: PENDING이면서 다음 시도 시각이 지난 요청
CREATE INDEX idx_refund_attempt_status_next ON refund_attempt (status, next_retry_at);
CREATE INDEX idx_refund_attempt_order ON refund_attempt (order_no);
