import React from 'react';
import { useNavigate } from 'react-router-dom';

// 1. 부모(App.jsx)로부터 history 데이터를 props로 받는다. 
const ParticipationHistory = ({history = []}) => {
  const navigate = useNavigate();

  // 2. 결과 상태(SUCCESS, REJECTED, PENDING)에 따른 스타일 설정
  const getResultStyle = (result) => {
    switch (result) {
      case "SUCCESS": 
        return { color: "#2e7d32", backgroundColor: "#e8f5e9", label: "당첨 🎉" };
      case "REJECTED": 
        return { color: "#d32f2f", backgroundColor: "#ffebee", label: "탈락 😢" };
      default: 
        return { color: "#ed6c02", backgroundColor: "#fff3e0", label: "대기 중 ⏳" };
    }
  };

  return (
    <div style={{ padding: '40px 20px', maxWidth: '800px', margin: '0 auto' }}>
      <h2 style={{ marginBottom: '10px' }}>3️⃣ 내 신청 내역</h2>
      <p style={{ color: '#666', marginBottom: '30px' }}>
        참여하신 이벤트의 처리 상태와 결과를 확인할 수 있습니다.
      </p>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
        {/* 3. 데이터가 없을 때의 예외 처리 */}
        {history.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '50px', color: '#999', border: '1px dashed #ccc', borderRadius: '12px' }}>
            아직 참여한 이벤트가 없습니다. 첫 이벤트에 응모해보세요!
          </div>
        ) : (
          // 4. 고정 데이터 대신 App.jsx에서 넘어온 history를 사용
          history.map((item) => {
            const resStyle = getResultStyle(item.result);
          
          return (
            <div 
              key={item.id}
              // 클릭 시 /result/요청ID 주소로 이동합니다.
              onClick={() => navigate('/result/${item.id}')}
              style={{
                border: '1px solid #eee',
                borderRadius: '12px',
                padding: '20px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                cursor: 'pointer',
                backgroundColor: '#fff',
                boxShadow: '0 2px 4px rgba(0,0,0,0.05)',
                transition: 'all 0.2s ease'
              }}
              // 마우스 올렸을 때 살짝 뜨는 효과
              onMouseEnter={(e) => {
                e.currentTarget.style.transform = 'translateY(-2px)';
                e.currentTarget.style.boxShadow = '0 4px 8px rgba(0,0,0,0.1)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.transform = 'translateY(0)';
                e.currentTarget.style.boxShadow = '0 2px 4px rgba(0,0,0,0.05)';
              }}
            >
              {/* 왼쪽: 이벤트 정보 */}
              <div style={{ textAlign: 'left' }}>
                <h3 style={{ margin: '0 0 8px 0', fontSize: '18px', color: '#333' }}>{item.title}</h3>
                <div style={{ fontSize: '13px', color: '#888' }}>
                  <div>📅 신청 시각: {item.appliedAt}</div>
                  <div style={{ marginTop: '4px' }}>🆔 요청 번호: {item.id}</div>
                </div>
              </div>

              {/* 오른쪽: 상태 및 결과 */}
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: '14px', fontWeight: '600', marginBottom: '8px', color: '#555' }}>
                  {item.status}
                </div>
                <span style={{
                  padding: '6px 12px',
                  borderRadius: '20px',
                  fontSize: '12px',
                  fontWeight: 'bold',
                  color: resStyle.color,
                  backgroundColor: resStyle.backgroundColor
                }}>
                  {resStyle.label}
                </span>
              </div>
            </div>
          );
        })  // map 끝
    )}
      </div>

      <p style={{ marginTop: '40px', fontSize: '13px', color: '#999', textAlign: 'center' }}>
        * 최근 30일간의 내역만 표시됩니다.
      </p>
    </div>
  );
};

export default ParticipationHistory;