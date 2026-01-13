import React, { useState } from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, LineChart, Line, PieChart, Pie, Cell } from 'recharts';

const Analytics = () => {
  // Athena 쿼리 결과 시뮬레이션 데이터
  const trafficData = [
    { time: '09:00', total: 150, success: 145, rejected: 5, failed: 0 },
    { time: '10:00', total: 400, success: 380, rejected: 15, failed: 5 },
    { time: '11:00', total: 1200, success: 1000, rejected: 180, failed: 20 }, // 피크 타임 진입
    { time: '12:00', total: 3500, success: 2800, rejected: 650, failed: 50 }, // 트래픽 정점
    { time: '13:00', total: 1800, success: 1650, rejected: 140, failed: 10 },
    { time: '14:00', total: 600, success: 580, rejected: 18, failed: 2 },
  ];

  const summaryMetrics = [
    { name: 'SUCCEEDED', value: '82.4%', color: '#93C572', desc: '정상 처리 완료' },
    { name: 'REJECTED', value: '15.2%', color: '#FFD54F', desc: 'Capacity 초과' },
    { name: 'FAILED', value: '1.8%', color: '#E57373', desc: '어플리케이션 에러' },
    { name: 'DLQ', value: '0.6%', color: '#3E2723', desc: '재처리 대기 중' },
  ];

  return (
    <div style={{ padding: '40px', backgroundColor: '#FDFCFB', minHeight: '100vh' }}>
      <div style={{ marginBottom: '30px' }}>
        <h2 style={{ color: '#3E2723', margin: 0, fontFamily: 'serif' }}>이벤트별 트래픽 분석</h2>
        <p style={{ color: '#888', fontSize: '14px' }}>Athena 분석 결과를 바탕으로 한 시간대별 처리 현황입니다.</p>
      </div>

      <div style={{ 
        backgroundColor: '#fff', 
        padding: '30px', 
        borderRadius: '16px', 
        border: '2px solid #5D4037',
        boxShadow: '0 4px 12px rgba(0,0,0,0.05)',
        marginBottom: '30px'
      }}>
        <h4 style={{ marginBottom: '25px', color: '#5D4037', display: 'flex', alignItems: 'center', gap: '8px' }}>
          📈 실시간 트래픽 타임라인
        </h4>
        <div style={{ width: '100%', height: 400 }}>
          <ResponsiveContainer>
            <LineChart data={trafficData}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#eee" />
              <XAxis dataKey="time" tick={{fill: '#888', fontSize: 12}} />
              <YAxis tick={{fill: '#888', fontSize: 12}} />
              <Tooltip 
                contentStyle={{ borderRadius: '12px', border: '1px solid #5D4037' }}
              />
              <Legend verticalAlign="top" height={36}/>
              <Line type="monotone" dataKey="total" stroke="#5D4037" strokeWidth={3} dot={{ r: 6 }} name="전체 요청량" />
              <Line type="monotone" dataKey="success" stroke="#93C572" strokeWidth={2} name="성공" />
              <Line type="monotone" dataKey="rejected" stroke="#FFD54F" strokeWidth={2} strokeDasharray="5 5" name="거절(Capacity)" />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '20px' }}>
        {summaryMetrics.map((item) => (
          <div key={item.name} style={{ 
            backgroundColor: '#fff', 
            padding: '20px', 
            borderRadius: '12px', 
            borderLeft: `6px solid ${item.color}`,
            boxShadow: '0 2px 8px rgba(0,0,0,0.05)'
          }}>
            <p style={{ fontSize: '12px', color: '#888', margin: '0 0 5px 0' }}>{item.name}</p>
            <h3 style={{ margin: '0 0 5px 0', color: '#3E2723', fontSize: '24px' }}>{item.value}</h3>
            <p style={{ fontSize: '11px', color: '#aaa', margin: 0 }}>{item.desc}</p>
          </div>
        ))}
      </div>
    </div>
  );
};

export default Analytics;