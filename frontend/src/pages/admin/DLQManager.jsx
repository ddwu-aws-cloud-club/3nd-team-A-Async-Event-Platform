import React, { useState } from 'react';
import { RefreshCw, Trash2, AlertCircle, ExternalLink } from 'lucide-react';

const DLQManager = () => {
  // 임시 DLQ 메시지 데이터
  const [dlqMessages, setDlqMessages] = useState([
    { id: 'msg_8210', eventId: 'EVT-101', error: 'DB_LOCK_TIMEOUT', payload: '{"userId": 501, "itemId": 1}', time: '2023-10-27 14:02:11', retryable: true },
    { id: 'msg_8215', eventId: 'EVT-101', error: 'INSUFFICIENT_STOCK', payload: '{"userId": 702, "itemId": 1}', time: '2023-10-27 14:05:45', retryable: false },
    { id: 'msg_8219', eventId: 'EVT-105', error: 'CONNECTION_REFUSED', payload: '{"userId": 333, "itemId": 5}', time: '2023-10-27 14:10:02', retryable: true },
  ]);

  const handleRedrive = (id) => {
    alert(`메시지 ${id}를 재처리 큐로 전송했습니다.`);
    setDlqMessages(prev => prev.filter(msg => msg.id !== id));
  };

  return (
    <div style={{ padding: '40px', backgroundColor: '#FDFCFB', minHeight: '100vh' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: '30px' }}>
        <div>
          <h2 style={{ color: '#3E2723', margin: 0, fontFamily: 'serif' }}>Dead Letter Queue 관리</h2>
          <p style={{ color: '#888', fontSize: '14px' }}>처리에 실패하여 DLQ로 유입된 메시지 목록입니다.</p>
        </div>
        <button style={{ 
          backgroundColor: '#3E2723', color: '#fff', border: 'none', padding: '10px 20px', 
          borderRadius: '8px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '8px' 
        }}>
          <RefreshCw size={18} /> 전체 메시지 재처리 (Redrive All)
        </button>
      </div>

      <div style={{ backgroundColor: '#fff', borderRadius: '16px', border: '2px solid #5D4037', overflow: 'hidden' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
          <thead style={{ backgroundColor: '#F5F5F5', borderBottom: '2px solid #5D4037' }}>
            <tr>
              <th style={{ padding: '15px', color: '#5D4037' }}>Message ID</th>
              <th style={{ padding: '15px', color: '#5D4037' }}>Error Type</th>
              <th style={{ padding: '15px', color: '#5D4037' }}>Payload</th>
              <th style={{ padding: '15px', color: '#5D4037' }}>Status</th>
              <th style={{ padding: '15px', color: '#5D4037' }}>Action</th>
            </tr>
          </thead>
          <tbody>
            {dlqMessages.map((msg) => (
              <tr key={msg.id} style={{ borderBottom: '1px solid #eee' }}>
                <td style={{ padding: '15px', fontSize: '14px' }}>
                  <span style={{ fontWeight: 'bold' }}>{msg.id}</span>
                  <div style={{ fontSize: '12px', color: '#aaa' }}>{msg.time}</div>
                </td>
                <td style={{ padding: '15px' }}>
                  <code style={{ color: '#E57373', backgroundColor: '#FFEBEE', padding: '2px 6px', borderRadius: '4px' }}>
                    {msg.error}
                  </code>
                </td>
                <td style={{ padding: '15px', fontSize: '12px', color: '#666', fontFamily: 'monospace' }}>
                  {msg.payload}
                </td>
                <td style={{ padding: '15px' }}>
                  {msg.retryable ? 
                    <span style={{ color: '#93C572', fontSize: '12px' }}>● Retryable</span> : 
                    <span style={{ color: '#888', fontSize: '12px' }}>○ Non-retryable</span>
                  }
                </td>
                <td style={{ padding: '15px' }}>
                  <div style={{ display: 'flex', gap: '10px' }}>
                    <button 
                      onClick={() => handleRedrive(msg.id)}
                      disabled={!msg.retryable}
                      style={{ 
                        padding: '6px 12px', borderRadius: '6px', border: '1px solid #5D4037',
                        backgroundColor: msg.retryable ? '#fff' : '#eee', cursor: msg.retryable ? 'pointer' : 'not-allowed'
                      }}
                    >
                      재처리
                    </button>
                    <button style={{ border: 'none', backgroundColor: 'transparent', color: '#888', cursor: 'pointer' }}>
                      <Trash2 size={18} />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* 가이드 라인 (Runbook) */}
      <div style={{ marginTop: '30px', padding: '20px', backgroundColor: '#FFF9C4', borderRadius: '12px', border: '1px solid #FBC02D' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 'bold', color: '#7F6000', marginBottom: '8px' }}>
          <AlertCircle size={18} /> 장애 대응 가이드 (Runbook)
        </div>
        <p style={{ margin: 0, fontSize: '13px', color: '#7F6000' }}>
          <code>DB_LOCK_TIMEOUT</code> 발생 시에는 즉시 재처리가 가능합니다. 
          단, <code>INSUFFICIENT_STOCK</code> 에러는 비즈니스 로직 확인이 필요하므로 수동 확인 후 삭제하십시오. 
        </p>
      </div>
    </div>
  );
};

export default DLQManager;