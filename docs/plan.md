# 작업 계획 · 체크리스트

**작업 관리용** 문서.
요구사항 원문은 과제 PDF 2.3(필수)·2.8(가산점), 작업 규칙은 `CLAUDE.md`.

## 계획의 근거

- **과제 개요**: 주문 → 결제 → 발급 → 어드민 확인 → 설치 추적 한 줄기를 끝에서 끝까지. "결제 직후 브라우저를 닫으면 이 주문은 어떻게 끝나는가"(→ P4 서버 주도 발급), "설치했다는 것을 무엇으로 아는가"(→ P7 콜백 + 폴링)에 답하는 것이 화면 수보다 중요.
- **도메인 모델을 먼저 전부 만든다(P1).** 상태와 전이 규칙을 한 번에 설계해 README 2.2(상태 설계)의 원본으로 삼고, 이후 Phase는 그 위에 유스케이스·API·영속성을 쌓는다.
- **필수 6개**를 먼저. 순서는 2.10 포기 순서(화면 → 주문 → 추적 → 어드민 → 발급 → 결제)의 역순. 결제는 주문 없이 검증할 수 없어 주문 흐름을 먼저 깐다.
- **평가 비중**: 이해도·설명 30%가 가장 크므로 README 답은 마지막에 몰아 쓰지 않고 **각 Phase 끝에 해당 절 초안**을 쓴다. 접속 URL은 "없으면 평가 불가" 제출물이라 **발급(P4) 직후 1차 배포(P5)**로 리스크를 앞당긴다.
- **가산점**은 다국어/다통화(B-1) → 공급사 추상화(B-2) → 자동 배포(B-3) 3개만, 필수와 README가 끝난 뒤에. 단 `Money` 값 객체(P0)와 `SupplierClient` 포트(P4)는 비용이 거의 없어 미리 깐다.
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
- [ ] **P3-6** 응답 유실 정리 스케줄러: 오래 `REQUESTED`로 남은 결제를 PG 상태 조회로 확정
- [ ] **P3-7** **필수 테스트(중복 통지): 같은 승인 통지 N개 동시 → payment 1건 APPROVED, 주문 PAID 1회, 이력 1건** (스레드 수·결과를 README에) + 응답과 웹훅이 둘 다 와도 1회, 금액 불일치 거절, 승인 실패 시 주문 상태, 타임아웃 후 상태 조회로 확정
- [ ] **P3-8** 문서: README **2.3** 중복 처리 방식·테스트 결과, **2.4** 트랜잭션 경계(PG 호출은 밖, 타임아웃 시 확정 방법), **시연 방법** 중 PG 장애 주입, decisions, 이 체크리스트
- [x] 결정: `event_key` = PG 이벤트 ID(미결 1), 외부 호출 대역 = Fake `PgClient` 빈 + `MockRestServiceServer`(미결 6), 웹훅 서명 HMAC-SHA256 검증
- 완료 기준: 중복 통지 테스트 통과, 장애 주입(지연·실패·중복·금액 변조)을 켠 상태에서도 결제가 정확히 1회만 처리됨

## P4. 발급(이심) · 실패 보상 — `phase/p4-issuance` — 과제 2.3-③ 이심, 중복 콜백·발급 실패 테스트

`SimProfile`·`RefundAttempt` 도메인은 P1에서 완성되어 있다.

