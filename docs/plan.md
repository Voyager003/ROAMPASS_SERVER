# 작업 계획 · 체크리스트

**작업 관리용** 문서.
요구사항 원문은 과제 PDF 2.3(필수)·2.8(가산점), 작업 규칙은 `CLAUDE.md`.

## 계획의 근거

- **과제 개요**: 주문 → 결제 → 발급 → 어드민 확인 → 설치 추적 한 줄기를 끝에서 끝까지. "결제 직후 브라우저를 닫으면 이 주문은 어떻게 끝나는가"(→ P4 서버 주도 발급), "설치했다는 것을 무엇으로 아는가"(→ P7 콜백 + 폴링)에 답하는 것이 화면 수보다 중요.
- **도메인 모델을 먼저 전부 만든다(P1).** 상태와 전이 규칙을 한 번에 설계해 README 2.2(상태 설계)의 원본으로 삼고, 이후 Phase는 그 위에 유스케이스·API·영속성을 쌓는다.
- **필수 6개**를 먼저. 순서는 2.10 포기 순서(화면 → 주문 → 추적 → 어드민 → 발급 → 결제)의 역순. 결제는 주문 없이 검증할 수 없어 주문 흐름을 먼저 깐다.
- **평가 비중**: 이해도·설명 30%가 가장 크므로 README 답은 마지막에 몰아 쓰지 않고 **각 Phase 끝에 해당 절 초안**을 쓴다.
- **(2026-10-21 변경) P4부터는 과제가 요구하는 것을 만족하는 최소한만 만든다.** 어드민·설치 추적·유심 개통은 한 Phase(P5)로 합치고, 배포는 맨 뒤(P8)로 미루며, 가산점(B-1~B-3)은 보류. `SupplierClient` 포트는 발급에 어차피 필요하므로 그대로 둔다.
- 프론트(Next.js)는 별도 저장소·별도 계획. 여기에는 **연동 가능 시점**만 표기

## 진행 규칙 (요약, 상세는 CLAUDE.md §6)

- Phase마다 브랜치 `phase/p{N}-{이름}`. 시작 시 sub-step 목록을 확인받고, sub-step마다 코드+테스트 → 검증 → 커밋. 모든 sub-step이 끝나면 확인 후 `main`에 `--no-ff` 머지.
- 도메인 모델은 P1에서 전부 만든다. P2 이후 Phase 내부 순서는 **application(Fake) → api → JPA 매핑·Flyway(마지막)**.
- 범례: `[ ]` 대기 · `[~]` 진행 중 · `[x]` 완료 · `[-]` 포기(사유는 README "미구현" 절에)

---

## P0. 기반 — `phase/p0-foundation`

"clone 후 README 커맨드로 뜬다"를 만드는 Phase. 기능은 없다.

- [x] README 뼈대, plan.md 커밋 (`main`의 `19c185b`)
- [x] **P0-1** 브랜치 생성 + `docs/decisions.md`(지금까지의 결정)
- [x] **P0-2** 설정 구조 + 테스트 인프라 (단일 모듈 상태에서)
  - 의존성: Testcontainers PostgreSQL, Awaitility (Boot 4.1.1 공식 문서로 좌표 확인)
  - `application.yml`(공통·비민감) / `application-local.yml`(ignore) / `application-local.yml.example` / `application-test.yml` / `application-prod.yml`(환경변수만), `.gitignore` 정리
  - `compose.yaml`: postgres 포트 고정, DB명·계정 정리 (로컬 전용 값). **앱 3개를 compose에 넣는 것은 Dockerfile이 생기는 P5에서**
  - Flyway 기동(마이그레이션 0개), `ddl-auto: validate`, `open-in-view: false`
  - 테스트: Testcontainers 베이스 클래스 + 컨텍스트 로드가 실제 PostgreSQL로 통과
