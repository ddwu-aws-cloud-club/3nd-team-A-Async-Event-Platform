import React from 'react';
import { useParams } from 'react-router-dom';

const Result = () => {
  const { requestId } = useParams(); 
  // 1. 백엔드 Request Item과 1:1 매핑되는 가상 데이터
  const requestItem = {
    requestId: requestId || "REQ-777-LUCKY",
    eventId: "EVENT-CHICKEN-100",
    eventType: "FIRST_COME", // FIRST_COME 또는 LOTTERY
    
    // 문서에 명시된 상태 머신 (RECEIVED, QUEUED, PROCESSING, SUCCEEDED, REJECTED, FAILED_FINAL)
    status: "SUCCEEDED", 
    
    // 지연 시간 모델링을 위한 필수 타임스탬프 필드
    receivedAt: "2026-01-11 14:00:01.050", // Ingest 기록 시점
    queuedAt: "2026-01-11 14:00:01.450",   // SQS Enqueue 완료 (공정성 기준 시점)
    startedAt: "2026-01-11 14:00:02.100",   // Worker 처리 시작
    finishedAt: "2026-01-11 14:00:02.350",  // 처리 종료 (결과 확정)
    
    retryCount: 0 // Retryable 실패 시 증가하는 카운트
  };

  // 1. 실제 서버와 연결되는 진짜 데이터 


  // 2. 결과 메시지 매핑 (설계 의도 반영)
  const getStatusDisplay = () => {
    switch (requestItem.status) {
      case "SUCCEEDED":
        return { label: requestItem.eventType === "FIRST_COME" ? "참여 확정 🎉" : "당첨 축하드립니다! 🎁", color: "#2e7d32", bg: "#e8f5e9" };
      case "REJECTED":
        return { label: "정책 거절 (정원 초과/중복) 😢", color: "#d32f2f", bg: "#ffebee" };
      case "FAILED_FINAL":
        return { label: "시스템 오류로 인한 실패 ⚠️", color: "#757575", bg: "#eeeeee" };
      case "PROCESSING":
        return { label: "결과 처리 중... ⚙️", color: "#0288d1", bg: "#e1f5fe" };
      case "QUEUED":
        return { label: "대기열 진입 (순번 대기) ⏳", color: "#ed6c02", bg: "#fff3e0" };
      default:
        return { label: "요청 접수 완료 📥", color: "#3e2723", bg: "#f5f5f5" };
    }
  };

  const statusDisplay = getStatusDisplay();

  // 3. 지연 지표 계산 
  const calculateDelay = (end, start) => {
    return (new Date(end) - new Date(start)).toFixed(2) + "ms";
  };

  return (
    <div style={{ padding: '40px 20px', maxWidth: '800px', margin: '0 auto' }}>
      <h2 style={{ textAlign: 'center', marginBottom: '30px' }}>요청 상세 처리 결과</h2>

      {/* 결과 요약 카드 */}
      <div style={{ 
        padding: '30px', borderRadius: '15px', textAlign: 'center', marginBottom: '30px',
        backgroundColor: statusDisplay.bg, border: `2px solid ${statusDisplay.color}`
      }}>
        <h1 style={{ color: statusDisplay.color, margin: '0 0 10px 0' }}>{statusDisplay.label}</h1>
        <p>요청 ID: {requestItem.requestId}</p>
        <p>이벤트 유형: {requestItem.eventType === "FIRST_COME" ? "선착순 방식" : "추첨 방식"}</p>
      </div>

      {/* 📍 처리 타임라인 (문서의 타임스탬프 필드 시각화) */}
      <div style={{ backgroundColor: '#fff', padding: '25px', borderRadius: '12px', border: '1px solid #eee', boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
        <h4 style={{ marginBottom: '20px', borderBottom: '2px solid #eee', paddingBottom: '10px' }}>📍 시스템 처리 타임라인</h4>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <Step label="RECEIVED (접수 완료)" time={requestItem.receivedAt} description="DynamoDB Ingest 기록" isDone />
          <Step label="QUEUED (공정성 기준 확정)" time={requestItem.queuedAt} description={`SQS Enqueue 성공 (Queue Delay: ${calculateDelay(requestItem.startedAt, requestItem.queuedAt)})`} isDone />
          <Step label="PROCESSING (워커 처리)" time={requestItem.startedAt} description="Worker 메시지 소비 및 비즈니스 로직 수행" isDone />
          <Step label="FINISHED (최종 상태 확정)" time={requestItem.finishedAt} description={`End-to-End 지연: ${calculateDelay(requestItem.finishedAt, requestItem.receivedAt)}`} isDone />
        </div>
      </div>

      {/* 공정성 안내 (문서 내용 반영) */}
      <div style={{ marginTop: '20px', padding: '15px', backgroundColor: '#f5f5f5', borderRadius: '8px', fontSize: '14px', color: '#555' }}>
        <p>💡 <strong>공정성 안내:</strong> {requestItem.eventType === "FIRST_COME" 
          ? "처리 완료 순서가 아닌, 큐(SQS)에 정상 접수된 시점(QUEUED)을 기준으로 선착순 정원을 확정합니다." 
          : "일정 시간 동안 응모된 모든 요청을 수집한 뒤, 마감 시점에 무작위 추첨을 진행합니다."}
        </p>
      </div>
    </div>
  );
};

// 타임라인 한 줄을 만드는 부품
const Step = ({ label, time, description, isDone }) => (
  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
    <div style={{ display: 'flex', gap: '15px' }}>
      <div style={{ width: '12px', height: '12px', borderRadius: '50%', backgroundColor: isDone ? '#93C572' : '#ddd', marginTop: '6px' }} />
      <div>
        <div style={{ fontWeight: 'bold', fontSize: '15px' }}>{label}</div>
        <div style={{ fontSize: '12px', color: '#888' }}>{description}</div>
      </div>
    </div>
    <span style={{ fontSize: '12px', color: '#666', fontFamily: 'monospace' }}>{time}</span>
  </div>
);

export default Result;