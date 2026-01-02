# Access Pattern 매핑 표

DynamoDB 설계의 핵심은 “이 화면이 이 키로 조회된다”를 명확히 고정하는 것이다.  
본 문서는 화면·API·비동기 처리 단계별 접근 패턴을 DynamoDB 단일 테이블 설계와 1:1로 매핑한 기준 문서다.

- 각 항목은 하나의 화면/기능 또는 처리 단계가 어떤 Item/Index를 어떤 Key로 접근하는지를 정의한다.
- 본 문서에 정의된 매핑은 설계, 구현, 코드 리뷰, 테스트, 운영 판단의 기준으로 사용한다.

---

## A) User API (프론트)

| 화면/기능 | API 예시 | DynamoDB Operation | 대상(Item / Index) | Key / 조건(요지) | 비고 (설계 의도 / 주의점) |
| --- | --- | --- | --- | --- | --- |
| 참여하기 (접수) | `POST /events/{eventId}/participations` | PutItem (Conditional) | IdempotencyLock | `PK = IDEMP#eventId#userId`<br/>`SK = LOCK`<br/>Condition: attribute_not_exists(PK) AND attribute_not_exists(SK) | 중복 신청 차단의 시작점<br/>성공 시 최초 requestId 확정 |
| 참여하기 (중복 클릭) | (동일 API) | GetItem | IdempotencyLock | 동일 PK / SK 조회 | 기존 requestId 반환 |
| 요청 레코드 생성 | 내부 단계 | PutItem | Request Item | `PK = REQ#requestId`<br/>`SK = META` | `status = RECEIVED`<br/>`requestedAt = now` |
| 큐잉 성공 반영 | 내부 단계 | UpdateItem (Conditional) | Request Item | `status = QUEUED`<br/>`queuedAt = now`<br/>Condition: `status = RECEIVED` | 공정성 기준 시각 고정<br/>SQS enqueue 성공 시점만 기록 |
| 요청 상세 조회 | `GET /requests/{requestId}` | GetItem | Request Item | `PK = REQ#requestId`<br/>`SK = META` | 요청 단건 조회 |
| 내 신청 내역 | `GET /me/participations?limit=20` | Query | GSI1 | `GSI1PK = USER#userId`<br/>`Limit = 20`<br/>`ScanIndexForward = false` | 최신순 고정 |

---

## B) Worker (비동기 처리)

| 처리 단계 | 트리거 | DynamoDB Operation | 대상(Item) | Key / 조건(요지) | 비고 (설계 의도 / 주의점) |
| --- | --- | --- | --- | --- | --- |
| 처리 시작 | SQS receive | UpdateItem (Conditional) | Request Item | `status = PROCESSING`<br/>`startedAt = now`<br/>Condition: `status = QUEUED` | at-least-once 환경 대응 (멱등 필수) |
| 선착순 정원 확정 | FIRST_COME 처리 중 | UpdateItem (Conditional) | EventCapacity | `PK = EVENT#eventId`<br/>`SK = CAPACITY`<br/>Condition: `capacityRemaining > 0` | 원자적 감소로 정확히 N명만 성공 |
| 선착순 성공 | capacity 성공 | UpdateItem | Request Item | `status = SUCCEEDED`<br/>`finishedAt = now`<br/>`uiResult = SUCCESS` |  |
| 선착순 정원 초과 | capacity 실패 | UpdateItem | Request Item | `status = REJECTED`<br/>`finishedAt = now`<br/>`uiResult = REJECTED_CAPACITY` | 결과 구분은 uiResult 기준 |
| 추첨 처리 | LOTTERY | UpdateItem | Request Item | `status = SUCCEEDED / REJECTED`<br/>`finishedAt = now`<br/>`uiResult = LOTTERY_WIN / LOTTERY_LOSE` | LOTTERY는 Capacity 미사용 |
| 큐잉 실패 | SQS send 실패 | UpdateItem | Request Item | `status = FAILED_FINAL`<br/>`errorCode = FAILED_INGEST_ENQUEUE`<br/>`finishedAt = now` | 재시도 정책은 SQS 레벨 |
| 최종 실패 | 예외/비정상 | UpdateItem | Request Item | `status = FAILED_FINAL`<br/>`failureClass`<br/>`errorCode`<br/>`errorMessage`<br/>`finishedAt = now` |  |
| 상태 감사 로그 (선택) | 상태 변경 시 | PutItem | RequestStatusLog | `PK = REQ#requestId`<br/>`SK = LOG#occurredAt` | GSI 미사용 |

---

## C) Admin (운영자)

| 화면/기능 | API 예시 | DynamoDB Operation | 대상(Index / Item) | Key / 조건(요지) | 비고 (설계 의도 / 주의점) |
| --- | --- | --- | --- | --- | --- |
| 이벤트별 요청 목록 | `GET /admin/events/{eventId}/requests` | Query | GSI2 | `GSI2PK = EVENT#eventId` | 상태별 집계는 서버에서 처리 |
| 이벤트별 최근 N건 | (동일) | Query | GSI2 | `Limit = N`<br/>`ScanIndexForward = false` | 최신순 조회 |
| requestId 검색 | `GET /admin/requests/{requestId}` | GetItem | Request Item | `PK = REQ#requestId`<br/>`SK = META` | CS 대응용 단건 조회 |
| 상태 로그 조회 | `GET /admin/requests/{requestId}/logs` | Query | RequestStatusLog | `PK = REQ#requestId`<br/>`begins_with(SK, "LOG#")` | 로그 활성화 시만 사용 |

---

## 3) GSI2 설계 규칙

### 3.1 기본 원칙

- GSI2는 이벤트별 요청의 접수 순서 조회 전용 인덱스다.
- Sort Key는 요청이 큐에 정상 접수된 시점(`queuedAt`)을 기준으로 생성하며, 생성 후 변경하지 않는다.

GSI2SK = QAT#{queuedAt}#REQ#{requestId}


### 3.2 status를 GSI2SK에 포함하지 않는 이유

GSI2의 목적은 접수 순서를 안정적으로 제공하는 것이다.  
`queuedAt`과 `requestId`는 불변 값이지만, `status`는 요청 처리 과정에서 여러 번 변경된다.

| 구분 | queuedAt | status |
| --- | --- | --- |
| 성격 | 불변 | 가변 |
| 의미 | 순서 | 처리 상태 |
| 책임 | 공정성 | 해석 |

status를 Sort Key에 포함하면 인덱스 재정렬, 순서 왜곡, 불필요한 쓰기 비용 증가가 발생할 수 있다.  
따라서 status는 Request Item의 Attribute로만 관리한다.

### 3.3 운영자 화면에서의 상태 해석

운영자 화면에서는 다음과 같이 역할을 분리한다.

- DynamoDB(GSI2): 접수 순서 제공
- 서버 애플리케이션: status 기준 집계 및 필터링

GSI2 Query 결과
→ Request Item 목록
→ 서버에서 status 기준으로 그룹핑 및 집계

---

## 실수하기 쉬운 포인트

1. IdempotencyLock 없이 Request를 먼저 생성하지 않는다.
2. queuedAt은 SQS enqueue 성공 이후에만 기록한다.
3. 상태 변경 시 GSI2SK 또는 queuedAt을 수정하지 않는다.
4. Worker 상태 전이는 항상 조건부로 수행한다.
5. GSI 속성은 Request Item에만 존재하도록 강제한다.
