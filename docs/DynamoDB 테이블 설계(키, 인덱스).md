### 0) 설계 목표

이 테이블은 아래 UX/운영 요구를 만족한다.

**사용자(프론트)**

- `202 Accepted` 이후 **requestId로 상태 조회**
- **내 신청 내역**: 로그인 사용자 기준 “최근 신청 순” 리스트
- **요청 상세**: 상태 타임라인/결과/지연시간(queuedAt/startedAt/finishedAt)

**운영자(어드민)**

- 이벤트별 요청 조회(요청량/성공/실패/DLQ 후보)
- requestId로 즉시 검색, 상태/실패 사유 확인
- (선택) 선착순 정원 N을 **원자적으로 확정**

**GSI1/2는 각각 ‘내 신청 내역’, ‘이벤트별 운영 조회’ 화면에 매핑된다**

---

## 1) 단일 테이블

### Table: `AsyncEventTable`

- **PK**: `PK` (String)
- **SK**: `SK` (String)
- Billing: `PAY_PER_REQUEST`
- TTL attribute(옵션): `ttl` (Number, epoch seconds)

원칙: **“한 테이블에 여러 엔티티를 넣되, GSI는 Request Item에만 둔다.”**

(IdempotencyLock/Capacity/Log/Config가 인덱스에 들어가면 비용/쿼리 품질/운영이 망가짐)

---

## 2) Item Types (같은 테이블에 공존)

## A. Request Item (핵심 조회 대상)

### Key

- **PK**: `REQ#<requestId>`
- **SK**: `META`
- Request는 항상 PK=REQ#requestId, SK=META로 고정

### 필드(표준)

**entity**

- `entityType = "REQUEST"`

**식별/분류**

- `requestId`
- `eventId`
- `userId`
- `eventType`: `FIRST_COME | LOTTERY`

**상태(백엔드 고정)**

- `status`: `RECEIVED | QUEUED | PROCESSING | SUCCEEDED | REJECTED | FAILED_FINAL`

> 주의: LOTTERY의 UI 단계(COLLECTING/DRAWING)는 status로 추가하지 않음
> 
> 
> 대신 아래 “UI 보조 필드”로 표현한다.
> 

**UI 보조 필드(선택 but 추천)**

- `uiPhase` (LOTTERY에서만): `COLLECTING | DRAWING | ANNOUNCED`
    - 이 값은 “내부 처리 상태”가 아니라 **프론트 문구용**이다.
- `lotteryCutoffAt` (마감 시각)
- `announcedAt` (결과 발표 시각, 있으면 UI에서 “결과 발표” 표시 쉬움)

**결과**

- `uiResult`: `PENDING | SUCCESS | REJECTED | FAILED` ← 프론트에 바로 쓰기 좋은 값
- `resultCode`(운영/분석용, enum 추천):
    - `SUCCESS`
    - `REJECTED_CAPACITY`
    - `REJECTED_DUPLICATE`
    - `REJECTED_LOTTERY_LOSE`
    - `FAILED_INGEST_ENQUEUE`
    - `FAILED_WORKER`
    - (필요 시) `FAILED_VALIDATION`, `FAILED_SERDE` 등

**timestamps (포맷 고정: epochMillis 권장)**

- `requestedAt`
- `queuedAt` “공정성 기준 시각” (SQS enqueue 성공 순간만 기록)
- `startedAt`
- `finishedAt`

**실패 정보(실패일 때만)**

- `failureClass`: `RETRYABLE | NON_RETRYABLE`
- `errorCode`: 짧은 코드 (예: `VALIDATION_FAILED`, `DDB_CONDITIONAL_FAILED`, `SERDE_ERROR`)
- `errorMessage`: **짧게(예: 256자 제한)** 운영자 힌트용

**멱등 연결**

- `idempotencyKey = IDEMP#<eventId>#<userId>`

### GSI 속성 (Request Item에만)

**GSI1 (내 신청 내역)**

- `GSI1PK`: `USER#<userId>`
- `GSI1SK`: `QAT#<queuedAt>#REQ#<requestId>` (queuedAt 기반)
    - *queuedAt이 아직 없으면(극초기) requestedAt을 임시로 쓰고, queuedAt 확정 시 갱신하는 방식도 가능하지만,*
        
        MVP에선 “queuedAt 기록 직후부터 리스트에 뜬다”로 단순화하는 걸 추천.
        

