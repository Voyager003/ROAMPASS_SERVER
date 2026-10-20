# 작업 계획 · 체크리스트

**작업 관리용** 문서.
요구사항 원문과 ID(`R-*`, `T-*`, `B-*`)는 `docs/requirements.md`, 작업 규칙은 `CLAUDE.md`.

## 계획의 근거

- **과제 개요**: 주문 → 결제 → 발급 → 어드민 확인 → 설치 추적 한 줄기를 끝에서 끝까지. "결제 직후 브라우저를 닫으면 이 주문은 어떻게 끝나는가"(→ P3 서버 주도 발급), "설치했다는 것을 무엇으로 아는가"(→ P6 콜백 + 폴링)에 답하는 것이 화면 수보다 중요.
- **필수 6개**를 먼저. 순서는 2.10 포기 순서(화면 → 주문 → 추적 → 어드민 → 발급 → 결제)의 역순. 결제는 주문 없이 검증할 수 없어 주문 뼈대를 먼저 깐다.
- **평가 비중**: 이해도·설명 30%가 가장 크므로 README 답은 마지막에 몰아 쓰지 않고 **각 Phase 끝에 해당 절 초안**을 쓴다. 접속 URL은 "없으면 평가 불가" 제출물이라 **P3 직후 1차 배포**로 리스크를 앞당긴다.
- **가산점**은 다국어/다통화(B-1) → 공급사 추상화(B-2) → 자동 배포(B-3) 3개만, 필수와 README가 끝난 뒤에. 단 `Money` 값 객체(P0)와 `SupplierClient` 포트(P3)는 비용이 거의 없어 미리 깐다.
- 프론트(Next.js)는 별도 저장소·별도 계획. 여기에는 **연동 가능 시점**만 표기

## 진행 규칙 (요약, 상세는 CLAUDE.md §6)

- Phase마다 브랜치 `phase/p{N}-{이름}`. 시작 시 sub-step 목록을 확인받고, sub-step마다 코드+테스트 → 검증 → 커밋. 모든 sub-step이 끝나면 확인 후 `main`에 `--no-ff` 머지.
- Phase 내부 순서는 항상 **도메인(순수 Java) → application(Fake) → api → JPA 매핑·Flyway(마지막)**.
- 범례: `[ ]` 대기 · `[~]` 진행 중 · `[x]` 완료 · `[-]` 포기(사유는 README "미구현" 절에)

---

## P0. 기반 — `phase/p0-foundation`

- [ ] 문서 커밋: `README.md` 뼈대, `docs/requirements.md`, `docs/plan.md`, `docs/decisions.md`(지금까지의 결정 기입), `docs/learning-log.md`, `docs/timelog.md`
- [ ] Gradle 멀티모듈 전환: 루트(공통 설정) + `roampass-api`(기존 `src` 이동) / `mock-pg` / `mock-supplier` 빈 앱. 모듈 간 코드 의존 없음
- [ ] 설정 구조 전환: `application.yml`(공통, 커밋) / `application-local.yml`(민감, ignore) / `application-local.yml.example` / `application-test.yml` / `application-prod.yml`. `.gitignore` 정리
- [ ] `compose.yaml`: postgres(포트 고정) + 3개 앱 로컬 실행. `HELP.md`·`out/` 제거
- [ ] 의존성 추가(Boot 4 좌표를 공식 문서로 확인): Testcontainers PostgreSQL, Awaitility. Mockito·AssertJ는 starter 포함 여부 확인
- [ ] `roampass-api` 패키지 골격 + `common`: 도메인 예외 베이스, 에러 응답(`@RestControllerAdvice`), `X-User-Id`/`X-Admin-Id` ArgumentResolver, `Clock` 빈, KST 유틸
- [ ] `Money` 값 객체(amount, currency — 초기 KRW 고정) + 단위 테스트
- [ ] Flyway 기동 설정 + Testcontainers 베이스 클래스 + 모듈별 컨텍스트 로드 테스트
- [ ] 결정: api 테스트에서 외부 호출(PG·공급사) 대역 방식 — HTTP 스텁 서버 라이브러리 vs mock 앱을 테스트에서 함께 기동 (미결 6)
- [ ] README 초안: **실행 방법** (compose + gradle 커맨드), **Q1** 중 지금까지 결정한 기술 선택 이유
- 완료 기준: `./gradlew test` 전 모듈 통과, `docker compose up` 후 세 앱 `/actuator/health` UP

## P1. 요금제 · 주문 — `phase/p1-order` — R-1

