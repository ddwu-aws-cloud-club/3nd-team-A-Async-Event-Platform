import React, { useState } from 'react';
import { Search, Clock, ChevronRight, Info, AlertCircle } from 'lucide-react';

const RequestDetail = () => {
  const [searchId, setSearchId] = useState('');
  const [searchResult, setSearchResult] = useState(null);

  // 임시 탐색 데이터 (requestId: REQ-1004 검색 시 시뮬레이션)
  const handleSearch = () => {
    if (searchId === 'REQ-1004') {
      setSearchResult({
        id: 'REQ-1004',
        userId: 'user_brown_99',
        eventId: 'EVT-2024-PROMO',
        finalStatus: 'FAILED',
        reason: 'Out of Capacity (First-come)',
        timeline: [
          { time: '14:00:01.102', msg: 'Ingest API: 요청 접수됨', status: 'SUCCESS' },
          { time: '14:00:01.150', msg: 'RabbitMQ: 메시지 발행 완료 (Exchange: event.topic)', status: 'SUCCESS' },
          { time: '14:00:02.450', msg: 'Worker: 메시지 소비 및 처리 시작', status: 'SUCCESS' },
          { time: '14:00:02.480', msg: 'Redis: Capacity 차감 시도', status: 'INFO' },
          { time: '14:00:02.510', msg: 'Worker: 잔여 수량 부족으로 인한 처리 거절', status: 'FAIL' },
          { time: '14:00:02.550', msg: 'DLQ: 실패 메시지 아카이빙 완료', status: 'SUCCESS' },
        ]
      });
    } else {
      alert('테스트를 위해 REQ-1004를 입력해보세요!');
    }
  };

  return (
    <div style={{ padding: '40px', backgroundColor: '#FDFCFB', minHeight: '100vh' }}>
      <div style={{ marginBottom: '30px' }}>
        <h2 style={{ color: '#3E2723', margin: 0, fontFamily: 'serif' }}>요청 탐색기 (Request Explorer)</h2>
        <p style={{ color: '#888', fontSize: '14px' }}>RequestId 또는 UserId를 통해 요청의 전체 처리 이력을 추적합니다.</p>
      </div>

      {/* 검색 바 */}
      <div style={{ display: 'flex', gap: '12px', marginBottom: '40px' }}>
        <div style={{ flex: 1, position: 'relative' }}>
          <Search size={20} style={{ position: 'absolute', left: '15px', top: '50%', transform: 'translateY(-50%)', color: '#888' }} />
          <input 
            type="text" 
            placeholder="RequestId 입력 (예: REQ-1004)"
            value={searchId}
            onChange={(e) => setSearchId(e.target.value)}
            style={{ 
              width: '100%', padding: '15px 15px 15px 45px', borderRadius: '12px', 
              border: '2px solid #5D4037', fontSize: '16px', outline: 'none'
            }}
          />
        </div>
        <button 
          onClick={handleSearch}
          style={{ 
            backgroundColor: '#5D4037', color: '#fff', border: 'none', padding: '0 30px', 
            borderRadius: '12px', cursor: 'pointer', fontWeight: 'bold' 
          }}
        >
          검색
        </button>
      </div>

      {searchResult && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '30px' }}>
          {/* 왼쪽: 요약 정보 */}
          <div style={{ backgroundColor: '#fff', padding: '24px', borderRadius: '16px', border: '2px solid #5D4037', height: 'fit-content' }}>
            <h4 style={{ margin: '0 0 20px 0', borderBottom: '1px solid #eee', paddingBottom: '10px' }}>요청 요약</h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
              <div><p style={{ fontSize: '12px', color: '#888', margin: 0 }}>Request ID</p><p style={{ margin: 0, fontWeight: 'bold' }}>{searchResult.id}</p></div>
              <div><p style={{ fontSize: '12px', color: '#888', margin: 0 }}>User ID</p><p style={{ margin: 0, fontWeight: 'bold' }}>{searchResult.userId}</p></div>
              <div><p style={{ fontSize: '12px', color: '#888', margin: 0 }}>최종 상태</p>
                <span style={{ backgroundColor: '#FFEBEE', color: '#E57373', padding: '2px 8px', borderRadius: '4px', fontSize: '12px', fontWeight: 'bold' }}>
                  {searchResult.finalStatus}
                </span>
              </div>
              <div style={{ backgroundColor: '#F5F5F5', padding: '12px', borderRadius: '8px' }}>
                <p style={{ fontSize: '12px', color: '#666', margin: '0 0 5px 0' }}>실패 원인 요약</p>
                <p style={{ fontSize: '13px', margin: 0, color: '#3E2723' }}>{searchResult.reason}</p>
              </div>
            </div>
          </div>

          {/* 오른쪽: 타임라인 */}
          <div style={{ backgroundColor: '#fff', padding: '30px', borderRadius: '16px', border: '2px solid #5D4037' }}>
            <h4 style={{ margin: '0 0 30px 0' }}>처리 타임라인</h4>
            <div style={{ position: 'relative' }}>
              {searchResult.timeline.map((step, index) => (
                <div key={index} style={{ display: 'flex', gap: '20px', marginBottom: '25px', position: 'relative' }}>
                  {/* 타임라인 선 */}
                  {index !== searchResult.timeline.length - 1 && (
                    <div style={{ position: 'absolute', left: '7px', top: '20px', width: '2px', height: '40px', backgroundColor: '#eee' }} />
                  )}
                  {/* 상태 점 */}
                  <div style={{ 
                    width: '16px', height: '16px', borderRadius: '50%', marginTop: '4px',
                    backgroundColor: step.status === 'SUCCESS' ? '#93C572' : step.status === 'FAIL' ? '#E57373' : '#FFD54F',
                    zIndex: 1
                  }} />
                  <div>
                    <p style={{ fontSize: '11px', color: '#aaa', margin: 0 }}>{step.time}</p>
                    <p style={{ fontSize: '14px', color: '#3E2723', margin: '2px 0' }}>{step.msg}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default RequestDetail;