- [x] **P0-3** Gradle 멀티모듈 전환
  - 루트(공통 설정) + `roampass-api`(기존 `src`를 `git mv`) / `mock-pg` / `mock-supplier`(DB 없는 빈 Boot 앱). 모듈 간 코드 의존 없음
  - `HELP.md`·`out/` 제거
  - 테스트: 모듈별 컨텍스트 로드, mock 두 앱 `/actuator/health` UP
- [x] **P0-4** `common` 공통 계층 (TDD)
  - 도메인 예외 베이스, 에러 응답 + `@RestControllerAdvice`
  - `UserId`/`AdminId` VO + ArgumentResolver (`/api/**`는 `X-User-Id`, `/admin/**`는 `X-Admin-Id` 필수, 없으면 401)
  - `Clock` 빈, KST 변환 유틸
  - 테스트: VO 검증, 헤더 누락 401·정상 주입(웹 슬라이스), KST 변환(자정 경계)
- [x] **P0-5** `Money` 값 객체 (TDD): amount ≥ 0, 통화(초기 KRW), 같은 통화끼리만 연산·비교, 음수 결과 거부
- [x] **P0-6** README 초안(**실행 방법**, **Q1** 지금까지의 기술 선택 이유) + decisions 갱신 + 이 체크리스트 갱신
- 완료 기준: `./gradlew test` 전 모듈 통과, `docker compose up -d` 후 세 앱 `bootRun` → `/actuator/health` UP
- 테스트 실행: Claude 쪽 환경은 Maven Central·Gradle 접속이 막혀 빌드 불가 → 모든 테스트를 사용자 Mac에서 `./gradlew test`로 실행하고, Claude가 `build/test-results`를 읽어 확인

## P1. 도메인 모델 — `phase/p1-domain`

모든 집합 루트와 값 객체를 **순수 Java**로 만들고 단위 테스트로 규칙을 고정하는 Phase. application·api·JPA·mock 서버는 만들지 않는다.
여기서 정한 상태와 전이 규칙이 README 2.2(상태 설계)의 원본이 된다. 이후 Phase에서 도메인을 고쳐야 하면 그 Phase에서 `refactor`/`fix` 커밋으로 고친다.

- [x] **P1-1** `Plan`, `PlanSpec`, `SimType`(공유 커널) — 국가 코드(ISO alpha-2)·일수·용량 검증, 가격 0원 거부, id 기준 동등성
- [x] **P1-2** `Order`, `OrderNo`, `OrderStatus`, 주문번호 생성기 포트
  - 전체 상태와 전이 규칙: `PENDING → PAID → ISSUE_REQUESTED → ISSUED`, `PAID → PICKUP_READY`(유심), `PENDING → EXPIRED`, `ISSUE_REQUESTED → ISSUE_FAILED → REFUNDED / NEEDS_ATTENTION` 등
  - 생성 시 요금제 가격·SIM 종류 복사, 결제 대기 만료 시각, 여행 시작일(KST 오늘~1년), 조회 시점 만료 판정
- [x] **P1-3** `Payment`, `PaymentStatus`, `IdempotencyKey`, `PgTid` — 승인 멱등(이미 승인이면 no-op), 금액 일치 검증, 전액·부분 취소(취소 누적 ≤ 승인 금액)
- [x] **P1-4** `RefundAttempt`, `RefundStatus`, `RetryPolicy` — 재시도 상한, 지수 백오프로 다음 시도 시각 계산, 상한 초과 시 운영자 확인 대상
- [x] **P1-5** `SimProfile`, `Iccid`(자릿수 19–20 + Luhn), `ActivationCode`, `InstallStatus`, `UsagePeriod`
  - 설치 상태 `ISSUED → QR_VIEWED → INSTALLED → ACTIVE → EXPIRED` 역행 금지, `DELETED` 처리
  - `activatedAt`(UTC) + 일수 → KST 만료일, 남은 기간, 환불 가능 판정(설치·개통 후 불가)