- [ ] `Plan` 도메인 + `GET /api/plans`(국가·일수·용량·sim_type 필터) + 시드 요금제 마이그레이션(이심/유심 × 국가 몇 개)
- [ ] `Order` 도메인: 생성 시 `PENDING`, `OrderNo` 발급, 여행 시작일, 결제 대기 만료 시각(설정값, 기본 10분). 상태 전이 메서드와 `IllegalStateTransitionException`
- [ ] **`StatusHistory` 기록 컴포넌트**(`history` 패키지): 대상·from·to·actor·reason·at. 이후 모든 Phase가 사용
- [ ] `POST /api/orders`, `GET /api/orders/{orderNo}`(본인만). 후자는 프론트의 발급 대기 폴링에도 쓰인다
- [ ] 만료 스케줄러: `PENDING && expiresAt < now` → `EXPIRED` (+ history)
- [ ] 테스트: 주문 생성·만료 전이 단위(Clock 고정), 영속성 통합, 만료 스케줄러 통합
- [ ] 결정: `OrderNo` 형식, 결제 대기 시간 기본값
- [ ] README 초안: **Q2** 상태 목록 첫 버전(PENDING/EXPIRED), 아키텍처 절에 패키지 구조
- 완료 기준: 요금제 조회 → 주문 생성 → 10분 후 만료가 테스트로 증명됨

## P2. 결제 — `phase/p2-payment` — R-2, T-1

중복 통지 1회 처리와 금액 검증이 핵심.

- [ ] `mock-pg` 모듈: `POST /mock/pg/approve`(approved/failed + pgTid), `POST /mock/pg/cancel`(전액/부분), `GET /mock/pg/payments/{key}`(상태 조회), 승인 후 웹훅 비동기 발송
- [ ] `mock-pg` 장애 주입: 실패율 / 응답 지연 / 웹훅 중복 횟수 / 웹훅 생략 / 금액 변조 / 취소 실패 — 설정값 + 런타임 변경 엔드포인트(`/mock/pg/config`) + 동작 테스트
- [ ] `Payment` 도메인: `REQUESTED → APPROVED | FAILED`, `APPROVED → CANCELLED | PARTIALLY_CANCELLED`, 승인 멱등(이미 APPROVED면 no-op)
- [ ] `PgClient` 포트(domain) + HTTP 어댑터(infrastructure/external, 타임아웃 명시)
- [ ] **`CallbackLog` 공통 컴포넌트**: `event_key UNIQUE` 선 INSERT로 중복 판정, `signature_ok`, payload 보관. P3 콜백도 재사용
- [ ] `POST /api/orders/{orderNo}/pay`: tx#1 `REQUESTED` 생성(멱등 키 UNIQUE) → PG 호출(트랜잭션 밖) → tx#2 결과 반영
- [ ] "결제 확정" 단일 유스케이스: 승인 응답 경로·웹훅 경로 공용. 결제 완료 → 주문 `PAID` (+ history)
- [ ] 웹훅 수신 `POST /api/payments/webhook`: `CallbackLog` 선 INSERT → 중복 skip(200), **금액 ≠ 주문 금액이면 거절 + 이상 건 기록**
- [ ] 승인 타임아웃·응답 유실: `REQUESTED`로 남은 결제를 스케줄러가 PG 상태 조회로 정리
- [ ] **T-1 테스트: 같은 승인 통지 N개 동시 → payment 1건 APPROVED, 주문 PAID 1회** (스레드 수·결과를 README Q3에)
- [ ] 테스트: 응답+웹훅 둘 다 와도 1회, 금액 불일치 거절, 승인 실패 시 주문 상태, 타임아웃 후 상태 조회로 확정
- [ ] 결정: `event_key` 구성(미결 1), 승인 실패 후 재결제 허용 여부(미결 4)
- [ ] README 초안: **Q3** 중복 처리 방식·테스트 결과, **Q4** 트랜잭션 경계(PG 호출은 밖, 타임아웃 시 확정 방법), **시연 방법** 중 PG 장애 주입
- 완료 기준: T-1 통과, 장애 주입(지연·실패·중복·금액 변조)을 켠 상태에서도 결제가 정확히 1회만 처리됨

## P3. 발급(이심) · 실패 보상 — `phase/p3-issuance` — R-3-1 ~ R-3-4, T-2, T-3

