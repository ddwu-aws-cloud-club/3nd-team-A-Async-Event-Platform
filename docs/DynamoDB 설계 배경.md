**Event-driven Async Platform – DynamoDB Single Table Design**

---

### 설계 배경

- 선착순/응모 이벤트는 **트래픽 스파이크 + 중복 요청 + 공정성 요구**가 동시에 존재
- JOIN 기반 RDB 구조는 병목/확장/공정성 제어에 불리
- 따라서 **조회 패턴 중심의 DynamoDB Single Table** 구조 채택

---

### 테이블 개요

**AsyncEventTable (단일 테이블)**

| 구분 | 내용 |
| --- | --- |
| Partition Key | PK |
| Sort Key | SK |
| GSI | GSI1 (User 기준), GSI2 (Event 기준) |
| Billing | PAY_PER_REQUEST |
| TTL | 옵션 (이벤트성 데이터 자동 만료) |

---

### 논리 엔티티 (Item Types)

### 1) Request (핵심)

- 사용자 요청 1건 = 1 Request Item
- 상태 머신:
    
    `RECEIVED → QUEUED → PROCESSING → SUCCEEDED / REJECTED / FAILED_FINAL`
    
- requestId 기반 상태 조회, 사용자/이벤트 기준 조회 지원

### 2) IdempotencyLock

- `(eventId, userId)` 기준 **원자적 중복 차단**
- 중복 요청 시 **같은 requestId 재사용**

### 3) EventCapacity (FIRST_COME 전용)

- 정원 N을 **조건부 업데이트**로 원자적 확정
- 멀티 워커 환경에서도 정확히 N명만 성공

### 4) StatusLog (옵션)

- 상태 전이 이력 관리 (운영/감사 목적)

---

### 핵심 설계 원칙

- **GSIs는 Request Item에만 존재**
- Dedup / Capacity / Log는 인덱스에 절대 포함하지 않음
- 모든 조회는 **GetItem 또는 Query 1회**로 끝나도록 설계

---

### 공정성 기준 고정

- 선착순 공정성 기준 = **queuedAt (SQS 정상 적재 시점)**
- 처리 완료 순서 ≠ 당첨 순서