- [x] **P1-6** `StatusHistory`, `Actor`(`USER`/`SYSTEM`/`ADMIN:{id}`/`PG`/`SUPPLIER`, 공유 커널), `HistoryTarget` — 추가만 가능한 기록
- [x] **P1-7** 문서: README **2.2** 상태 설계(주문·결제·설치 상태와 전이도), 데이터 용량은 총량(MB)이라는 가정, decisions, 이 체크리스트
- [x] 결정: 만료일 정의(미결 2), 승인 실패 후 재결제 허용(미결 4), 프로필 `DELETED` 처리(미결 5) — decisions #38, #48, #50
- 완료 기준: 모든 집합 루트의 생성 규칙·상태 전이·불변식이 단위 테스트로 증명됨. Spring 없이 테스트가 돈다

## P2. 요금제 · 주문 — `phase/p2-order` — 과제 2.3-①

P1의 `Plan`·`Order`·`StatusHistory` 위에 유스케이스·영속성·API를 쌓는다. 컨트롤러가 실제 빈을 쓰려면 리포지토리 구현이 먼저 있어야 해서 영속성을 API보다 먼저 진행(decisions #62).

- [x] **P2-1** 요금제 조회 유스케이스: `PlanRepository`, `PlanSearchCondition`(정확히 일치), `PlanQueryService`(표시 순서 정렬, 없는 요금제 404)
- [x] **P2-2** 주문 생성·조회 유스케이스: `OrderRepository`, `StatusHistoryRepository`, `StatusHistoryRecorder`, `OrderService`(생성 이력, 남의 주문 404, 조회 시점 만료 판정)
- [x] **P2-3** 만료 처리 유스케이스: `findExpirable`, `saveIfStatus`(조건부 저장), `OrderExpirationService`
- [x] **P2-4** 영속성: Flyway `V1__init`(plan·orders·status_history + 인덱스) + `V2__seed_plans`(16개), JPA 엔티티·어댑터, 조건부 UPDATE, `Order.restore`·`StatusHistory.restore`, 설정값(`roampass.order.*`), 서비스 빈 등록
- [x] **P2-5** API: `GET /api/plans`, `POST /api/orders`(201), `GET /api/orders/{orderNo}`, 404·검증 실패도 `ProblemDetail + code`
- [x] **P2-6** 만료 스케줄러(30초) + 끝단 테스트(API로 주문 → 시계 10분 경과 → 만료 실행 → `EXPIRED` + history 2건), 테스트에서는 스케줄러 끔
- [x] **P2-7** 문서: README 2.2의 만료 관련 _(구현 예정)_ 제거, 아키텍처 절, decisions, 이 체크리스트
- 완료 기준: 요금제 조회 → 주문 생성 → 10분 후 만료가 테스트로 증명됨, README 실행 방법대로 `bootRun` 후 API 호출 가능

## P3. 결제 — `phase/p3-payment` — 과제 2.3-②, 중복 통지 테스트

중복 통지 1회 처리와 금액 검증이 핵심. `Payment` 도메인은 P1에서 완성되어 있다. 순서는 mock-pg(독립 모듈) → application(Fake) → 영속성 → API(decisions #62).

- [x] **P3-1** `mock-pg` 기본: `POST /mock/pg/approve`(멱등 키 재요청은 처음 결과 반환), `POST /mock/pg/cancel`(전액/부분, 취소 키 멱등 — #47), `GET /mock/pg/payments/{idempotencyKey}`(상태 조회), 승인 후 서명(HMAC-SHA256)된 웹훅 비동기 발송(이벤트 ID 포함)
- [x] **P3-2** `mock-pg` 장애 주입: 실패율 / 응답 지연 / 웹훅 중복 횟수 / 웹훅 생략 / 금액 변조 / 취소 실패 — 설정값 + 런타임 변경 엔드포인트(`/mock/pg/config`) + 동작 테스트
- [x] **P3-3** 결제 유스케이스(Fake): `PgClient` 포트, `PaymentRepository`·`CallbackLogRepository`, 결제 요청(tx#1 `REQUESTED` → PG 호출은 트랜잭션 밖 → tx#2 결과 반영, 요청 시점 만료 재확인), "결제 확정" 단일 유스케이스(응답·웹훅 공용, 주문 `PAID`/`PICKUP_READY` + history, 만료 후 승인은 `REFUNDING`), 웹훅 처리(`CallbackLog` 선 INSERT로 중복 skip, 서명 불일치 거절, 금액 불일치 거절 + 이상 건 기록)
- [x] **P3-4** 영속성: `V3`(`payment`·`callback_log`), 멱등 키 UNIQUE, 주문당 승인 결제 1건 부분 UNIQUE, `event_key` UNIQUE, 어댑터, 서비스 빈 등록
- [x] **P3-5** PG HTTP 어댑터(`RestClient`, 연결·읽기 타임아웃 명시, `MockRestServiceServer` 테스트) + API: `POST /api/orders/{orderNo}/pay`, `POST /api/payments/webhook`
- [x] **P3-6** 응답 유실 정리 스케줄러: 오래 `REQUESTED`로 남은 결제를 PG 상태 조회로 확정
- [x] **P3-7** **필수 테스트(중복 통지): 같은 승인 통지 N개 동시 → payment 1건 APPROVED, 주문 PAID 1회, 이력 1건** (스레드 수·결과를 README에) + 응답과 웹훅이 둘 다 와도 1회, 금액 불일치 거절, 승인 실패 시 주문 상태, 타임아웃 후 상태 조회로 확정
- [x] **P3-8** 문서: README **2.3** 중복 처리 방식·테스트 결과, **2.4** 트랜잭션 경계(PG 호출은 밖, 타임아웃 시 확정 방법), **시연 방법** 중 PG 장애 주입, decisions, 이 체크리스트
- [x] 결정: `event_key` = PG 이벤트 ID(미결 1), 외부 호출 대역 = Fake `PgClient` 빈 + `MockRestServiceServer`(미결 6), 웹훅 서명 HMAC-SHA256 검증
- 완료 기준: 중복 통지 테스트 통과, 장애 주입(지연·실패·중복·금액 변조)을 켠 상태에서도 결제가 정확히 1회만 처리됨

## P4. 발급(이심) · 실패 보상 — `phase/p4-issuance` — 과제 2.3-③ 이심, 중복 콜백·발급 실패 테스트

`SimProfile`·`RefundAttempt` 도메인은 P1에, 수신 기록(`CallbackLog`)·서명 검증(`HmacSignature`)·조건부 UPDATE는 P3에 있다. 순서는 mock → application(Fake) → 영속성 → API·스케줄러(decisions #62). 유심 개통 API는 P5.

- [x] **P4-1** `mock-supplier`(부록 A): `POST /mock/supplier/orders` → 202 + `supplierOrderNo`(같은 멱등 키면 같은 번호), 몇 초 뒤 `X-Signature`(HMAC-SHA256) 콜백(성공: iccid·activationCode / 실패), `GET /mock/supplier/profiles/{iccid}`, 시연용 상태 변경 `POST /mock/supplier/profiles/{iccid}/state`. 장애 주입(발급 실패율 / 콜백 지연·중복·누락 / 서명 오류) + `/mock/supplier/config`
- [x] **P4-2** 발급·환불 유스케이스(Fake): `SupplierClient`·`PgClient.cancel` 포트, `IssuanceRequestRepository`·`SimProfileRepository`·`RefundAttemptRepository`. 발급 요청(`PAID`+ESIM을 조건부 UPDATE로 `ISSUE_REQUESTED` → 공급사 호출은 밖 → `supplierOrderNo` 저장, 접수 실패는 같은 멱등 키로 재시도), 콜백 처리(수신 기록 선 INSERT → `SimProfile` 생성 → 주문 `ISSUED`, 이미 실패 처리된 주문의 늦은 성공은 무시 + 이상 건 기록), 콜백 타임아웃(2분) → `ISSUE_FAILED` → 환불 시작, 환불 재시도(PG 취소, 최대 3회·1분부터 2배) → `REFUNDED` / 상한 초과 `NEEDS_ATTENTION`. 만료 뒤 승인(P3의 `REFUNDING`)도 같은 환불 흐름
- [x] **P4-3** 영속성: `V4`(`issuance_request`·`sim_profile`·`refund_attempt`, 주문번호·iccid·`supplier_order_no` UNIQUE), 어댑터, 서비스 빈 등록 — **P4-2와 한 단계로 진행**(결제 확정 서비스가 환불 요청 저장소를 쓰게 되어, 저장소 구현 없이는 앱 기동 테스트가 실패하므로)
- [x] **P4-4** HTTP 어댑터(공급사 발급 요청, PG 취소) + `POST /api/issuance/callback`(서명 검증 401, 모르는 주문 거절) + 스케줄러(발급 요청 / 콜백 타임아웃 / 환불 재시도)
- [x] **P4-5** **필수 테스트: 같은 콜백 N개 동시 → `sim_profile` 1개·주문 `ISSUED` 1회. 결제됐는데 발급 실패 → `ISSUE_FAILED` → 환불 시도 기록 → `REFUNDED`, 이력 흔적. 환불 3회 실패 → `NEEDS_ATTENTION`**
- [x] **P4-6** 문서: README **2.2** 표의 _(구현 예정)_ 해소, **2.3**·**2.4** 공급사 부분, **시연 방법** 공급사 장애 주입, decisions, 이 체크리스트
- [x] 결정: 콜백 중복 키 = 공급사 이벤트 ID(`SUPPLIER:{eventId}`), 콜백 대기 2분, 환불 재시도 최대 3회·1분부터 2배(#46 유지), 늦은 성공 콜백은 무시 + 이상 건 기록 (decisions에 기록 예정)
- 완료 기준: 중복 콜백·발급 실패 테스트 통과. 장애 주입을 켠 상태에서 결제 → 발급 → 실패 시 환불까지 사람 개입 없이 끝남
- **▶ 마일스톤: 프론트 "구매 흐름"(요금제 → 주문 → 결제 → 발급 대기) 연동 가능**

## P5. 어드민 · 설치/사용 추적 · 유심 개통 — `phase/p5-ops` — 과제 2.3-④·⑤, 2.3-③ 유심, 만료일 테스트

세 묶음을 한 Phase에서 최소한으로. 설치 상태 규칙·만료일 계산·환불 가능 판정·`Iccid` 검증·`PICKUP_READY` 전이는 P1에 있다. 묶음이 작아 단계마다 유스케이스부터 API까지 한 번에(계층별로 나누면 앱 기동 테스트 문제 반복, #61·#62).

- [x] **P5-1** 설치·사용 추적(2.3-④): `SupplierClient.getProfile` 포트·어댑터, 폴링 스케줄러(미개통 SIM 중 여행 시작일 3일 전 ~ 시작일+사용일수 창 안만, 1분 주기)로 `INSTALLED`/`ENABLED`/`DELETED` 반영, `POST /api/sims/{orderNo}/qr-viewed`, **만료일 통합 테스트**(부록 A 예시)
- [x] **P5-2** 유심 개통(2.3-③): `mock-supplier` `POST /mock/supplier/usim/activate`, `POST /api/orders/{orderNo}/usim/register` `{iccid}`(잘못된 ICCID 400, 중복 ICCID 409 — 동시 등록 포함) → 프로필 생성·주문 `ISSUED` → 공급사 개통 → `ACTIVE`(응답 유실은 폴링이 메움)
- [ ] **P5-3** 어드민 조회(2.3-⑤): `V5` `callback_log.order_no`, `GET /admin/orders`(상태·SIM 종류·기간 필터, 페이지네이션 최대 50), `GET /admin/orders/{orderNo}`(이력 타임라인 + 결제·프로필·환불 시도·발급 요청·콜백 기록), 응답에 `asOf`
- [ ] **P5-4** 이상 건 + 운영자 조치(2.3-⑤·④): `GET /admin/issues`(결제 후 5분 미발급 / `NEEDS_ATTENTION` / 금액 불일치 / 서명 실패), `POST /admin/orders/{orderNo}/refund`(전액)·`/reissue`(`X-Admin-Id`·사유 필수, 조건부 UPDATE로 1회, history), 환불 직전 공급사 상태 재조회. 테스트: 두 운영자 동시 환불 → 1회
- [ ] **P5-5** 문서: README **2.2** 남은 _(구현 예정)_, **2.5**(설치 판단: 신호별 신뢰도·폴링 대상·주기·신호가 틀렸을 때), **2.6** 어드민 쪽(`asOf`), **시연 방법** 설치·개통 흉내·ICCID 등록, decisions
- [x] 결정: 폴링은 여행 시작일 창 안의 미개통 SIM만 1분, 이상 건 기준 결제 후 5분 미발급, 수동 환불은 전액만, 어드민 목록은 행별 추가 조회(N+1) + 페이지 최대 50
- 완료 기준: 장애 주입으로 만든 "결제됐는데 발급 안 된 주문"을 어드민에서 찾아 조치. 콜백 없이 폴링만으로 ACTIVE. 만료일 KST 기준. 잘못된/중복 ICCID 거절 후 정상 ICCID로 개통
- **▶ 마일스톤: 프론트 "어드민 화면"·"내 SIM" 연동 가능**

## P6. 사용자 화면용 API · 프론트 연동 확인 — `phase/p6-user-api` — 과제 2.3-⑥

- [ ] `GET /api/sims`(내 SIM 목록), `GET /api/sims/{id}`(결제 금액 · 발급/설치 상태 · QR/활성화 코드 · 남은 기간 · 환불 가능 여부), `POST /api/orders/{orderNo}/refund`(사용자 환불 요청)
- [ ] 발급 대기 응답에 "발급이 늦을 때 사용자가 할 수 있는 일"에 필요한 정보 포함
- [ ] CORS(프론트 도메인), 프론트와 로컬 연동해 구매 흐름·내 SIM·어드민 화면 동작 확인
- [ ] 문서: README **2.6** 사용자 화면 쪽(발급 대기 결과를 받는 방식과 이유)
- 완료 기준: 로컬에서 프론트의 사용자 화면과 어드민 화면이 모두 동작

## P7. 제출 마감 — `phase/p7-submission` — 2.5, 부록 B

- [ ] README **2.1~2.8** 다듬기. 2.1(기술 선택 이유)·2.7(처음 써본 것) 필수. 2.8(시간이 더 있었다면)에 보류한 가산점과 미구현 항목
- [ ] 동시성 테스트 결과(중복 통지·중복 콜백) README 확인, ERD·상태 전이도 정리, **미구현 · 타협** 절, **투입 시간 · AI 사용처** 작성
- [ ] 부록 B 체크리스트 10개 점검, 새 디렉터리에 `git clone` 후 README 커맨드만으로 실행 재현, `git log -p | grep -iE "password|secret"`
- 완료 기준: 배포 항목을 제외한 부록 B 전부 체크

## P8. 배포 — `phase/p8-deploy` — 2.5-② (맨 뒤로 이동)

- [ ] 모듈별 Dockerfile(멀티스테이지), `linux/amd64` 이미지 빌드 → 레지스트리 push
- [ ] Oracle Always Free AMD Micro 2대: A = `roampass-api` + postgres, B = `mock-pg` + `mock-supplier`. 스왑, Docker, VCN 보안 규칙(A↔B 사설 IP), 외부 공개 범위(미결 7)
- [ ] 배포 환경에서 결제 → 웹훅 → 발급 → 콜백 왕복 확인, 프론트 배포 주소와 CORS 연결
- [ ] README **배포** 절(어디에·왜·콜백 주소·막혔던 지점), **2.1** 호스팅 선택 이유, 부록 B 배포 항목 체크
- 완료 기준: 공개 URL로 사용자·어드민 화면 접속, 배포 환경에서 장애 주입 시연 가능

## 보류 — 가산점 B-1 → B-2 → B-3 (필수·배포가 끝나고 시간이 남을 때만)

- [-] **B-1 다국어/다통화**: `Money` 통화 확장(고정 환율), 응답 통화 표시, 에러 메시지 ko/en
- [-] **B-2 공급사 추상화**: 2번째 모의 공급사 + `SupplierClient` 2번째 어댑터 + 공급사 선택 설정
- [-] **B-3 자동 배포**: push → 테스트 → 이미지 빌드 → 인스턴스 재기동 파이프라인

---

## 주문 상태 전이 초안 (P1에서 확정 후 README 2.2로 옮김)

```
PENDING ──결제 승인──▶ PAID ──(ESIM, 스케줄러)──▶ ISSUE_REQUESTED ──콜백──▶ ISSUED
   │                    │                              │
   │ 만료               │ (USIM)                       │ 실패/타임아웃
   ▼                    ▼                              ▼
EXPIRED            PICKUP_READY ──ICCID 등록──▶ ACTIVE   ISSUE_FAILED ──자동 환불──▶ REFUNDED
                                                              │ 상한 초과
                                                              ▼
                                                        NEEDS_ATTENTION ──운영자 조치──▶ REFUNDED / ISSUED
```
결제: `REQUESTED → APPROVED | FAILED`, `APPROVED → CANCELLED | PARTIALLY_CANCELLED`
설치: `ISSUED → QR_VIEWED → INSTALLED → ACTIVE → EXPIRED`

---

## 미결 질문 (결정 시 `docs/decisions.md`)

| # | 질문 | 결정 Phase |
|---|---|---|
| 1 | 콜백 `event_key`: 공급사 ID 기반 vs 본문 해시 — "같은 이벤트가 내용만 조금 달라 다시 오면?" | P3 — **결정됨**: 발신 측 이벤트 ID(`PG:{eventId}`), 재발송도 같은 ID |
| 2 | 만료일: `activatedAt + 24h×N` vs KST 날짜 N일 | P1 — **결정됨**: 24시간 × N, 표시는 KST (decisions #48) |
| 3 | 콜백 타임아웃 · 환불 재시도 상한·간격 · 이상 건 기준 기본값 | P4·P5 — **결정됨**: 콜백 대기 2분, 환불 최대 3회·1분부터 2배 (decisions #84·#85), 이상 건 기준 결제 후 5분 미발급 |
| 4 | 승인 실패 후 같은 주문 재결제 허용 여부(멱등 키 설계에 영향) | P1 — **결정됨**: PENDING 유지·재결제 허용, 승인 결제는 주문당 최대 1건 (decisions #38) |
| 5 | 프로필 `DELETED`와 설치 상태 역행 금지의 조화 | P1 — **결정됨**: 상태 유지 + `deletedAt` 기록 (decisions #50) |
| 6 | api 테스트의 외부 호출 대역 방식 | P3 — **결정됨**: 유스케이스·E2E는 Fake `PgClient` 빈, HTTP 어댑터는 `MockRestServiceServer` |
| 7 | 인스턴스 B 공개 범위: 시연용 장애 주입 설정을 외부에 열지, 어떻게 보호할지 | P8 |