**GSI2 (이벤트별 운영 조회)**

- `GSI2PK`: `EVENT#<eventId>`
- `GSI2SK`: `QAT#<queuedAt>#REQ#<requestId>` (queuedAt 기반)

**왜 queuedAt 기반이 더 좋은가?**

- 너의 공정성 기준이 **queuedAt(큐 정상 접수 시점)** 이고,
- 운영도 “이벤트 폭주 때 실제 줄 선 순서”를 보고 싶어함
- 그래서 **정렬/리스트/분석 축이 전부 일관**해짐

> 참고: status로 DB에서 바로 필터링(contains)은 Query에서 안 됨 → Query 후 서버에서 status 필터/집계가 기본.
> 
> 
> “FAILED_FINAL만 초고속 조회”가 필요해지면 그때 GSI 추가가 맞음.
> 

---

## B. IdempotencyLock/ Idempotency Item (중복 차단 전용)

### Key

- **PK**: `IDEMP#<eventId>#<userId>`
- **SK**: `LOCK`

### 필드

- `entityType = "IDEMPOTENCY"`
- `requestId` : 최초 생성된 requestId
- `createdAt`
- (옵션) `ttl` : 이벤트 종료 후 +N일 (예: 7일)

### 동작 규칙(고정)

- Ingest는 **Conditional Put**으로 원자적 중복 차단
    - `ConditionExpression:` attribute_not_exists(PK) AND attribute_not_exists(SK)
- 이미 존재하면 저장된 `requestId` 반환 → 프론트 UX의 “이미 신청됨/버튼 비활성”과 1:1 매핑

이 구조는 **at-least-once + 사용자 중복 클릭 + 봇 재시도** 환경에서 제일 안전함

---

## C. Capacity Item (FIRST_COME 정원 원자성)

### Key

- **PK**: `EVENT#<eventId>`
- **SK**: `CAPACITY`

### 필드

- `entityType = "CAPACITY"`
- `capacityTotal`
- `capacityRemaining`
- `updatedAt`

### Worker 원자 처리(고정)

- `capacityRemaining > 0` 일 때만 감소 (조건부 업데이트)
    - 성공: remaining--, Request → `SUCCEEDED`, `resultCode=SUCCESS`
    - 실패: 조건 불일치면 Request → `REJECTED`, `resultCode=REJECTED_CAPACITY`
- LOTTERY에서는 capacity는 쓰지 않고 마감 시간 기반

“멀티 워커여도 딱 N명만 성공” 보장하는 가장 단순/강력 패턴

---

## D. StatusLog Item (선택: 운영 타임라인/감사 로그)

> 프론트 타임라인은 RequestItem의 status만 폴링해도 표현 가능.
> 
> 
> 운영에서 “언제 어떤 전이가 있었는지”까지 남기고 싶으면 사용.
> 

### Key

- **PK**: `REQ#<requestId>`
- **SK**: `LOG#<occurredAt>` (epochMillis 권장)

### 필드

- `entityType = "STATUS_LOG"`
- `fromStatus, toStatus, occurredAt`
- `message`(선택)
- (옵션) `ttl` (예: 30일)

규칙: **StatusLog에는 GSI 속성 절대 금지**

---

## (선택) E. EventConfig Item (운영 편의용, 추천)

> “정원/마감/타입/발표시각” 같은 이벤트 메타를 한 곳에서 관리하면
> 
> 
> LOTTERY UI 단계 문구/마감 표시가 쉬워짐.
> 
- PK: `EVENT#<eventId>`
- SK: `CONFIG`
- fields: `eventType`, `capacityTotal`, `lotteryCutoffAt`, `announcedAt`, `status(Open/Closed/Announced)` 등

---

## 3) GSI 최종 정리 (2개만 유지)

### GSI1: 유저 기준 “내 신청 내역”

- `GSI1PK = USER#<userId>`
- `GSI1SK = QAT#<queuedAt>#REQ#<requestId>`
- 최신순:
    - 서버 Query에서 `ScanIndexForward=false`로 역순 가능 (추천)
    - MVP에서 reverse도 가능