- [ ] `SupplierClient` 포트(domain) 정의: 발급 요청 / 프로필 상태 조회 / 유심 개통. B-2를 위해 응답은 공급사 형식이 아닌 우리 도메인 타입으로
- [ ] `mock-supplier` 모듈: `POST /mock/supplier/orders`(202 + `supplierOrderNo`, 몇 초 뒤 콜백 `X-Signature` HMAC-SHA256), `GET /mock/supplier/profiles/{iccid}`, `POST /mock/supplier/usim/activate`
- [ ] `mock-supplier` 장애 주입: 발급 실패율 / 콜백 지연·중복·누락·순서 뒤바뀜 / 서명 오류 + 시연용 `POST /mock/supplier/profiles/{iccid}/state` + 동작 테스트
- [ ] `SimProfile` 도메인(`InstallStatus`는 일단 `ISSUED` 하나, P6에서 확장) + `Iccid` 값 객체 틀
- [ ] **서버 주도 발급**: 스케줄러가 `PAID && ESIM` 주문을 조건부 UPDATE로 `ISSUE_REQUESTED` 전이 → 공급사 호출(밖) → `supplierOrderNo` 저장. 클라이언트 호출에 의존하지 않음
- [ ] 콜백 `POST /api/issuance/callback`: 서명 검증(원문 바이트, 실패 401 + `signature_ok=false`) → `CallbackLog` 선 INSERT → 모르는 `supplierOrderNo` 거절 → `SimProfile` 생성(`order_id`, `iccid` UNIQUE) → 주문 `ISSUED`
- [ ] 순서 뒤바뀜: 뒤 단계 상태에서 앞 단계 콜백은 무시 + 로그
- [ ] **발급 실패 보상**: 공급사 실패 응답 또는 콜백 타임아웃(설정값) → `ISSUE_FAILED` → `RefundAttempt`(상한 + 지수 백오프) → `REFUNDED` / 상한 초과 `NEEDS_ATTENTION`
- [ ] 발급 접수 실패(5xx·타임아웃) 재시도, "접수됐는데 우리 저장 실패"를 멱등 키로 흡수
- [ ] **T-2 테스트: 같은 콜백 N개 동시 → sim_profile 1개**
- [ ] **T-3 테스트: 결제됐는데 발급 실패 → RefundAttempt 기록 + 주문 상태 + history 흔적**
- [ ] 테스트: 서명 불일치 401, 모르는 주문번호 거절, 순서 뒤바뀜 무시, 환불 상한 초과 → NEEDS_ATTENTION
- [ ] 결정: 콜백 타임아웃 기본값, 환불 재시도 상한·간격(미결 3 일부)
- [ ] README 초안: **Q2** 상태 전이도 확정판("결제됐는데 발급 안 된 주문은 어디 있고 누가 끝내는가", "브라우저를 닫으면"), **Q3**·**Q4** 공급사 부분 추가, **시연 방법** 중 공급사 장애 주입
- 완료 기준: T-2·T-3 통과. 장애 주입을 켠 상태에서 결제 → 발급 → 실패 시 환불까지 사람 개입 없이 끝남
- **▶ 마일스톤: 프론트 "구매 흐름"(요금제 → 주문 → 결제 → 발급 대기) 연동 가능**

## P4. 1차 배포 — `phase/p4-deploy` — 2.5-②

결제 → 발급 한 줄기가 두 인스턴스 사이의 실제 네트워크를 건너 동작하는지 확인하는 Phase. 가장 처음 해 보는 작업이 많으므로 막힌 지점을 그때그때 기록한다.

- [ ] 모듈별 Dockerfile(멀티스테이지, JVM 옵션 외부 주입), 로컬에서 `linux/amd64` 이미지 빌드 → 레지스트리 push
- [ ] Oracle Always Free 한도 확인 후 AMD Micro 인스턴스 2대 생성. 스왑 1–2GB, Docker 설치
- [ ] 인스턴스 A: `roampass-api` + postgres(축소 설정) compose. 인스턴스 B: `mock-pg` + `mock-supplier` compose
- [ ] VCN 보안 규칙: A↔B는 상대 사설 IP의 해당 포트만. 프록시(80/443)로 외부 공개 범위 결정(미결 7)
- [ ] 배포 환경에서 결제 → 웹훅 → 발급 → 콜백 왕복 확인, `docker stats`/`free -m` 실측 기록
- [ ] 이후 Phase용 재배포 절차를 스크립트 또는 README 명령으로 고정 (B-3의 출발점)
- [ ] README 초안: **배포** 절(어디에·왜·콜백 주소를 잡은 방법·막혔던 지점·실측 메모리), **Q1**에 호스팅 선택 이유
- 완료 기준: 공개 URL로 `roampass-api` 헬스·요금제 조회 가능, 배포 환경에서 T-1~T-3 시나리오를 수동 재현 가능