- [ ] `SupplierClient` 포트(domain) 정의: 발급 요청 / 프로필 상태 조회 / 유심 개통. B-2를 위해 응답은 공급사 형식이 아닌 우리 도메인 타입으로
- [ ] `mock-supplier` 모듈: `POST /mock/supplier/orders`(202 + `supplierOrderNo`, 몇 초 뒤 콜백 `X-Signature` HMAC-SHA256), `GET /mock/supplier/profiles/{iccid}`, `POST /mock/supplier/usim/activate`
- [ ] `mock-supplier` 장애 주입: 발급 실패율 / 콜백 지연·중복·누락·순서 뒤바뀜 / 서명 오류 + 시연용 `POST /mock/supplier/profiles/{iccid}/state` + 동작 테스트
- [ ] **서버 주도 발급**: 스케줄러가 `PAID && ESIM` 주문을 조건부 UPDATE로 `ISSUE_REQUESTED` 전이 → 공급사 호출(밖) → `supplierOrderNo` 저장. 클라이언트 호출에 의존하지 않음
- [ ] 콜백 `POST /api/issuance/callback`: 서명 검증(원문 바이트, 실패 401 + `signature_ok=false`) → `CallbackLog` 선 INSERT → 모르는 `supplierOrderNo` 거절 → `SimProfile` 생성(`order_id`, `iccid` UNIQUE) → 주문 `ISSUED`
- [ ] 순서 뒤바뀜: 뒤 단계 상태에서 앞 단계 콜백은 무시 + 로그
- [ ] **발급 실패 보상**: 공급사 실패 응답 또는 콜백 타임아웃(설정값) → `ISSUE_FAILED` → `RefundAttempt`(상한 + 지수 백오프) → `REFUNDED` / 상한 초과 `NEEDS_ATTENTION`
- [ ] 발급 접수 실패(5xx·타임아웃) 재시도, "접수됐는데 우리 저장 실패"를 멱등 키로 흡수
- [ ] 영속성(마지막): `sim_profile`·`refund_attempt` 엔티티·마이그레이션
- [ ] **필수 테스트(중복 콜백): 같은 콜백 N개 동시 → sim_profile 1개**
- [ ] **필수 테스트(발급 실패): 결제됐는데 발급 실패 → RefundAttempt 기록 + 주문 상태 + history 흔적**
- [ ] 테스트: 서명 불일치 401, 모르는 주문번호 거절, 순서 뒤바뀜 무시, 환불 상한 초과 → NEEDS_ATTENTION
- [ ] 결정: 콜백 타임아웃 기본값, 환불 재시도 상한·간격(미결 3 일부)
- [ ] README 초안: **2.2**에 "결제됐는데 발급 안 된 주문은 어디 있고 누가 끝내는가", "브라우저를 닫으면" 추가, **2.3**·**2.4** 공급사 부분 추가, **시연 방법** 중 공급사 장애 주입
- 완료 기준: 중복 콜백·발급 실패 테스트 통과. 장애 주입을 켠 상태에서 결제 → 발급 → 실패 시 환불까지 사람 개입 없이 끝남
- **▶ 마일스톤: 프론트 "구매 흐름"(요금제 → 주문 → 결제 → 발급 대기) 연동 가능**

## P5. 1차 배포 — `phase/p5-deploy` — 2.5-②

결제 → 발급 한 줄기가 두 인스턴스 사이의 실제 네트워크를 건너 동작하는지 확인하는 Phase. 가장 처음 해 보는 작업이 많으므로 막힌 지점을 그때그때 기록한다.

- [ ] 모듈별 Dockerfile(멀티스테이지, JVM 옵션 외부 주입), 로컬에서 `linux/amd64` 이미지 빌드 → 레지스트리 push
- [ ] Oracle Always Free 한도 확인 후 AMD Micro 인스턴스 2대 생성. 스왑 1–2GB, Docker 설치
- [ ] 인스턴스 A: `roampass-api` + postgres(축소 설정) compose. 인스턴스 B: `mock-pg` + `mock-supplier` compose
- [ ] VCN 보안 규칙: A↔B는 상대 사설 IP의 해당 포트만. 프록시(80/443)로 외부 공개 범위 결정(미결 7)
- [ ] 배포 환경에서 결제 → 웹훅 → 발급 → 콜백 왕복 확인, `docker stats`/`free -m` 실측 기록
- [ ] 이후 Phase용 재배포 절차를 스크립트 또는 README 명령으로 고정 (B-3의 출발점)
- [ ] README 초안: **배포** 절(어디에·왜·콜백 주소를 잡은 방법·막혔던 지점·실측 메모리), **2.1**에 호스팅 선택 이유
- 완료 기준: 공개 URL로 `roampass-api` 헬스·요금제 조회 가능, 배포 환경에서 중복 통지·중복 콜백·발급 실패 시나리오를 수동 재현 가능

