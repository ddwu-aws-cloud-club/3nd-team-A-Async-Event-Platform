import React, { useState } from 'react';
import { useLocation } from 'react-router-dom';

const EventDetailAsync = ({ eventData, addHistory }) => {
  // 1. 상태 관리 (참여 여부, 요청 ID, 이벤트 타입)
  const [isSubmitting, setIsSubmitting] = useState(false); // 멱등성 유지를 위한 상태
  const [errorType, setErrorType] = useState(null); // 실패 분류 (Retryable vs Final)
  const [isApplied, setIsApplied] = useState(false);
  const [requestId, setRequestId] = useState("");
  const eventType = eventData?.type || "FIRST_COME";

  // 2. 가짜 참여 함수 (백엔드 202 Accepted 시뮬레이션)
  const handleApply = async () => { 
    if (isSubmitting) return;
    setIsSubmitting(true);
    setErrorType(null);

    try {
      // 실제 API 호출 시나리오 시뮬레이션
      const mockRequestId = "REQ-" + Math.random().toString(36).substr(2, 9).toUpperCase();
      
      const newEntry = {
        id: mockRequestId,
        title: eventData.title,
        status: "RECEIVED", // 초기 상태
        result: "PROCESSING",
        appliedAt: new Date().toLocaleString(),
      };

      // 부모 App.jsx의 history에 추가
      if (addHistory) {
        addHistory(newEntry); 
        console.log("기록 완료:", newEntry); // 디버깅용
      }
      
      setRequestId(mockRequestId);
      setIsApplied(true);
      alert("✅ 공정성 기준(Queue)에 따라 접수가 완료되었습니다.");
    } catch (err) {
      // 에러 분류 예시
      setErrorType("RETRYABLE"); // 또는 "FINAL"
    } finally {
      setIsSubmitting(false);
    }
  };

  // 3. 스타일 정의
  const containerStyle = { padding: '40px 20px', maxWidth: '600px', margin: '0 auto', textAlign: 'center' };
  const infoBox = { backgroundColor: '#f0f4f8', padding: '20px', borderRadius: '12px', marginBottom: '30px', textAlign: 'left' };
  const buttonStyle = {
    backgroundColor: isApplied ? '#ccc' : '#93C572',
    color: '#3E2723',
    border: '2px solid #5D4037',
    padding: '15px 30px',
    borderRadius: '8px',
    fontWeight: 'bold',
    cursor: isApplied ? 'not-allowed' : 'pointer',
    fontSize: '18px',
    width: '100%'
  };

  // 4. 타임라인 컴포넌트
  const Timeline = () => {
    const steps = eventType === "FIRST_COME" 
      ? ["접수 완료", "대기열 등록", "처리 중", "결과 확정"]
      : ["접수 완료", "응모 수집 중", "추첨 진행", "결과 발표"];
    
    const statusMsg = eventType === "FIRST_COME"
      ? "현재 대기열에 있습니다. 잠시만 기다려주세요."
      : "응모 수집 중입니다 (마감 시각 이후 추첨)";

    return (
      <div style={{ marginTop: '40px', padding: '20px', borderTop: '1px solid #eee' }}>
        <h4>📍 처리 상태 타임라인</h4>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '20px', position: 'relative' }}>
          {steps.map((step, index) => (
            <div key={index} style={{ textAlign: 'center', flex: 1 }}>
              <div style={{ 
                width: '30px', height: '30px', borderRadius: '50%', 
                backgroundColor: index === 0 ? '#93C572' : '#ddd', 
                margin: '0 auto 10px', color: 'white', lineHeight: '30px' 
              }}>{index + 1}</div>
              <div style={{ fontSize: '12px', fontWeight: index === 0 ? 'bold' : 'normal' }}>{step}</div>
            </div>
          ))}
        </div>
        <p style={{ backgroundColor: '#fff8e1', padding: '10px', borderRadius: '8px', fontSize: '14px' }}>
          💡 <strong>{statusMsg}</strong>
        </p>
      </div>
    );
  };

  return (
    <div style={containerStyle}>
      <h2>{eventData.title}</h2> 

      {/* 공정성 모델 안내 */}
      <div style={infoBox}>
        <p>⏱️ <strong>공정성 안내</strong></p>
        <p style={{fontSize: '13px'}}>본 시스템은 '처리 속도'가 아닌 <strong>'SQS 큐 접수 시점(queuedAt)'</strong>을 기준으로 정원을 확정하여 공정한 기회를 보장합니다.</p>
      </div>
      
      {/* 에러 처리 UI */}
      {errorType === "FINAL" && <div style={{color: 'red'}}>❌ 재시도 불가 오류: 관리자에게 문의하세요.</div>}
      {errorType === "RETRYABLE" && <button onClick={handleApply}>일시적 오류: 다시 시도</button>}

      {!isApplied ? (
        <button 
          style={buttonStyle} 
          onClick={handleApply} 
          disabled={isSubmitting} // 멱등성 보장
        >
          {isSubmitting ? "데이터 무결성 확인 중..." : "참여하기"}
        </button>
      ) : (
        <div>
          <button style={buttonStyle} disabled>이미 신청된 이벤트입니다</button>
          
          <div style={{ marginTop: '20px', padding: '15px', border: '1px dashed #5D4037', borderRadius: '8px' }}>
            <span style={{ fontSize: '14px' }}>요청 ID: <strong>{requestId}</strong></span>
            <button 
              onClick={() => { navigator.clipboard.writeText(requestId); alert("복사되었습니다!"); }}
              style={{ marginLeft: '10px', padding: '2px 8px', fontSize: '12px', cursor: 'pointer' }}
            >복사</button>
          </div>

          <Timeline />
        </div>
      )}

      <p style={{ marginTop: '30px', fontSize: '13px', color: '#888' }}>
        * 202 Accepted: 요청이 시스템에 안전하게 전달되었습니다.
      </p>
    </div>
  );
};

export default EventDetailAsync;