## P5. 어드민 — `phase/p5-admin` — R-5

- [ ] `GET /admin/orders`: 결제 상태 · 발급/설치 상태 · SIM 종류 · 기간 필터 + 페이지네이션(DTO 프로젝션)
- [ ] `GET /admin/orders/{orderNo}`: `status_history` 타임라인(언제·무엇·누가·어떤 신호) + 결제·프로필·환불 시도·콜백 로그
- [ ] `GET /admin/issues` 이상 건: PAID 후 N분 미발급 / 환불 실패·상한 초과 / 금액 불일치 통지 / 서명 실패 콜백. 기준은 설정값
- [ ] `POST /admin/orders/{orderNo}/refund`, `POST /admin/orders/{orderNo}/reissue`: 조건부 UPDATE로 1회 실행, 운영자 ID(`X-Admin-Id`)·사유 필수, history 기록
- [ ] PG 취소 실패 재시도 경로(`RefundAttempt` 재사용) + 상한 초과 시 운영자 확인 대상
- [ ] 응답에 `asOf`(조회 시각) 포함
- [ ] 테스트: 두 운영자 동시 환불 → 1회 실행, 이상 건 판정 기준, 목록 필터·페이지네이션
- [ ] 결정: 이상 건 기준값(미결 3 나머지), 부분 환불 허용 여부
- [ ] README 초안: **Q6** 중 어드민 쪽("운영자가 보는 결제 상태가 몇 초 전 데이터인지"), **Q2**에 운영자 조치 전이 추가
- 완료 기준: 장애 주입으로 만든 "결제됐는데 발급 안 된 주문"을 어드민에서 찾아 조치할 수 있음
- **▶ 마일스톤: 프론트 "어드민 화면" 연동 가능**

## P6. 설치 · 사용 추적 — `phase/p6-tracking` — R-4, T-4

- [ ] `InstallStatus` 확장: `ISSUED → QR_VIEWED → INSTALLED → ACTIVE → EXPIRED`, 역행 금지. `DELETED` 처리 방식 결정(미결 5)
- [ ] `POST /api/sims/{id}/qr-viewed`: 우리가 직접 기록하는 유일한 신호
- [ ] 공급사 개통 이벤트 콜백 수신(`ENABLED`) + **폴링 보완** 스케줄러: 대상은 여행 시작일 ±N일인 미개통 SIM만, 주기 설정값. 콜백이 빠져도 폴링이 메운다
- [ ] 만료 계산: `activatedAt(UTC)` + 요금제 일수 → KST 기준. "24h×N" vs "KST 날짜 N일" 결정(미결 2)
- [ ] 환불 가능 판정: `INSTALLED/ACTIVE`면 불가, 판정 직전 공급사 상태 재조회(늦은 신호 대비). 사용자 주장과 공급사 상태가 다를 때의 규칙
- [ ] **T-4 테스트: UTC → KST 만료일 계산(자정 경계 포함)**, 상태 역행 거부, 폴링 대상 선별, 콜백 누락 시 폴링으로 ACTIVE 전이
- [ ] README 초안: **Q5** "설치했다"를 무엇으로 판단하는지(신호별 신뢰도, 콜백 누락 시 메우는 방법, 폴링 대상·주기, 신호가 틀렸을 때 사용자·운영자가 보는 것)
- 완료 기준: 콜백 누락을 켜도 폴링으로 ACTIVE가 되고, 만료일이 KST 기준으로 맞음

## P7. 유심 — `phase/p7-usim` — R-3-5, R-3-6

- [ ] 유심 주문은 결제 완료 시 `PICKUP_READY`(P2의 결제 확정 유스케이스에서 sim_type 분기)
- [ ] `POST /api/orders/{orderNo}/usim/register` `{iccid}`: `Iccid` 값 객체(자릿수 19–20, Luhn 체크 디지트), 이미 등록된 ICCID 거절(UNIQUE) → 공급사 개통 요청 → `ACTIVE`
- [ ] 테스트: ICCID 검증 단위(정상·자릿수·체크 디지트), 중복 ICCID 거절, 개통 흐름 통합
- [ ] README 초안: **Q2** 유심 분기 추가, **시연 방법**에 ICCID 등록
- 완료 기준: 잘못된/중복 ICCID가 거절되고 정상 ICCID로 개통까지 감

## P8. 사용자 화면용 API · 프론트 연동 확인 — `phase/p8-user-api` — R-6

