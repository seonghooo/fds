# FDS — 실시간 이상거래 탐지 시스템

> **Fraud Detection System**
> 금융 거래의 이상 패턴을 실시간으로 탐지하고 차단하는 풀스택 애플리케이션입니다.
> 룰 엔진(Drools) · 이벤트 스트림(Kafka) · 인메모리 캐시(Redis)를 결합한 하이브리드 아키텍처로 구현했습니다.

---

## 목차

- [개요](#개요)
- [기술 스택](#기술-스택)
- [시스템 아키텍처](#시스템-아키텍처)
- [FDS 탐지 룰 6가지](#fds-탐지-룰-6가지)
- [핵심 설계 결정](#핵심-설계-결정)
- [API 명세](#api-명세)
- [데이터베이스 설계](#데이터베이스-설계)
- [디렉터리 구조](#디렉터리-구조)
- [실행 방법](#실행-방법)
- [테스트](#테스트)

---

## 개요

### 배경

금융기관의 FDS는 자금세탁, 보이스피싱, 이상 송금 등 금융 범죄를 실시간으로 차단하는 핵심 인프라입니다.
본 프로젝트는 실제 FDS의 핵심 요소인 **실시간성 · 정확성 · 확장성**을 직접 설계하고 구현하는 것을 목표로 했습니다.

### 해결한 문제

| 문제 | 해결 방법 |
|------|-----------|
| 탐지 룰이 코드에 하드코딩되면 변경 시마다 재배포 필요 | Drools 룰 엔진 분리 → `.drl` 파일과 `application.yml`만 수정하면 적용 |
| 탐지와 저장이 하나의 트랜잭션이면 DB 장애 시 거래도 실패 | Kafka 비동기 분리 → 탐지 응답과 DB 저장 완전 독립 |
| 빈도 탐지를 DB로 구현하면 매 요청마다 레인지 스캔 발생 | Redis `INCR + EXPIRE` → O(1) 슬라이딩 윈도우 |
| 신뢰할 수 있는 수신자도 동일 룰에 걸림 | 화이트리스트 API → 등록된 수신자는 빈도 기반 룰 제외 |
| Consumer 장애 시 메시지 유실 가능성 | Retry Topic + DLQ(Dead Letter Queue) 3단계 재시도 구성 |

---

## 기술 스택

### 백엔드

| 분류 | 기술 | 버전 | 선택 이유 |
|------|------|------|-----------|
| Framework | Spring Boot | 4.0.2 | 표준 엔터프라이즈 스택 |
| Language | Java | 17 | LTS, Record/Sealed 등 현대적 문법 |
| Rule Engine | Drools | 9.x | 비즈니스 룰과 코드 분리, 재배포 없이 룰 변경 |
| Message Queue | Apache Kafka | - | 고가용성 이벤트 스트림, Consumer 재처리 보장 |
| Cache | Redis | 7 | TTL 기반 슬라이딩 윈도우, O(1) 빈도 계산 |
| Database | MySQL | 8.0 | 거래 이력 영구 저장, 인덱스 기반 페이지네이션 |
| Auth | Spring Security + JWT | - | Stateless 인증, 세션 서버 불필요 |
| API Docs | SpringDoc OpenAPI | 2.8.5 | Swagger UI 자동 생성 |
| Build | Gradle | - | 의존성 관리 및 빌드 자동화 |
| Infra | Docker Compose | - | 로컬 인프라 환경 일원화 |

### 프론트엔드

| 분류 | 기술 |
|------|------|
| Framework | React 18 + TypeScript |
| Bundler | Vite |
| HTTP Client | Axios (JWT 인터셉터 내장) |
| Routing | React Router v6 |

---

## 시스템 아키텍처

### 전체 흐름

```
                         React Frontend
                               │
                    POST /api/v1/transfer/check
                               │
                               ▼
                    ┌─────────────────────┐
                    │    FdsController    │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────────────────────────┐
                    │              FdsService                  │
                    │                                         │
                    │  1. DB 조회    → trusted 여부 확인       │
                    │  2. Redis 조회 → 1분 내 거래 횟수        │
                    │  3. Redis 조회 → 1분 내 고유 수신자 수   │
                    │  4. Redis 조회 → 유저 평균 송금액        │
                    │  5. Drools     → 룰 6개 동시 실행        │
                    │  6. Redis 갱신 → 평균 송금액 누적        │
                    └──────────┬──────────────────────────────┘
                               │
                ┌──────────────┴──────────────┐
                ▼                             ▼
        즉시 응답 (동기)              Kafka Producer (비동기)
     { status, reasons }                     │
                                             ▼
                                    transaction-topic
                                             │
                                    ┌────────┴────────┐
                                    │  성공 시         │  실패 시
                                    ▼                  ▼
                             TransactionConsumer   retry-topic (3회)
                                    │                  │ 최종 실패
                                    │              DLQ(Dead Letter Queue)
                                    ▼
                             BLOCKED 이벤트만
                             fraud_history 저장 (MySQL)
```

### 처리 흐름 요약

| 경로 | 처리 내용 |
|------|-----------|
| **동기 (실시간)** | 요청 → Redis 조회 → Drools 룰 실행 → 즉시 APPROVED/BLOCKED 응답 |
| **비동기 (감사)** | 탐지 결과 → Kafka → Consumer → BLOCKED 거래만 DB 저장 |
| **장애 복구** | Consumer 실패 → retry-0 → retry-1 → retry-2 → DLQ |

---

## FDS 탐지 룰 6가지

> 화이트리스트에 등록된 송금자-수취인 쌍은 **모든 빈도/분산 기반 룰에서 제외**됩니다.
> 복수의 룰이 동시에 탐지되면 모든 사유를 응답에 포함합니다.

| # | 룰명 | 탐지 조건 | 기본 임계값 |
|---|------|-----------|-------------|
| 1 | 고액 송금 감지 | 1회 송금액 ≥ 임계값 | 1,000만 원 |
| 2 | 차단 IP 감지 | 송신자 IP가 차단 패턴에 매칭 | `192\.168\.0\..*` |
| 3 | 단기 반복 송금 | 1분 내 동일 송금자의 송금 횟수 ≥ 임계값 | 5회 |
| 4 | 새벽 고액 송금 | 새벽 01~05시 + 금액 ≥ 임계값 | 100만 원 |
| 5 | 단기 분산 송금 | 1분 내 서로 다른 수신자 수 ≥ 임계값 (보이스피싱 패턴) | 3명 |
| 6 | 평균 대비 이상 금액 | 유저 과거 평균의 N배 이상 (최소 5건 데이터 필요) | 5배 |

### 임계값 변경 방법 (재배포 불필요)

```yaml
# application.yml
fds:
  rules:
    amount-threshold: 10000000          # 룰1: 고액 기준 (원)
    blacklisted-ip-pattern: "192\\.168\\.0\\..*"  # 룰2: 차단 IP 정규식
    frequency-threshold: 5              # 룰3: 빈도 기준 (회/분)
    night-amount-threshold: 1000000     # 룰4: 새벽 고액 기준 (원)
    distributed-receiver-threshold: 3   # 룰5: 분산 수신자 수 기준
    avg-amount-multiplier: 5            # 룰6: 평균 대비 배수 기준
```

### 응답 예시

```json
// BLOCKED — 두 가지 룰 동시 탐지
{
  "status": "BLOCKED",
  "reasons": [
    "고액 송금 감지 (1000만원 이상)",
    "새벽 시간대 고액 송금 감지 (새벽 1~5시, 100만원 이상)"
  ]
}

// APPROVED
{
  "status": "APPROVED",
  "reasons": []
}
```

---

## 핵심 설계 결정

### 1. Drools 룰 엔진 분리

일반 if-else 방식 대비 장점:

```
if-else 방식                     Drools 방식
─────────────────────────────    ────────────────────────────
룰 변경 → 코드 수정              룰 변경 → .drl 파일만 수정
       → 빌드/배포 필요                 → 서버 재시작만 필요
       → 테스트 재실행                  → 임계값은 yml에서 주입
```

### 2. Kafka 비동기 분리

```
[동기 처리 — 응답 지연 없음]
클라이언트 ──→ FdsService ──→ 즉시 응답 반환
                    │
                    └──→ Kafka 전송 (fire-and-forget)

[비동기 처리 — DB 장애가 응답에 영향 없음]
Kafka ──→ Consumer ──→ DB 저장
              │
              └─ 실패 시 Retry Topic(3회) → DLQ
```

### 3. Redis 슬라이딩 윈도우

```
DB 방식: SELECT COUNT(*) ... WHERE created_at > NOW() - 60s
         → 매 요청마다 인덱스 스캔 발생

Redis 방식:
  INCR tx_count:{senderId}       # O(1) 카운터 증가
  EXPIRE tx_count:{senderId} 60  # 1분 후 자동 삭제 (슬라이딩 윈도우)
```

> **크래시 복구**: `INCR` 성공 후 `EXPIRE` 실패 시 TTL=-1인 키가 남을 수 있습니다.
> TTL 조회 후 -1이면 강제로 EXPIRE를 설정하여 키가 영구 잔존하는 상황을 방지합니다.

### 4. 분산 송금 탐지 — Redis Set 활용

```
1분 내 여러 수신자 감지 → Redis Set 사용 이유:
  List: 중복 허용 → 같은 수신자 여러 번 세면 안됨
  Set:  중복 자동 제거 → 고유 수신자 수만 카운팅

SADD tx_receivers:{senderId} {receiverId}
SCARD tx_receivers:{senderId}  # 고유 수신자 수
```

---

## API 명세

> Swagger UI: **`http://localhost:8080/swagger-ui.html`**
> 로그인 후 Authorize 버튼에 JWT 토큰을 등록하면 인증이 필요한 API를 직접 테스트할 수 있습니다.

### 인증

| Method | URI | 권한 | 설명 |
|--------|-----|------|------|
| POST | `/api/v1/auth/signup` | Public | 일반 사용자 가입 |
| POST | `/api/v1/auth/login` | Public | 로그인 → JWT 토큰 반환 |
| POST | `/api/v1/auth/logout` | USER / ADMIN | 토큰 블랙리스트 등록 (Redis) |
| POST | `/api/v1/admin/users` | ADMIN | 관리자 계정 생성 |

### 거래 탐지

| Method | URI | 권한 | 설명 |
|--------|-----|------|------|
| POST | `/api/v1/transfer/check` | USER / ADMIN | 실시간 이상 거래 탐지 |
| GET | `/api/v1/transfer/history` | USER / ADMIN | 내 차단 내역 조회 (페이지네이션) |

**요청 예시 (POST /check)**
```json
{
  "senderId": "user123",
  "receiverId": "receiver456",
  "amount": 15000000,
  "deviceId": "iPhone-ABC123",
  "timestamp": "2026-04-29T14:00:00"
}
```

> `senderIp`는 서버에서 HTTP 요청 헤더로 자동 추출합니다. 클라이언트가 직접 전송하지 않습니다.

### 화이트리스트 (ADMIN 전용)

| Method | URI | 권한 | 설명 |
|--------|-----|------|------|
| GET | `/api/v1/whitelist` | ADMIN | 전체 목록 조회 |
| POST | `/api/v1/whitelist` | ADMIN | 송금자-수취인 쌍 등록 |
| DELETE | `/api/v1/whitelist/{id}` | ADMIN | 항목 삭제 |

### 이상 거래 이력 (ADMIN 전용)

| Method | URI | 권한 | 설명 |
|--------|-----|------|------|
| GET | `/api/v1/admin/fraud-history` | ADMIN | 전체 차단 내역 조회 (페이지네이션, 최신순) |

---

## 데이터베이스 설계

### ERD

```
users
├── id            BIGINT  PK
├── username      VARCHAR UNIQUE
├── password      VARCHAR (BCrypt 암호화)
└── role          ENUM (USER / ADMIN)

trusted_receiver                              (화이트리스트)
├── id            BIGINT  PK
├── sender_id     VARCHAR INDEX
├── receiver_id   VARCHAR
└── UNIQUE (sender_id, receiver_id)

fraud_history                                 (차단된 거래 이력)
├── id            BIGINT  PK
├── sender_id     VARCHAR INDEX
├── receiver_id   VARCHAR
├── amount        BIGINT
├── detected_rules VARCHAR (콤마 구분 룰 목록)
└── detected_at   DATETIME INDEX
```

### 인덱스 전략

| 인덱스 | 용도 |
|--------|------|
| `fraud_history.sender_id` | 특정 유저의 이력 조회 (`/transfer/history`) |
| `fraud_history.detected_at` | 최신순 정렬 및 페이지네이션 최적화 |
| `trusted_receiver.sender_id` | 화이트리스트 조회 (`existsBySenderIdAndReceiverId`) |

---

## 디렉터리 구조

```
fds/
├── src/main/java/com/seongho/fds/
│   ├── FdsApplication.java
│   ├── auth/                            # 회원가입, 로그인, 사용자와 권한
│   │   ├── controller/
│   │   ├── service/
│   │   ├── dto/
│   │   ├── domain/
│   │   └── repository/
│   ├── transaction/                     # 실시간 거래 탐지와 Kafka 이벤트
│   │   ├── controller/
│   │   ├── service/
│   │   ├── dto/
│   │   ├── producer/
│   │   └── consumer/
│   ├── fraudhistory/                    # 차단 거래 이력 저장과 조회
│   │   ├── controller/
│   │   ├── service/
│   │   ├── domain/
│   │   └── repository/
│   ├── whitelist/                       # 신뢰 수취인 관리
│   │   ├── controller/
│   │   ├── service/
│   │   ├── dto/
│   │   ├── domain/
│   │   └── repository/
│   └── global/                          # 여러 기능에서 공유하는 인프라
│       ├── config/                      # Drools, Kafka, Swagger, 환경 설정
│       ├── security/                    # Spring Security와 JWT
│       ├── redis/                       # Redis 공통 연산
│       └── exception/                   # 전역 예외 처리
├── src/main/resources/
│   ├── application.yml                  # 서버 설정 + 룰 임계값
│   └── rules/
│       └── transfer-rules.drl           # Drools 탐지 룰 정의 (6개)
├── src/test/java/com/seongho/fds/
│   ├── auth/service/
│   ├── transaction/service/             # 룰 엔진 6개 룰 검증
│   ├── transaction/consumer/
│   ├── transaction/producer/
│   ├── fraudhistory/service/
│   ├── whitelist/service/
│   └── global/redis/
├── docker-compose.yml                   # Zookeeper, Kafka, MySQL, Redis
├── .env                                 # 환경 변수 (DB / Kafka / JWT)
├── .env.example                         # 환경 변수 템플릿
└── build.gradle
```

---

## 실행 방법

### 사전 요구사항

- Java 17+
- Docker & Docker Compose

### 1. 환경 변수 설정

```bash
cp .env.example .env
```

`.env` 파일:
```env
DB_HOST=localhost
DB_PORT=3306
DB_NAME=fds
DB_USERNAME=root
DB_PASSWORD=your_password

KAFKA_BOOTSTRAP_SERVERS=localhost:9092

JWT_SECRET=your-256-bit-secret-key-must-be-at-least-32-characters
```

### 2. 인프라 실행

```bash
docker-compose up -d
```

| 서비스 | 포트 |
|--------|------|
| MySQL | 3306 |
| Redis | 6379 |
| Kafka | 9092 |
| Zookeeper | 2181 |

### 3. 서버 실행

```bash
./gradlew bootRun
```

### 4. 확인

| 항목 | URL |
|------|-----|
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| 프론트엔드 (별도 실행 필요) | `http://localhost:5173` |

### 프론트엔드 실행

```bash
cd ../frontend
npm install
npm run dev
```

---

## 테스트

```bash
./gradlew test
```

### 테스트 전략

> `FdsServiceTest`는 `.drl` 파일을 **실제로 로드**하여 룰 엔진 자체를 검증합니다.
> Redis · DB 의존성은 Mockito로 대체하여 외부 인프라 없이 빠르게 실행됩니다.

### 테스트 범위

| 테스트 클래스 | 테스트 대상 | 주요 케이스 |
|--------------|------------|-------------|
| `FdsServiceTest` | Drools 룰 엔진 6개 룰 | 정상 / 경계값 / 복합 룰 동시 발동 |
| `AuthServiceTest` | 인증 로직 | 가입 / 로그인 / 로그아웃 / 예외 |
| `FraudHistoryServiceTest` | 이력 저장·조회 | reasons 직렬화 / 유저 필터 |
| `WhitelistServiceTest` | 화이트리스트 CRUD | 중복 방지 / 방향 독립성 |
| `TransactionConsumerTest` | Kafka Consumer | BLOCKED만 저장 / APPROVED 무시 |
| `TransactionProducerTest` | Kafka Producer | 토픽 · 키 · 이벤트 검증 |
| `RedisUtilTest` | Redis 연산 | TTL 관리 / 크래시 복구 / 평균 계산 |
