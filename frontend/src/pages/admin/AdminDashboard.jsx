import React, { useState, useEffect } from 'react';
import { Activity, AlertCircle, RefreshCw, Clock, CheckCircle } from 'lucide-react';

const AdminDashboard = () => {
  // 1. 실시간 지표 상태 (실제 환경에서는 API 폴링 또는 WebSocket 사용)
  const [metrics, setMetrics] = useState({
    queueDepth: 142,
    errorRate: 1.2,
    dlqCount: 3,
    p95Latency: 850, // ms
    status: 'NORMAL' // NORMAL, WARNING, CRITICAL
  });

  // 스타일 정의 (사용자 Login UI의 톤앤매너 계승)
  const dashboardCardStyle = {
    backgroundColor: '#fff',
    border: '2px solid #5D4037',
    borderRadius: '16px',
    padding: '24px',
    boxShadow: '0 10px 25px rgba(0,0,0,0.05)',
  };

  const metricTitleStyle = {
    fontSize: '14px',
    color: '#888',
    marginBottom: '8px',
    fontWeight: 'bold'
  };

  const metricValueStyle = {
    fontSize: '28px',
    color: '#3E2723',
    fontWeight: '800'
  };

  return (
    <div style={{ padding: '40px', backgroundColor: '#FDFCFB', minHeight: '100vh' }}>
      {/* 상단 헤더: 시스템 상태 배지 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '40px' }}>
        <div>
          <h2 style={{ color: '#3E2723', margin: 0 }}>운영 대시보드</h2>
          <p style={{ color: '#888', fontSize: '14px' }}>시스템의 실시간 처리 현황을 모니터링합니다.</p>
        </div>
        <SystemStatusBadge status={metrics.status} />
      </div>

      {/* 핵심 지표 그리드 */}
      <div style={{ 
        display: 'grid', 
        gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', 
        gap: '24px',
        marginBottom: '40px' 
      }}>
        <div style={dashboardCardStyle}>
          <div style={metricTitleStyle}>Queue Depth</div>
          <div style={metricValueStyle}>{metrics.queueDepth.toLocaleString()} <span style={{fontSize: '16px'}}>msg</span></div>
          <div style={{marginTop: '10px', color: '#93C572', fontSize: '12px'}}>▲ 12% vs last hour</div>
        </div>

        <div style={dashboardCardStyle}>
          <div style={metricTitleStyle}>Error Rate</div>
          <div style={{...metricValueStyle, color: metrics.errorRate > 5 ? '#E57373' : '#3E2723'}}>
            {metrics.errorRate}%
          </div>
          <div style={{marginTop: '10px', color: '#888', fontSize: '12px'}}>최근 5분 기준</div>
        </div>

        <div style={{...dashboardCardStyle, border: metrics.dlqCount > 0 ? '2px solid #E57373' : '2px solid #5D4037'}}>
          <div style={metricTitleStyle}>DLQ Count</div>
          <div style={{...metricValueStyle, color: metrics.dlqCount > 0 ? '#E57373' : '#3E2723'}}>
            {metrics.dlqCount}
          </div>
          <div style={{marginTop: '10px', fontSize: '12px', color: metrics.dlqCount > 0 ? '#E57373' : '#888'}}>
            {metrics.dlqCount > 0 ? '⚠️ 즉시 확인 필요' : '관리 중인 실패 없음'}
          </div>
        </div>

        <div style={dashboardCardStyle}>
          <div style={metricTitleStyle}>p95 Latency</div>
          <div style={metricValueStyle}>{metrics.p95Latency} <span style={{fontSize: '16px'}}>ms</span></div>
          <div style={{marginTop: '10px', color: '#888', fontSize: '12px'}}>상위 95% 처리 속도</div>
        </div>
      </div>

      {/* 시스템 메시지 박스 (Login UI 하단 힌트 박스 스타일) */}
      <div style={{ 
        padding: '20px', 
        backgroundColor: '#fff', 
        border: '1px solid #ddd', 
        borderRadius: '12px',
        fontSize: '14px',
        color: '#3E2723'
      }}>
        <div style={{display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 'bold', marginBottom: '8px'}}>
          <Activity size={18} color="#93C572" /> 시스템 안정성 진단
        </div>
        {metrics.status === 'NORMAL' 
          ? "✅ 현재 모든 워커가 정상적으로 이벤트를 처리하고 있습니다. 트래픽 유입량이 안정적입니다."
          : "⚠️ 현재 일부 처리 지연이 감지되었습니다. DLQ 목록을 확인하세요."}
      </div>
    </div>
  );
};

// 상태 배지 컴포넌트
const SystemStatusBadge = ({ status }) => {
  const isNormal = status === 'NORMAL';
  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      padding: '8px 16px',
      borderRadius: '20px',
      backgroundColor: isNormal ? '#93C572' : '#FFDAD6',
      color: '#3E2723',
      fontWeight: 'bold',
      fontSize: '14px',
      border: `1px solid ${isNormal ? '#7CB342' : '#E57373'}`
    }}>
      {isNormal ? <CheckCircle size={16} style={{marginRight: '6px'}}/> : <AlertCircle size={16} style={{marginRight: '6px'}}/>}
      {isNormal ? '시스템 정상' : '주의 요망'}
    </div>
  );
};

export default AdminDashboard;