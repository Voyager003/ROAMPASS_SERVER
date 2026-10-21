-- 주문 생성 멱등 키. 클라이언트가 주문 폼을 띄울 때 만든 키로, 같은 사용자의 재전송(응답 유실·서버 재시작 후 재시도)을 같은 주문으로 판정
-- 키 없이 만든 주문(기존 주문, 헤더 없는 호출)은 NULL, 유니크 판정 대상에서 제외
ALTER TABLE orders ADD COLUMN idempotency_key VARCHAR(64);

CREATE UNIQUE INDEX uq_orders_user_idempotency_key
    ON orders (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
