import React, { useState } from 'react';

// ✅ Props를 여기서 받아야 컴포넌트 전체에서 사용할 수 있습니다.
const EventDetailLottery = ({ eventData, addHistory, eventType = "LOTTERY" }) => {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isApplied, setIsApplied] = useState(false);
  const [requestId, setRequestId] = useState("");

  // ✅ 함수의 인자에서 { eventData... } 를 제거했습니다.
  const handleApply = async () => {
    if (isSubmitting) return;
    setIsSubmitting(true);

    try {
      const mockRequestId = "LOTT-" + Math.random().toString(36).substr(2, 9).toUpperCase();
      
      const newEntry = {
        id: mockRequestId,
        title: eventData?.title || "이벤트", // 안전하게 접근
        status: "응모 완료",
        result: "PROCESSING",
        appliedAt: new Date().toLocaleString(),
      };

      if (addHistory) addHistory(newEntry);
      
      setRequestId(mockRequestId);
      setIsApplied(true);
      alert("🎲 추첨 응모가 완료되었습니다! 마감 후 결과를 확인하세요.");
    } finally {
      setIsSubmitting(false);
    }
  };

  // ... (기존 스타일 정의 동일)
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
    // 이제 eventType을 정상적으로 인식합니다.
    const steps = eventType === "LOTTERY" 
      ? ["접수 완료", "응모 수집 중", "추첨 진행", "결과 발표"]
      : ["접수 완료", "대기열 등록", "처리 중", "결과 확정"];

    return (
      <div style={{ padding: '40px 20px', textAlign: 'center' }}>
        {/* ✅ eventData?.title 로 수정하여 에러 방지 */}
        <h2>{eventData?.title}</h2>
        
        <div style={{ backgroundColor: '#93C572', padding: '20px', borderRadius: '12px', marginBottom: '30px', textAlign: 'left' }}>
          <p>🎲 <strong>추첨제 안내</strong></p>
          <p style={{ fontSize: '13px', color: '#555' }}>
            이 이벤트는 <strong>무작위 추첨</strong> 방식입니다. 응모 순서와 상관없이 마감 시점까지 신청한 모든 인원을 대상으로 시스템이 공정하게 당첨자를 선발합니다.
          </p>
        </div>

        {!isApplied ? (
          <button 
            onClick={handleApply}
            style={{ backgroundColor: '#93C572', padding: '15px 30px', borderRadius: '8px', fontWeight: 'bold', width: '100%', cursor: 'pointer', border: 'none' }}
            disabled={isSubmitting}
          >
            {isSubmitting ? "응모 처리 중..." : "추첨 응모하기"}
          </button>
        ) : (
          <div>
            <h4 style={{ color: '#2e7d32' }}>✅ 응모 완료!</h4>
            <p>내 응모 ID: <strong>{requestId}</strong></p>
          </div>
        )}
      </div>
    );
  };

  return (
    <div style={containerStyle}>
      <h2>Lottery 추첨 시스템</h2>
      
      <div style={infoBox}>
        <p>⚠️ <strong>참여 전 안내</strong></p>
        <ul style={{ fontSize: '14px', color: '#555' }}>
          <li>본 이벤트는 <strong>추첨 방식</strong>으로 처리됩니다.</li>
          <li>버튼 클릭 시 '접수'가 먼저 진행되며, 최종 결과는 타임라인에서 확인 가능합니다.</li>
        </ul>
      </div>

      {!isApplied ? (
        <button style={buttonStyle} onClick={handleApply}>참여하기</button>
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

export default EventDetailLottery;