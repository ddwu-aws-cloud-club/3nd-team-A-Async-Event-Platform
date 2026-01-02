# Event-driven Asynchronous Processing Architecture

> 요청을 즉시 처리하지 않고 이벤트로 수집한 뒤,
비동기 워커가 공정하고 안정적으로 처리하는 아키텍처
> 

---

## 1. Overview

본 문서는 **트래픽이 폭증하는 선착순·응모 이벤트 환경**에서

요청을 동기 처리하지 않고 **이벤트 기반 비동기 처리 구조**로 전환한

시스템 아키텍처를 설명한다.

<img width="1536" height="1024" alt="Image" src="https://github.com/user-attachments/assets/e618de17-8019-4638-842b-f037a889550b" />

### 설계 목표

- 순간 트래픽 폭주 상황에서도 **접수 서버 안정성 유지**
- `(eventId, userId)` 기준 **중복 참여 방지 (Idempotency)**
- 비동기 처리 과정의 **상태 추적 가능성 확보**
- 실패 요청의 **격리(DLQ) 및 운영 복구 가능**
- 처리 결과를 **운영·분석 데이터로 환류**

---

## 2. High-level Architecture

### 전체 처리 흐름 (Logical View)

<img width="5660" height="980" alt="Image" src="https://github.com/user-attachments/assets/37057512-9273-45c9-a583-0abf4d9e9e9f" />

> 핵심 설계 포인트
> 
> - Ingest는 *처리 서버가 아닌* **요청 접수 게이트**
> - DynamoDB는 요청 상태의 **Single Source of Truth**
> - SQS는 폭주 트래픽을 흡수하는 **완충 버퍼**
> - Worker는 **비동기 처리 책임만 집중**

---

## 3. End-to-End Data Flow

### Step 1. Client → Ingest (요청 접수)

- 사용자는 로그인 기반으로 이벤트 참여 요청을 전송한다.
- AWS WAF는 비정상·자동화 트래픽을 차단한다.
- API Gateway는 인증 및 기본 레이트 리밋을 적용한다.

---

### Step 2. Ingest: 멱등성 확정 & 202 응답

- Ingest는 요청을 **즉시 처리하지 않는다**.
- DynamoDB에 `(eventId, userId)` 기준 **Conditional Write** 수행
    - 최초 요청: `RECEIVED` 상태 기록
    - 중복 요청: 기존 `requestId` 반환
- SQS Main Queue에 메시지를 enqueue
- enqueue 성공 시 상태를 `QUEUED`로 전이
- Client에는 **`202 Accepted + requestId`** 즉시 반환

---

### Step 3. Worker: 비동기 처리

- Worker는 SQS를 **Long Polling** 방식으로 소비한다.
- 처리 시작 시 DynamoDB 상태를 `PROCESSING`으로 조건부 전이한다.
- 처리 결과에 따라 상태를 최종 전이한다.
    - `SUCCEEDED`
    - `REJECTED`
    - `FAILED_FINAL`

---

### Step 4. 실패 처리 & DLQ

- 메시지가 반복 실패하여 `maxReceiveCount` 초과 시 DLQ로 이동한다.
- DLQ 메시지는 메인 처리 흐름에서 격리되어 전체 안정성을 보호한다.
- 운영자는 분석 후 **선택적으로 Redrive**를 수행할 수 있다.

---

### Step 5. 분석 파이프라인

- Worker는 처리 결과를 **도메인 이벤트**로 발행한다.
- EventBridge → Firehose → S3(Data Lake)로 적재된다.
- Athena를 통해 다음 지표를 분석한다.
    - p95 처리 지연
    - 성공 / 실패 비율
    - DLQ 발생률

---

## 4. Component Responsibilities

### Client

- 이벤트 참여 요청 전송
- `requestId` 기반 상태 조회

### AWS WAF

- 비정상 트래픽 차단
- 공정성 및 비용 보호

### API Gateway

- 인증 및 레이트 리밋
- 단일 진입점 제공

### Spring Ingest API

- 요청 검증 및 사용자 식별
- `(eventId, userId)` 멱등성 확정
- SQS enqueue
- 202 응답 반환

### DynamoDB RequestTable

- 요청 상태 머신 관리
- 멱등성 키 및 상태 전이 관리
- 시스템의 단일 진실 원천

### SQS Main Queue

- 트래픽 버퍼링
- 처리율 안정화

### Spring Worker

- 비동기 이벤트 처리 엔진
- 상태 전이 및 실패 분류

### SQS DLQ

- 독성 메시지 격리
- 운영 복구 지점 제공

### EventBridge / Firehose / S3 / Athena

- 처리 결과를 분석 가능한 이벤트 데이터로 전환
- 운영 지표 환류

### CloudWatch

- 로그·메트릭·알람 통합 관측 허브

---

## 5. One-line Summary 

> Event-driven Async Architecture
> 
> 
> 요청을 즉시 처리하지 않고 큐에 적재한 뒤,
> 
> 비동기 워커가 공정하고 안정적으로 처리하는 구조
<img width="555" height="153" alt="Image" src="https://github.com/user-attachments/assets/7bd2d5e6-fdf1-4d47-9168-43bc4469c184" />
