import React from 'react';

// 공정성 처리 모델
const EventCard = ({ title, type, deadline, status }) => {
  // 방식(Type) 뱃지 스타일
  const badgeStyle = {
    display: 'inline-block',
    padding: '4px 8px',
    borderRadius: '4px',
    fontSize: '12px',
    fontWeight: 'bold',
    marginBottom: '8px',
    backgroundColor: type === 'FIRST_COME' ? '#FFE0B2' : '#E1F5FE',
    color: type === 'FIRST_COME' ? '#E65100' : '#01579B'
  };

  const buttonStyle = {
    backgroundColor: '#93C572', 
    color: '#3E2723',
    border: '2px solid #5D4037',
    padding: '10px 20px',
    borderRadius: '8px',
    fontWeight: 'bold',
    cursor: 'pointer',
    width: '100%',
    marginTop: '10px'
  };

  return (
    <div style={{
      border: '1px solid #ddd',
      borderRadius: '12px',
      padding: '20px',
      width: '300px',
      boxShadow: '0 4px 6px rgba(0,0,0,0.05)',
      backgroundColor: '#fff',
      textAlign: 'left' // 텍스트 왼쪽 정렬
    }}>
      <div style={badgeStyle}>
        {type === 'FIRST_COME' ? '⚡ 선착순 방식' : '🎲 추첨 방식'}
      </div>
      
      <h3 style={{ margin: '0 0 10px 0', fontSize: '18px', color: '#333' }}>{title}</h3>
      
      <div style={{ fontSize: '14px', color: '#666', lineHeight: '1.6' }}>
        <div>📅 <strong>마감/정원:</strong> {deadline}</div>
        <div>🔔 <strong>상태:</strong> {status}</div>
        
        {/* 핵심 기술 문서 내용 반영: 공정성 고지 */}
        <div style={{ 
          marginTop: '12px', 
          padding: '8px', 
          backgroundColor: '#f5f5f5', 
          borderRadius: '6px',
          fontSize: '11px',
          color: '#5D4037',
          lineHeight: '1.3'
        }}>
          {type === 'FIRST_COME' 
            ? "⏱️ 본 이벤트는 네트워크 처리 순서가 아닌, 시스템 큐 접수 시점을 기준으로 공정하게 선착순을 확정합니다." 
            : "🎲 응모 기간 내 접수된 모든 요청을 대상으로 마감 후 무작위 추첨을 진행합니다."}
        </div>
      </div>

      <button style={buttonStyle}>참여하기</button>
    </div>
  );
};

export default EventCard;