### GSI2: 이벤트 기준 “운영자 조회”

- `GSI2PK = EVENT#<eventId>`
- `GSI2SK = QAT#<queuedAt>#ST#<status>#REQ#<requestId>`

---

## 4) 쿼리 패턴 (화면 매핑)

1. **요청 상세(requestId)**
- `GetItem(PK=REQ#<requestId>, SK=META)`
1. **내 신청 내역(로그인 사용자)**
- `Query GSI1 where GSI1PK=USER#<userId> limit 20`
1. **이벤트별 요청 조회(관리자)**
- `Query GSI2 where GSI2PK=EVENT#<eventId> limit N`
- 상태/실패율은 서버에서 `status/resultCode` 집계
1. **선착순 정원 확정**
- `UpdateItem` on Capacity with condition `capacityRemaining > 0`

---

## 5) 상태 전이 규칙(공정성 핵심)

- Ingest:
    - Request 생성: `RECEIVED + requestedAt`
    - **SQS enqueue 성공 후에만** `QUEUED + queuedAt` 확정 (공정성 기준 시각)
- Worker:
    - 처리 시작: `PROCESSING + startedAt`
    - 완료: `SUCCEEDED | REJECTED | FAILED_FINAL + finishedAt`
- 상태 변경 시: **RequestItem의 status + (GSI2SK 포함) 동시 갱신**

 기획의 “공정성 기준 = queuedAt”을 코드 규칙으로 고정하는 포인트

---

## 6) TTL 정책(권장)

- RequestItem: 예) **90일**
- IdempotencyLock: 이벤트 종료 후 **+7일**
- StatusLog: **30일**
- (EventConfig는 TTL 없이 유지하거나, 이벤트 종료 후 별도 정리)

---

## 7) CreateTable (CLI 예시)

```bash
aws dynamodb create-table \
  --table-name AsyncEventTable \
  --attribute-definitions \
    AttributeName=PK,AttributeType=S \
    AttributeName=SK,AttributeType=S \
    AttributeName=GSI1PK,AttributeType=S \
    AttributeName=GSI1SK,AttributeType=S \
    AttributeName=GSI2PK,AttributeType=S \
    AttributeName=GSI2SK,AttributeType=S \
  --key-schema \
    AttributeName=PK,KeyType=HASH \
    AttributeName=SK,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --global-secondary-indexes '[
    {
      "IndexName": "GSI1",
      "KeySchema": [
        {"AttributeName":"GSI1PK","KeyType":"HASH"},
        {"AttributeName":"GSI1SK","KeyType":"RANGE"}
      ],
      "Projection": {"ProjectionType":"ALL"}
    },
    {
      "IndexName": "GSI2",
      "KeySchema": [
        {"AttributeName":"GSI2PK","KeyType":"HASH"},
        {"AttributeName":"GSI2SK","KeyType":"RANGE"}
      ],
      "Projection": {"ProjectionType":"ALL"}
    }
  ]'

```

TTL 켜기(옵션)

```bash
aws dynamodb update-time-to-live \
  --table-name AsyncEventTable \
  --time-to-live-specification"Enabled=true,AttributeName=ttl"

```

---

## 8) “팀 고정” 템플릿(실수 방지)

### Ingest: Request 생성

- PK=`REQ#${requestId}`, SK=`META`
- status=`RECEIVED`, requestedAt=now
- GSI1/GSI2는 **RequestItem에만** 항상 포함

### Ingest: enqueue 성공 후 QUEUED 전이(queuedAt 확정)

- status=`QUEUED`, queuedAt=now
- GSI1SK/GSI2SK도 queuedAt 기반으로 갱신

### Ingest: IdempotencyLock 생성

- PK=`IDEMP#${eventId}#${userId}`, SK=`LOCK`
- Conditional Put(없을 때만)
- 있으면 저장된 requestId 반환

### Worker: 상태 전이

- PROCESSING + startedAt
- SUCCEEDED/REJECTED/FAILED_FINAL + finishedAt
- status 변경 시 GSI2SK도 갱신
