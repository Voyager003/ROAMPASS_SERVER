-- 어드민 주문 상세에서 그 주문의 콜백 기록을 찾기 위한 주문번호. 서명 불일치 기록은 본문을 믿을 수 없어 NULL
ALTER TABLE callback_log ADD COLUMN order_no VARCHAR(30);
CREATE INDEX idx_callback_log_order_no ON callback_log (order_no);
