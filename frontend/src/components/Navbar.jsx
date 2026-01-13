import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Home, ClipboardList, User, Settings } from 'lucide-react';

const Navbar = ({ userName }) => {
  const location = useLocation();

  // 현재 활성화된 메뉴인지 확인하는 스타일 함수
  const getLinkStyle = (path) => ({
    textDecoration: 'none',
    color: location.pathname === path ? '#93C572' : 'white',
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
    fontSize: '14px',
    fontWeight: location.pathname === path ? 'bold' : 'normal',
    transition: '0.2s'
  });

  return (
    <nav style={{ 
      padding: '15px 40px', 
      display: 'flex', 
      justifyContent: 'space-between', 
      alignItems: 'center',
      backgroundColor: '#3E2723', 
      color: 'white',
      boxShadow: '0 2px 10px rgba(0,0,0,0.2)',
      position: 'sticky',
      top: 0,
      zIndex: 1000
    }}>
      {/* 로고 영역: 클릭 시 홈으로 */}
      <Link to="/" style={{ textDecoration: 'none', color: '#93C572', fontWeight: 'bold', fontSize: '18px', fontFamily: 'serif' }}>
        Async-Event
      </Link>

      {/* 중앙 메뉴 영역 */}
      <div style={{ display: 'flex', gap: '30px' }}>
        <Link to="/" style={getLinkStyle('/')}>
          <Home size={18} /> 이벤트 목록
        </Link>
        <Link to="/history" style={getLinkStyle('/history')}>
          <ClipboardList size={18} /> 신청 내역
        </Link>
      </div>

      {/* 우측 사용자 영역 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
        {userName ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', fontSize: '14px' }}>
            <User size={18} color="#93C572" />
            <span>Welcome, <strong>{userName}</strong>님</span>
          </div>
        ) : (
          <Link to="/login" style={{ 
            backgroundColor: '#93C572', 
            color: '#3E2723', 
            padding: '6px 16px', 
            borderRadius: '20px', 
            textDecoration: 'none',
            fontSize: '13px',
            fontWeight: 'bold'
          }}>
            로그인
          </Link>
        )}

        {/* 관리자 페이지 이동 버튼 (구분선 추가) */}
        <div style={{ marginLeft: '10px', paddingLeft: '20px', borderLeft: '1px solid rgba(255,255,255,0.2)' }}>
          <Link to="/admin" style={{ 
            color: '#D7CCC8', 
            textDecoration: 'none', 
            fontSize: '12px',
            display: 'flex',
            alignItems: 'center',
            gap: '4px'
          }}>
            <Settings size={14} /> 관리자
          </Link>
        </div>
      </div>
    </nav>
  );
};

export default Navbar;