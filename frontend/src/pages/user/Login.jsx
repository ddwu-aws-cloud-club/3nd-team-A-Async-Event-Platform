import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://alb-async-ingest-1521062058.ap-northeast-2.elb.amazonaws.com',
  withCredentials: true
});

const Login = ({ onLoginSuccess }) => {
  const [isLoginMode, setIsLoginMode] = useState(true);

  const [userId, setUserId] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();

    try {
      if (isLoginMode) {
        // ✅ 로그인
        const response = await api.post('/api/auth/login', {
          userId,
          password
        });

        const { tokenType, accessToken } = response.data || {};
        if (!accessToken) throw new Error("응답에 accessToken이 없습니다.");

        localStorage.setItem('accessToken', accessToken);
        localStorage.setItem('tokenType', tokenType || 'Bearer');

        onLoginSuccess?.(userId);

        alert('로그인 성공!');

        // ✅ admin이면 관리자 페이지(프론트 라우트)로
        if (userId.startsWith("admin")) {
          navigate('/admin/event');
        } else {
          navigate('/');
        }

      } else {
        // ✅ 회원가입
        if (password !== confirmPassword) {
          alert("비밀번호 확인이 일치하지 않습니다.");
          return;
        }

        await api.post('/api/auth/signup', {
          userId,
          password
        });

        alert("회원가입 성공! 이제 로그인 해주세요.");

        // 회원가입 후 로그인 모드로 전환 + 비번 확인 초기화
        setIsLoginMode(true);
        setConfirmPassword("");
      }

    } catch (err) {
      console.error('인증 중 에러 발생:', err);

      const msg =
        err?.response?.data?.message ||
        err?.response?.data?.error ||
        err?.message ||
        (isLoginMode ? '로그인 실패' : '회원가입 실패');

      alert(msg);
    }
  };

  // 스타일은 원본 유지
  const containerStyle = {
    maxWidth: '400px',
    margin: '100px auto',
    padding: '40px',
    border: '2px solid #5D4037',
    borderRadius: '16px',
    backgroundColor: '#fff',
    textAlign: 'center',
    boxShadow: '0 10px 25px rgba(0,0,0,0.1)'
  };

  const inputStyle = {
    width: '100%',
    padding: '12px',
    margin: '10px 0',
    borderRadius: '8px',
    border: '1px solid #ddd',
    boxSizing: 'border-box'
  };

  const submitButtonStyle = {
    width: '100%',
    padding: '12px',
    backgroundColor: '#93C572',
    color: '#3E2723',
    border: 'none',
    borderRadius: '8px',
    fontWeight: 'bold',
    fontSize: '16px',
    cursor: 'pointer',
    marginTop: '20px'
  };

  const toggleStyle = {
    marginTop: '20px',
    fontSize: '14px',
    color: '#666',
    cursor: 'pointer',
    textDecoration: 'underline'
  };

  return (
    <div style={containerStyle}>
      <h2 style={{ color: '#3E2723', marginBottom: '10px' }}>
        {isLoginMode ? '환영합니다!' : '첫 방문이신가요?'}
      </h2>
      <p style={{ fontSize: '14px', color: '#888', marginBottom: '30px' }}>
        {isLoginMode ? '로그인하여 신청 내역을 확인하세요.' : '회원가입 후 이벤트에 참여해보세요.'}
      </p>

      <form onSubmit={handleSubmit}>
        <input
          type="text"
          placeholder="userId를 입력하세요 (예: dyjung, admin_somang)"
          style={inputStyle}
          value={userId}
          onChange={(e) => setUserId(e.target.value)}
          required
        />

        {/* 이메일은 아직 미사용 */}
        <input type="email" placeholder="이메일 주소 (미사용)" style={inputStyle} />

        <input
          type="password"
          placeholder="비밀번호"
          style={inputStyle}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />

        {!isLoginMode && (
          <input
            type="password"
            placeholder="비밀번호 확인"
            style={inputStyle}
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            required
          />
        )}

        <button type="submit" style={submitButtonStyle}>
          {isLoginMode ? '로그인' : '회원가입 시작하기'}
        </button>
      </form>

      <div style={toggleStyle} onClick={() => setIsLoginMode(!isLoginMode)}>
        {isLoginMode ? '계정이 없으신가요? 회원가입' : '이미 계정이 있나요? 로그인'}
      </div>
    </div>
  );
};

export default Login;
