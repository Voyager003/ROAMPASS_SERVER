-- 요금제 카탈로그. V2 시드로만 채움
CREATE TABLE plan (
    id             BIGSERIAL PRIMARY KEY,
    country_code   VARCHAR(2)  NOT NULL,
    days           INT         NOT NULL CHECK (days >= 1),
    data_mb        INT         NOT NULL CHECK (data_mb >= 1),
    sim_type       VARCHAR(10) NOT NULL,
    price_amount   BIGINT      NOT NULL CHECK (price_amount > 0),
    price_currency VARCHAR(3)  NOT NULL
);

-- 주문. order는 SQL 예약어라 orders
CREATE TABLE orders (
    id                BIGSERIAL PRIMARY KEY,
    order_no          VARCHAR(30) NOT NULL UNIQUE,
    user_id           VARCHAR(64) NOT NULL,
    plan_id           BIGINT      NOT NULL REFERENCES plan (id),
    sim_type          VARCHAR(10) NOT NULL,
    amount            BIGINT      NOT NULL CHECK (amount > 0),
    currency          VARCHAR(3)  NOT NULL,
    travel_start_date DATE        NOT NULL,
    status            VARCHAR(30) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    issued_at         TIMESTAMPTZ
);

-- 만료 스케줄러: status = 'PENDING' AND expires_at <= now ORDER BY expires_at
CREATE INDEX idx_orders_status_expires_at ON orders (status, expires_at);
-- 내 주문 목록(P9)
CREATE INDEX idx_orders_user_created_at ON orders (user_id, created_at);

-- 상태 변경 이력. 추가 전용, 모든 기록에 주문번호
CREATE TABLE status_history (
    id          BIGSERIAL PRIMARY KEY,
    order_no    VARCHAR(30)  NOT NULL REFERENCES orders (order_no),
    target_type VARCHAR(20)  NOT NULL,
    target_key  VARCHAR(100) NOT NULL,
    from_status VARCHAR(30),
    to_status   VARCHAR(30)  NOT NULL,
    actor       VARCHAR(80)  NOT NULL,
    reason      VARCHAR(500),
    occurred_at TIMESTAMPTZ  NOT NULL
);

-- 어드민 주문 타임라인: order_no 기준 시간순
CREATE INDEX idx_status_history_order_occurred ON status_history (order_no, occurred_at, id);