## P6. 어드민 — `phase/p6-admin` — 과제 2.3-⑤

- [ ] `GET /admin/orders`: 결제 상태 · 발급/설치 상태 · SIM 종류 · 기간 필터 + 페이지네이션(DTO 프로젝션)
- [ ] `GET /admin/orders/{orderNo}`: `status_history` 타임라인(언제·무엇·누가·어떤 신호) + 결제·프로필·환불 시도·콜백 로그
- [ ] `GET /admin/issues` 이상 건: PAID 후 N분 미발급 / 환불 실패·상한 초과 / 금액 불일치 통지 / 서명 실패 콜백. 기준은 설정값
- [ ] `POST /admin/orders/{orderNo}/refund`, `POST /admin/orders/{orderNo}/reissue`: 조건부 UPDATE로 1회 실행, 운영자 ID(`X-Admin-Id`)·사유 필수, history 기록
- [ ] PG 취소 실패 재시도 경로(`RefundAttempt` 재사용) + 상한 초과 시 운영자 확인 대상
- [ ] 응답에 `asOf`(조회 시각) 포함
- [ ] 테스트: 두 운영자 동시 환불 → 1회 실행, 이상 건 판정 기준, 목록 필터·페이지네이션
- [ ] 결정: 이상 건 기준값(미결 3 나머지), 부분 환불 허용 여부
- [ ] README 초안: **2.6** 중 어드민 쪽("운영자가 보는 결제 상태가 몇 초 전 데이터인지"), **2.2**에 운영자 조치 전이 추가
- 완료 기준: 장애 주입으로 만든 "결제됐는데 발급 안 된 주문"을 어드민에서 찾아 조치할 수 있음
- **▶ 마일스톤: 프론트 "어드민 화면" 연동 가능**

## P7. 설치 · 사용 추적 — `phase/p7-tracking` — 과제 2.3-④, 만료일 테스트

설치 상태 규칙·만료일 계산·환불 가능 판정은 P1의 `SimProfile`·`UsagePeriod`에 있다. 여기서는 신호를 받아 반영하는 흐름을 만든다.

- [ ] `POST /api/sims/{id}/qr-viewed`: 우리가 직접 기록하는 유일한 신호
- [ ] 공급사 개통 이벤트 콜백 수신(`ENABLED`) + **폴링 보완** 스케줄러: 대상은 여행 시작일 ±N일인 미개통 SIM만, 주기 설정값. 콜백이 빠져도 폴링이 메운다
- [ ] 환불 요청 시 판정 직전 공급사 상태 재조회(늦은 신호 대비). 사용자 주장과 공급사 상태가 다를 때의 처리
- [ ] **권장 테스트(만료일)**: P1 단위 테스트에 더해 개통 콜백 → KST 만료일이 저장·응답되는 통합 테스트, 폴링 대상 선별, 콜백 누락 시 폴링으로 ACTIVE 전이
- [ ] README 초안: **2.5** "설치했다"를 무엇으로 판단하는지(신호별 신뢰도, 콜백 누락 시 메우는 방법, 폴링 대상·주기, 신호가 틀렸을 때 사용자·운영자가 보는 것)
- 완료 기준: 콜백 누락을 켜도 폴링으로 ACTIVE가 되고, 만료일이 KST 기준으로 맞음

## P8. 유심 개통 — `phase/p8-usim` — 과제 2.3-③ 유심

`Iccid` 검증 규칙과 `PICKUP_READY` 전이는 P1에 있다.

- [ ] `POST /api/orders/{orderNo}/usim/register` `{iccid}`: 이미 등록된 ICCID 거절(UNIQUE) → 공급사 개통 요청 → `ACTIVE`
- [ ] 테스트: 잘못된 ICCID 400, 중복 ICCID 거절(동시 등록 포함), 개통 흐름 통합
- [ ] README 초안: **2.2** 유심 분기 추가, **시연 방법**에 ICCID 등록
- 완료 기준: 잘못된/중복 ICCID가 거절되고 정상 ICCID로 개통까지 감

