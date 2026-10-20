# 설계 · 기술 결정 기록

기술·설계 선택이 생길 때마다 바로 한 줄씩 추가한다. README Q1(기술 선택 이유)과 Q2~Q6 답의 원본.
형식: 날짜 | 선택 | 고려한 대안 | 이유. 결정이 바뀌면 지우지 않고 새 줄로 추가하고 이전 줄에 "→ #번호로 변경"을 단다.

| # | 날짜 | 선택 | 고려한 대안 | 이유 |
|---|---|---|---|---|
| 1 | 2026-10-19 | 백엔드 Java 21 + Spring Boot 4.1.1 + Gradle(Kotlin DSL) | — | Spring Initializr 생성본을 그대로 사용. Boot 4는 의존성 좌표가 3.x와 달라 추가할 때마다 공식 문서로 확인 |
| 2 | 2026-10-19 | DB PostgreSQL, 스키마는 Flyway + `ddl-auto: validate` | `ddl-auto: update`, Liquibase | 과제 측 사용 DB. 빈 DB에서 스키마·시드 재현이 제출 요건(2.5-④)이라 마이그레이션 파일로 관리 |
| 3 | 2026-10-19 | 비동기 작업(발급 요청·재시도·만료)은 DB 상태 기반 `@Scheduled` 폴링 | 커밋 후 이벤트 + `@Async`, 메시지 큐(Redis/RabbitMQ) | 결제 커밋 후 클라이언트가 사라져도 서버가 발급을 이어감(2.3-③ "브라우저를 닫아도"). 외부 인프라가 없고 서버 재시작에 안전, 테스트가 쉬움. 대가: 폴링 주기만큼의 지연과 조회 비용 |
| 4 | 2026-10-19 | 통합·동시성 테스트는 Testcontainers PostgreSQL | compose의 PG 재사용, H2(PostgreSQL 모드) | 유니크 제약·조건부 UPDATE·락의 동작은 실제 PostgreSQL에서만 검증 의미가 있음. 대가: 테스트 실행에 Docker 필요 |
| 5 | 2026-10-19 | Lombok 제한 사용(`@Getter`, `@NoArgsConstructor(PROTECTED)`, `@RequiredArgsConstructor`, `@Slf4j`), domain 패키지는 Lombok 미사용 | 자유 사용, 미사용 | setter·빌더를 막아 상태 변경이 의도가 드러나는 메서드로만 일어나게 함. 도메인은 순수 Java 유지 |
| 6 | 2026-10-19 | 인증은 `X-User-Id` / `X-Admin-Id` 헤더, 회원 도메인 없음 | 회원 테이블 + 로그인 | 과제 2.2가 헤더 수준이면 충분하고 평가 항목이 아니라고 명시. 헤더 위조 가능성은 README "가정"에 기록 |
| 7 | 2026-10-20 | 패키지는 도메인별(`order`, `payment` …) + 내부 레이어(api / application / domain / infrastructure) | 전역 레이어드, 헥사고날 | 도메인 경계가 README 설명 단위와 일치. 외부 시스템은 domain이 정의한 포트(`PgClient`, `SupplierClient`)로 접근 |
| 8 | 2026-10-20 | domain은 순수 Java, JPA 엔티티는 `infrastructure/persistence`에 분리하고 각 Phase의 마지막에 매핑 | 도메인 객체에 JPA 애너테이션 직접 사용 | 상태 전이·멱등 규칙을 Spring 없이 단위 테스트. 도메인이 확정된 뒤 스키마를 정해 잘못된 스키마로 도메인이 굳는 것을 피함. 대가: 매퍼 코드 증가 |
| 9 | 2026-10-20 | Gradle 멀티모듈 3개: `roampass-api` / `mock-pg` / `mock-supplier`, 모듈 간 코드 의존 없음(HMAC 서명 코드도 각자 구현) | 단일 앱에 `mock/` 패키지 | PG 타임아웃·공급사 다운·콜백 누락을 실제 프로세스·네트워크 분리로 재현. 외부 시스템과는 HTTP 계약만 공유하고, 독립 구현인데 서명이 맞는 것을 계약 준수의 증거로 삼음 |
| 10 | 2026-10-20 | 모의 PG·공급사 상태는 각 mock 앱의 인메모리 저장(DB 없음) | mock 전용 DB | 1GB 인스턴스 메모리 절약. 대가: mock 재시작 시 모의 상태가 사라짐(README에 명시) |
| 11 | 2026-10-20 | 배포는 Oracle Cloud Always Free AMD Micro(1GB) 2대. A: api + PostgreSQL, B: mock-pg + mock-supplier, 인스턴스 간 VCN 사설 IP 통신 | 1대에 단일 앱, 1대에 3모듈, 외부 관리형 DB(Neon·Supabase) | 1GB 1대에 JVM 3개 + DB는 OOM 위험(추정치 기준). 무료 한도 안에서 메모리를 확보하고 외부 시스템이 실제로 다른 호스트에 있는 구조. 상시 가동이라 스케줄러가 멈추지 않음. Always Free 한도는 인스턴스 생성 시 콘솔에서 확인 필요 |
| 12 | 2026-10-20 | 이미지는 인스턴스에서 빌드하지 않고 로컬/CI에서 `linux/amd64`로 빌드 후 pull | 인스턴스에서 Gradle 빌드 | 1GB에서 Gradle 빌드는 메모리 부족·과도한 시간 위험 |
| 13 | 2026-10-20 | 상태 변경 이력은 별도 이력 테이블(`status_history`)에 같은 트랜잭션으로 기록 | Hibernate Envers 등 자동 감사, 이벤트 소싱 | 과제 2.3-⑤가 "언제·무엇이·누가/어떤 신호로"를 요구 → 사유·신호 출처를 담아야 해 자동 감사로는 부족. 이벤트 소싱은 과제 규모에 과함 |
| 14 | 2026-10-20 | `Money` 값 객체(금액 + 통화)를 P0부터 사용, `SupplierClient` 포트를 P3에서 정의 | 금액을 `long`으로, 공급사 HTTP 클라이언트 직접 사용 | 가산점 다통화·공급사 추상화를 나중에 싸게 붙이기 위한 사전 설계. 비용이 거의 없음 |
| 15 | 2026-10-20 | Phase마다 브랜치 `phase/p{N}-{이름}`, 완료 시 `--no-ff` 머지 | `main` 직접 커밋, PR | 과제가 커밋으로 "어떤 순서로 문제를 풀었는지"를 봄(2.5-⑥). 머지 커밋이 Phase 경계를 드러냄 |
| 16 | 2026-10-20 | 1차 배포를 발급 Phase(P3) 직후에 수행 | 모든 기능 완성 후 한 번에 배포 | 접속 URL은 없으면 평가 불가인 제출물이고, 처음 해 보는 작업(인스턴스 2대·사설 IP·amd64 빌드)이 가장 많음. 리스크를 앞당김 |
| 17 | 2026-10-20 | P0의 compose는 PostgreSQL만, 앱 3개는 `bootRun`으로 실행. 앱의 compose 편입은 P4 | P0부터 앱 3개를 compose로 | 앱을 compose로 띄우려면 Dockerfile이 필요하고 이는 배포 Phase의 작업 |