- [ ] `GET /api/sims`(내 SIM 목록), `GET /api/sims/{id}`(결제 금액 · 발급/설치 상태 · QR/활성화 코드 · 남은 기간 · 환불 가능 여부)
- [ ] 발급 대기 응답에 "발급이 늦을 때 사용자가 할 수 있는 일"에 필요한 정보(예상 상태·문의 경로) 포함
- [ ] CORS(프론트 도메인), 프론트와 실제 연동해 구매 흐름·내 SIM·어드민 화면 동작 확인
- [ ] README 초안: **Q6** 사용자 화면 쪽(발급 대기 결과를 받는 방식: 폴링/SSE/새로고침과 그 이유)
- 완료 기준: 배포된 프론트에서 사용자 화면과 어드민 화면이 모두 동작

## P9. 제출 마감 — `phase/p9-submission` — 2.5, 부록 B

- [ ] README **Q1~Q8** 다듬기. Q1(기술 선택 이유)·Q7(처음 써본 것, `docs/learning-log.md` 기반) 필수. Q8(시간이 더 있었다면)에 B-4~B-7과 미구현 항목
- [ ] 동시성 테스트 실행 결과(T-1·T-2 스레드 수·결과) README에 첨부, ERD·상태 전이도(Mermaid) 정리
- [ ] **미구현 · 타협** 절, **가정** 절, **투입 시간 · AI 사용처**(`docs/timelog.md` 기반) 작성
- [ ] 최종 배포 점검: 두 화면 모두 공개 URL 접속, 배포 환경에서 장애 주입 시연 가능
- [ ] 부록 B 체크리스트 10개 점검, 새 디렉터리에 `git clone` 후 README 커맨드만으로 실행 재현, `git log -p | grep -iE "password|secret"`
- 완료 기준: 부록 B 10개 전부 체크

## P10. 가산점 — B-1 → B-2 → B-3 (P9 이후, 이 순서로)

- [ ] **B-1 다국어/다통화** — `phase/p10-b1-i18n`: `Money` 통화 확장(고정 환율 설정값), 요금제·주문 응답에 통화 표시, 에러 메시지 ko/en(`Accept-Language`)
- [ ] **B-2 공급사 추상화** — `phase/p10-b2-supplier`: 응답 형식·상태 이름이 다른 2번째 모의 공급사 + `SupplierClient` 2번째 어댑터(상태 이름 매핑) + 요금제/주문별 공급사 선택 설정. README Q1·아키텍처에 설계 설명
- [ ] **B-3 자동 배포** — `phase/p10-b3-cd`: push → 테스트 → 이미지 빌드·push → 인스턴스 A/B pull·재기동 파이프라인. 시크릿은 CI 비밀 변수로

---

## 주문 상태 전이 초안 (P1~P3에서 확정 후 README 아키텍처 절로 옮김)

```
PENDING ──결제 승인──▶ PAID ──(ESIM, 스케줄러)──▶ ISSUE_REQUESTED ──콜백──▶ ISSUED
   │                    │                              │
   │ 만료               │ (USIM, P7)                   │ 실패/타임아웃
   ▼                    ▼                              ▼
EXPIRED            PICKUP_READY ──ICCID 등록──▶ ACTIVE   ISSUE_FAILED ──자동 환불──▶ REFUNDED
                                                              │ 상한 초과
                                                              ▼
                                                        NEEDS_ATTENTION ──운영자 조치──▶ REFUNDED / ISSUED
```
결제: `REQUESTED → APPROVED | FAILED`, `APPROVED → CANCELLED | PARTIALLY_CANCELLED`
설치(P6): `ISSUED → QR_VIEWED → INSTALLED → ACTIVE → EXPIRED`

---

## 미결 질문 (결정 시 `docs/decisions.md`)

| # | 질문 | 결정 Phase |
|---|---|---|
| 1 | 콜백 `event_key`: 공급사 ID 기반 vs 본문 해시 — "같은 이벤트가 내용만 조금 달라 다시 오면?" | P2 |
| 2 | 만료일: `activatedAt + 24h×N` vs KST 날짜 N일 | P6 |
| 3 | 콜백 타임아웃 · 환불 재시도 상한·간격 · 이상 건 기준 기본값 | P3, P5 |
| 4 | 승인 실패 후 같은 주문 재결제 허용 여부(멱등 키 설계에 영향) | P2 |
| 5 | 프로필 `DELETED`와 설치 상태 역행 금지의 조화 | P6 |
| 6 | api 테스트의 외부 호출 대역 방식 | P0 |
| 7 | 인스턴스 B 공개 범위: 시연용 장애 주입 설정을 외부에 열지, 어떻게 보호할지 | P4 |