## P9. 사용자 화면용 API · 프론트 연동 확인 — `phase/p9-user-api` — 과제 2.3-⑥

- [ ] `GET /api/sims`(내 SIM 목록), `GET /api/sims/{id}`(결제 금액 · 발급/설치 상태 · QR/활성화 코드 · 남은 기간 · 환불 가능 여부)
- [ ] 발급 대기 응답에 "발급이 늦을 때 사용자가 할 수 있는 일"에 필요한 정보(예상 상태·문의 경로) 포함
- [ ] CORS(프론트 도메인), 프론트와 실제 연동해 구매 흐름·내 SIM·어드민 화면 동작 확인
- [ ] README 초안: **2.6** 사용자 화면 쪽(발급 대기 결과를 받는 방식: 폴링/SSE/새로고침과 그 이유)
- 완료 기준: 배포된 프론트에서 사용자 화면과 어드민 화면이 모두 동작

## P10. 제출 마감 — `phase/p10-submission` — 2.5, 부록 B

- [ ] README **2.1~2.8** 다듬기. 2.1(기술 선택 이유)·2.7(처음 써본 것) 필수. 2.8(시간이 더 있었다면)에 제외한 가산점(데이터 사용량 알림·장기 미설치 관리·관측성)과 미구현 항목
- [ ] 동시성 테스트 실행 결과(중복 통지·중복 콜백 테스트의 스레드 수·결과) README에 첨부, ERD·상태 전이도(Mermaid) 정리
- [ ] **미구현 · 타협** 절, **가정** 절, **투입 시간 · AI 사용처** 작성
- [ ] 최종 배포 점검: 두 화면 모두 공개 URL 접속, 배포 환경에서 장애 주입 시연 가능
- [ ] 부록 B 체크리스트 10개 점검, 새 디렉터리에 `git clone` 후 README 커맨드만으로 실행 재현, `git log -p | grep -iE "password|secret"`
- 완료 기준: 부록 B 10개 전부 체크

## P11. 가산점 — B-1 → B-2 → B-3 (P10 이후, 이 순서로)

- [ ] **B-1 다국어/다통화** — `phase/p11-b1-i18n`: `Money` 통화 확장(고정 환율 설정값), 요금제·주문 응답에 통화 표시, 에러 메시지 ko/en(`Accept-Language`)
- [ ] **B-2 공급사 추상화** — `phase/p11-b2-supplier`: 응답 형식·상태 이름이 다른 2번째 모의 공급사 + `SupplierClient` 2번째 어댑터(상태 이름 매핑) + 요금제/주문별 공급사 선택 설정. README 2.1·아키텍처에 설계 설명
- [ ] **B-3 자동 배포** — `phase/p11-b3-cd`: push → 테스트 → 이미지 빌드·push → 인스턴스 A/B pull·재기동 파이프라인. 시크릿은 CI 비밀 변수로

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
| 3 | 콜백 타임아웃 · 환불 재시도 상한·간격 · 이상 건 기준 기본값 | P4, P6 |
| 4 | 승인 실패 후 같은 주문 재결제 허용 여부(멱등 키 설계에 영향) | P1 — **결정됨**: PENDING 유지·재결제 허용, 승인 결제는 주문당 최대 1건 (decisions #38) |
| 5 | 프로필 `DELETED`와 설치 상태 역행 금지의 조화 | P1 — **결정됨**: 상태 유지 + `deletedAt` 기록 (decisions #50) |
| 6 | api 테스트의 외부 호출 대역 방식 | P3 — **결정됨**: 유스케이스·E2E는 Fake `PgClient` 빈, HTTP 어댑터는 `MockRestServiceServer` |
| 7 | 인스턴스 B 공개 범위: 시연용 장애 주입 설정을 외부에 열지, 어떻게 보호할지 | P5 |
