-- 시드 요금제 16개: 4개 국가 × 이심/유심 × 2개 상품. 가격은 가상의 값(KRW)
-- 유심은 실물 카드 비용을 반영해 같은 구성의 이심보다 2,000원 높게 설정
INSERT INTO plan (id, country_code, days, data_mb, sim_type, price_amount, price_currency) VALUES
    (1,  'JP', 5,  3072,  'ESIM', 9900,  'KRW'),
    (2,  'JP', 10, 10240, 'ESIM', 19900, 'KRW'),
    (3,  'JP', 5,  3072,  'USIM', 11900, 'KRW'),
    (4,  'JP', 10, 10240, 'USIM', 21900, 'KRW'),
    (5,  'US', 7,  5120,  'ESIM', 15900, 'KRW'),
    (6,  'US', 15, 20480, 'ESIM', 32900, 'KRW'),
    (7,  'US', 7,  5120,  'USIM', 17900, 'KRW'),
    (8,  'US', 15, 20480, 'USIM', 34900, 'KRW'),
    (9,  'VN', 5,  5120,  'ESIM', 6900,  'KRW'),
    (10, 'VN', 10, 15360, 'ESIM', 12900, 'KRW'),
    (11, 'VN', 5,  5120,  'USIM', 8900,  'KRW'),
    (12, 'VN', 10, 15360, 'USIM', 14900, 'KRW'),
    (13, 'TH', 5,  5120,  'ESIM', 7900,  'KRW'),
    (14, 'TH', 10, 15360, 'ESIM', 13900, 'KRW'),
    (15, 'TH', 5,  5120,  'USIM', 9900,  'KRW'),
    (16, 'TH', 10, 15360, 'USIM', 15900, 'KRW');

-- id를 직접 넣었으므로 이후 자동 증가가 17부터 이어지도록 시퀀스 조정
SELECT setval('plan_id_seq', (SELECT MAX(id) FROM plan));
