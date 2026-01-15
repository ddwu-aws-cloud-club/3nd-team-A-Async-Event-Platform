import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { LayoutDashboard, BarChart3, DatabaseZap, Search, Home } from 'lucide-react';

const AdminLayout = ({ children }) => {
  const location = useLocation();

  const menuItems = [
    { path: '/admin', name: '운영 대시보드', icon: LayoutDashboard },
    { path: '/admin/analytics', name: '트래픽 분석', icon: BarChart3 },
    { path: '/admin/dlq', name: 'DLQ 관리', icon: DatabaseZap },
    { path: '/admin/search', name: '요청 탐색', icon: Search },
  ];

  return (
    <div style={{ display: 'flex', minHeight: '100vh', backgroundColor: '#FDFCFB' }}>
      {/* 사이드바 */}
      <div style={{ 
        width: '260px', backgroundColor: '#3E2723', color: '#fff', 
        padding: '30px 20px', display: 'flex', flexDirection: 'column' 
      }}>
        <h2 style={{ fontSize: '20px', marginBottom: '40px', color: '#93C572', fontFamily: 'serif' }}>
          Event Admin
        </h2>
        
        <nav style={{ flex: 1 }}>
          {menuItems.map((item) => {
            const isActive = location.pathname === item.path;
            const Icon = item.icon;
            return (
              <Link 
                key={item.path} 
                to={item.path} 
                style={{ 
                  display: 'flex', alignItems: 'center', gap: '12px', padding: '15px',
                  textDecoration: 'none', color: isActive ? '#93C572' : '#D7CCC8',
                  backgroundColor: isActive ? 'rgba(255,255,255,0.05)' : 'transparent',
                  borderRadius: '8px', marginBottom: '8px', fontWeight: isActive ? 'bold' : 'normal'
                }}
              >
                <Icon size={20} />
                {item.name}
              </Link>
            );
          })}
        </nav>

        {/* 사용자 홈으로 돌아가기 */}
        <Link to="/" style={{ 
          display: 'flex', alignItems: 'center', gap: '12px', padding: '15px',
          textDecoration: 'none', color: '#888', borderTop: '1px solid #5D4037', paddingTop: '20px'
        }}>
          <Home size={20} /> 메인으로 돌아가기
        </Link>
      </div>

      {/* 메인 콘텐츠 영역 */}
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {children}
      </div>
    </div>
  );
};

export default AdminLayout;