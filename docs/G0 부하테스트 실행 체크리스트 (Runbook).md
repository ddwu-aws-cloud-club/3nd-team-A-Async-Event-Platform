## 0. 사전 준비 (필수)

### 0-1. 환경 변수/토큰 준비

- [ ]  `ACCESS_TOKEN` 1개 발급해두기 (G0는 1개로 충분)
    - [ ]  **k6 스크립트 확인**: 매 요청마다 **랜덤 `userId` 생성** 및 **토큰 발급** 로직이 포함되어 있는지 확인 (단일 토큰 재사용 금지 -> 멱등성 필터링 방지)
- [ ]  `BASE_URL` 확인 (`http://localhost:8080` 등)

### 0-2. 대상 이벤트/테이블 픽스처 확인

- [ ]  **EVENT_ID(EVT-009)**에 대해 `CapacityItem` 존재 확인
    - PK = `EVENT#EVT-009`, SK = `CAPACITY`
- [ ]  `capacityRemaining`이 **1 이상**인지 확인 (초기값: 100 권장)
    - 0이면 전부 `REJECTED`로만 끝나는 건 “정상”이지만, **SUCCEEDED 샘플을 보기 어려움**
- [ ]  `AsyncEventTable` / SQS Main Queue / DLQ가 올바른 계정/리전에 있는지 확인

### 0-3. Worker 상태 확인 (지금은 꺼둘 것)

- Tip: DynamoDB 콘솔에서 해당 `CapacityItem`을 찾아두고, 테스트 중간중간 '새로고침'을 눌러 숫자가 줄어드는지 눈으로 확인하면 더 확실
- [ ]  worker 프로세스 종료 상태 확인 (켜져 있으면 `Ctrl+C`)
- [ ]  SQS 큐가 비어있는지(0) 확인 (이전 테스트 잔여물 제거) ← 중

---

## 1. ingest-api 기동

### 실행

- [ ]  `./gradlew :ingest-api:bootRun`

### 확인 (1회만)

- [ ]  Postman으로 `POST /auth/login` → 토큰 발급 OK
- [ ]  Postman으로 `POST /events/EVT-009/participations`
    - Header: `Authorization: Bearer <TOKEN>`
    - 기대값: **202 + requestId**

### 합격 조건

- [ ]  202가 즉시 떨어짐 (타임아웃/5xx 없음)
- [ ]  응답 바디에 `requestId` 존재
- [ ]  응답 바디에 `status` 필드가 "없어야" 함

---

## 2. Worker는 일부러 끄기 (적재 증명)

- [ ]  Worker는 절대 켜지 않음
- [ ]  목적: **큐 적재(QueueDepth 증가)** 증명

---

## 3. SQS 콘솔 열어두기 (관찰 지표 1개만)

### AWS 콘솔 → SQS Main Queue

- [ ]  **ApproximateNumberOfMessagesVisible** 확인

### 합격 조건

- [ ]  k6 실행 직후 Visible 메시지가 **증가**해야 함
    
    (worker가 꺼져 있으니 “쌓이는 게 정상”)
    

---

## 4. k6 실행 (스파이크 주입)

### 실행 커맨드 (예시)

```bash
k6 run `
  -e BASE_URL=http://localhost:8080 `
  -e EVENT_ID=EVT-009 `
  -e ACCESS_TOKEN=eyJhbGciOi... `
  -e VUS=300 `
  -e DURATION=60s `
  k6/g0_spike_participation.js

