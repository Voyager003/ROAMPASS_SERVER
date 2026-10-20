# ROAMPASS_SERVER

여행자용 유심(USIM)·이심(eSIM) 판매 서비스 **ROAMPASS**의 서버

---

## 1. 실행 방법

### 사전 요구사항
- JDK 21
- Docker (로컬 PostgreSQL과 통합 테스트용 Testcontainers에 필요)

### 로컬 실행
```bash
git clone https://github.com/Voyager003/ROAMPASS_SERVER.git
cd ROAMPASS_SERVER

# 1) 로컬 접속 정보 파일 만들기 (둘 다 gitignore 대상, 두 파일의 값은 서로 같아야 함)
cp .env.example .env
cp roampass-api/src/main/resources/application-local.yml.example \
   roampass-api/src/main/resources/application-local.yml

# 2) 로컬 PostgreSQL 기동 (localhost:5432)
docker compose up -d

# 3) 세 앱 실행 (각각 다른 터미널에서)
./gradlew :roampass-api:bootRun     # http://localhost:8080
./gradlew :mock-pg:bootRun          # http://localhost:8081
./gradlew :mock-supplier:bootRun    # http://localhost:8082

# 4) 기동 확인
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```
DB 스키마와 시드 데이터는 `roampass-api` 기동 시 Flyway가 자동으로 적용합니다.

### 테스트
```bash
./gradlew test
```
- Docker가 실행 중이어야 합니다. `roampass-api` 통합 테스트는 Testcontainers로 PostgreSQL 17 컨테이너를 직접 띄우므로 `docker compose`나 `application-local.yml`은 필요 없습니다.

### API 호출 시 식별 헤더
회원 시스템 대신 헤더로 호출자를 구분합니다(과제 2.2 허용 범위).
- 사용자 API(`/api/**`): `X-User-Id: user-1`
- 어드민 API(`/admin/**`): `X-Admin-Id: admin-1`

---

## 2. 질문에 대한 답

### 2.1 기술선택 이유

> 진행 중인 초안입니다. 프론트(Next.js)와 배포 호스팅 항목은 해당 작업을 마친 뒤 채웁니다. 결정 전체와 시점은 `docs/decisions.md`에 있습니다.

| 영역 | 선택 | 이유 | 고려했다가 버린 것 |
|---|---|---|---|
| 백엔드 | Java 21, Spring Boot 4.1.1, Gradle | 과제가 지정한 Spring. Initializr 생성본을 그대로 사용 | — |
| DB | PostgreSQL + Flyway(`ddl-auto: validate`) | 유니크 제약·`timestamptz`를 그대로 활용. 빈 DB에서 스키마·시드가 재현되어야 해서 마이그레이션 파일로 관리 | Hibernate `ddl-auto: update` |
| ORM | Spring Data JPA, 단 **도메인 모델과 JPA 엔티티를 분리** | 상태 전이·멱등 규칙을 Spring·DB 없이 단위 테스트하기 위함. JPA 매핑은 도메인이 확정된 뒤 각 단계의 마지막에 작성 | 도메인 객체에 JPA 애너테이션 직접 사용 (대가: 매퍼 코드 증가) |
| 비동기 작업 | DB 상태 기반 `@Scheduled` 폴링 | 결제 커밋 후 사용자가 브라우저를 닫아도 서버가 발급을 끝까지 진행. 외부 인프라가 없고 서버 재시작에도 작업이 사라지지 않음 | 커밋 후 이벤트 + `@Async`, 메시지 큐 (대가: 폴링 주기만큼 지연) |
| 모의 PG · 공급사 | 별도 Spring Boot 앱 2개 (Gradle 멀티모듈), 우리 서버와 **HTTP로만** 통신 | PG 타임아웃·공급사 다운·콜백 누락을 실제 프로세스·네트워크 분리로 재현. 서명(HMAC) 코드도 모듈마다 따로 구현해 "계약만 공유"함을 보임 | 같은 앱 안의 컨트롤러 |
| 테스트 | JUnit 5 + AssertJ, Testcontainers PostgreSQL | 중복 처리의 핵심인 유니크 제약·조건부 UPDATE는 실제 PostgreSQL에서만 검증 의미가 있음 | H2 (PostgreSQL 모드) |
| 인증 | `X-User-Id` / `X-Admin-Id` 헤더 | 과제가 헤더 수준이면 충분하고 평가 항목이 아니라고 명시. 헤더는 위조 가능하다는 한계를 인지 | 회원 도메인 + 로그인 |
| 에러 응답 | RFC 9457 `ProblemDetail` + `code` | Spring 내장 표준 형식. 도메인 예외는 HTTP를 모르고, 웹 계층에서 상태 코드로 매핑 | 자체 응답 형식 |
| 금액 | `Money` 값 객체 (금액 + 통화, 음수 금지) | 부분 취소 시 "승인 금액보다 많이 취소"를 값 수준에서 차단. 다통화 확장 대비 | `long` 금액만 사용 |
| 프론트 | _(작성 예정)_ | | |
| 배포 | _(작성 예정)_ Oracle Cloud Always Free 인스턴스 2대 예정 | | |

### 2.2 결제, 발급, 주문 상태 설계

### 2.3 중복 처리를 어떻게 막았고, 어떻게 증명했습니까?

### 2.4 트랜잭션 경계

### 2.5 "이 사용자가 SIM을 설치했다"를 무엇으로 판단

### 2.6 어드민과 사용자 화면에서 서버와 클라이언트의 경계

### 2.7 이번 과제에서 처음 써본 것

### 2.8 시간이 더 있었다면?

---

## 시연 방법

_(모의 PG·공급사의 실패·지연·중복을 켜는 방법, 설치·개통을 흉내 내는 방법)_

---

## 배포

_(배포한 곳과 고른 이유, 콜백 주소를 잡은 방법, 배포하며 막혔던 지점)_

---

## 아키텍처 · 데이터 모델

| 모듈 | 역할 | 배포 위치 |
|---|---|---|
| `roampass-api` | 주문·결제 확정·발급 오케스트레이션·어드민·설치 추적 | 인스턴스 A (+ PostgreSQL) |
| `mock-pg` | 모의 PG (승인·취소·웹훅·장애 주입) | 인스턴스 B |
| `mock-supplier` | 모의 공급사 (발급·콜백·상태 조회·유심 개통·장애 주입) | 인스턴스 B |

### roampass-api 구조
도메인별 패키지 안에 계층을 둡니다. 의존은 항상 도메인 쪽(안쪽)으로만 향합니다.
```
com.roampass_server
├── common/
│   ├── domain/   # 순수 Java 공유 커널: 도메인 예외, UserId·AdminId, Money, KoreaTime
│   ├── web/      # 에러 응답(ProblemDetail), 식별 헤더 처리
│   └── config/   # Clock 빈
└── {도메인}/      # plan, order, payment, issuance, admin, history (단계별로 추가)
    ├── api/             # 컨트롤러, 요청·응답 DTO
    ├── application/     # 유스케이스, 트랜잭션 경계
    ├── domain/          # 순수 Java 모델 · 리포지토리 인터페이스
    └── infrastructure/  # JPA 엔티티·어댑터, 외부 HTTP 클라이언트, 스케줄러
```

_(ERD, 주문 상태 전이도는 해당 단계에서 추가)_

---

## 미구현 · 타협한 부분과 이유