```

### 즉시 할 일 (캡처 2장)

- [ ]  **k6 콘솔 결과 캡처 1장**
- [ ]  **SQS QueueDepth 증가 화면 캡처 1장**
    - `ApproximateNumberOfMessagesVisible` 상승 증거

### 합격 조건 (k6)

- [ ]  `http_req_failed < 1%`
- [ ]  `check` 실패가 거의 없음 (202 + requestId)

현재 **`k6/g0_spike_participation.js`** 스크립트가 매 요청마다 **새로운(랜덤) 유저 ID**를 사용하는지 꼭 확인

---

## 5. Worker 켜기 (소비/회복 증명)

### 실행 전 확인

- [ ]  **DynamoDB 확인**: **`capacityRemaining`**이 **초기값(100) 그대로인지** 확인 (Worker가 진짜 꺼져있었다면 줄어들지 않았어야 함)

### 실행

- [ ]  `./gradlew :worker:bootRun`

### 관찰 (캡처 1장)

- [ ]  SQS에서 `ApproximateNumberOfMessagesVisible`이 **감소**하는 화면 캡처 1장
- [ ]  **DynamoDB 확인**: **`capacityRemaining`**이 **빠르게 0(또는 감소된 값)으로 변하는지** 확인

### 합격 조건

- [ ]  QueueDepth가 시간이 지나면서 내려감
- [ ]  worker 로그에서 처리 시작/완료 로그가 지속 발생

---

## 6. requestId 최종 상태 “샘플” 확인 (전체 완료 기다릴 필요 없음)

### 부하 상황에서의 샘플 확인 목적
    
    부하 테스트(k6)로 수백, 수천 건의 요청을 쏟아붓는 동안 서버는 정신이 없을 겁니다. 이때 **"내 소중한 요청 하나가 이 난리통 속에서도 잃어버리지 않고 끝까지 잘 처리되는가?"**를 증명하는 것이 이 테스트의 핵심 목표입니다.
    
    - **단순히 트래픽만 버티는 게 아니라(202 응답)**,
    - **실제로 그 요청이 큐를 타고 → Worker를 거쳐 → 최종 결과(성공/실패)까지 가는지**를 눈으로 확인하고 싶은 것입니다.

### 샘플 확보

- [ ]  Postman으로 k6 실행 **직전**에 `POST /events/EVT-009/participations` 3~5회 호출
- [ ]  각 응답의 `requestId` 저장

### 대기 (k6 + Worker 처리)

- [ ]  k6 테스트가 완전히 끝나고, SQS 큐가 0이 될 때까지 대기
    - 내 요청들이 수백 개의 부하 요청들과 함께 처리될 시간을 줍니다.

### 상태 조회 및 검증

- [ ]  저장해둔 **`requestId`**로 **`GET /requests/{requestId}`** 호출
- [ ]  status가 아래 중 하나면 OK
    - **`SUCCEEDED`** (성공)
    - **`REJECTED`** (매진으로 인한 거절)
    - **`FAILED_FINAL`** (기타 오류)
    - *주의: **`QUEUED`**나 **`PROCESSING`** 상태라면 아직 처리가 안 끝난 것이니 조금 더 대기*

G0에서는 “전부 완료”가 목표가 아님

→ **최종 상태 도달 샘플만 있으면 충분**

### 캡처 (3~5개)

- [ ]  최종 상태 응답 캡처 3~5개

### 합격 조건

- [ ]  전이 완료: 샘플 요청들이 중간에 유실되지 않고 최종 상태에 도달함
- [ ]  데이터 정합성: **`status`**가 역행하거나 이상한 값으로 변하지 않음 (예: PROCESSING → QUEUED 같은 이상 전이 X)

---

# G0 부하테스트 성공 판정

아래 3개가 **증거(스크린샷)로 남아있으면 통과**

1. **202 유지**
    - k6 결과 + Postman 202 캡처
2. **QueueDepth 증가 → 감소**
    - Worker OFF 상태에서 증가
    - Worker ON 후 감소
3. **requestId 최종 상태 샘플 존재**
    - SUCCEEDED / REJECTED / FAILED_FINAL

---

# 실패 시 빠른 원인 체크

## A) k6에서 401이 많이 뜬다

- [ ]  `ACCESS_TOKEN` 누락/오타
- [ ]  Header가 `Authorization: Bearer <TOKEN>` 형식인지 확인
- [ ]  SecurityConfig에서 `/events/**`가 authenticated로 막혀있는지 확인(정상)

## B) QueueDepth가 안 오른다

- [ ]  Worker가 켜져있어서 바로 소비 중
- [ ]  ingest에서 enqueue 실패 → `FAILED_INGEST_ENQUEUE`로 떨어지고 있을 수 있음
- [ ]  queueUrl 설정이 다른 큐를 보고 있을 수 있음

## C) Worker 켰는데 QueueDepth가 안 줄어든다

- [ ]  Visibility Timeout 너무 짧음 (worker 처리 시간보다 짧으면 재시도 루프)
- [ ]  worker가 메시지 delete를 안 하는 상태(ack 정책 버그)
- [ ]  DLQ로 빠지고 있는지 확인 (ReceiveCount 초과)
- [ ]  

## D) (추가) SQS는 쌓였는데 DynamoDB 상태가 안 바뀐다

- [ ]  Worker 로그에 에러(Exception)가 뜨는지 확인
- [ ]  **`ParticipationMessage`** 스키마와 Worker의 DTO가 일치하는지 확인 (JSON 파싱 에러 가능성)

---

# 결과물 정리

- `k6/g0_spike_participation.js`
- `docs/g0-load-test.md`
    - k6 실행 로그 캡처
    - SQS 증가 캡처
    - SQS 감소 캡처
    - 최종 상태 응답 캡처 3~5개
    - 한 줄 결론(“부하를 에러가 아니라 큐로 흡수”)
