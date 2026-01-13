import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080',
  withCredentials: true // Spring에서 allowCredentials(true)를 했으므로 필수!
});

const Login = ({ onLoginSuccess }) => {
  const [isLoginMode, setIsLoginMode] = useState(true); // 로그인/회원가입 전환 상태
  const [nameInput, setNameInput] = useState("");      // 사용자 이름 입력 상태
  const navigate = useNavigate();
  const [userId, setUserId] = useState('')

  // 로그인 버튼 클릭 시 실행되는 함수
  const handleLogin = async (e) => {
    e.preventDefault();
    
    // 서버가 G0 단계에서 요구하는 'user-XXXX' 형식을 맞추기 위한 검사
    // if (!nameInput.startsWith("user-")) {
    //   alert("아이디는 'user-이름' 형식으로 입력해주세요! (예: user-asdf)");
    //   return;
    // }

    try {
      // 1. 서버에 로그인 요청
      const response = await api.post('/auth/login', { 
        userId: nameInput  // 사용자가 입력한 '이름'을 서버에 전송
      });
      
      // 2. 서버가 준 토큰(출입증) 보관
      const token = response.data.token;
      localStorage.setItem('accessToken', token); // 받은 토큰 저장
      alert('로그인 성공!');
      
      // 3. UI 업데이트 및 이동
      onLoginSuccess(nameInput);
      alert(`${nameInput}님, 서버 인증이 완료되었습니다!`);
      navigate('/');
    } catch (err) {
      console.error('로그인 중 에러 발생:', err);
      alert('로그인 실패');
    }
  };

  // 스타일 정의 (기존과 동일)
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
        {isLoginMode 
          ? '로그인하여 신청 내역을 확인하세요.' 
          : '회원가입 후 이벤트에 참여해보세요.'}
      </p>

      {/* 폼 제출 시 handleLogin 함수 실행 */}
      <form onSubmit={handleLogin}>
        {/* 이름을 입력받아 상단바에 넘겨주기 위한 input */}
        <input 
          type="text" 
          placeholder="사용자 이름을 입력하세요" 
          style={inputStyle} 
          value={nameInput}
          onChange={(e) => setNameInput(e.target.value)}
          required 
        />
        
        <input type="email" placeholder="이메일 주소" style={inputStyle} required />
        <input type="password" placeholder="비밀번호" style={inputStyle} required />
        
        {!isLoginMode && (
          <input type="password" placeholder="비밀번호 확인" style={inputStyle} required />
        )}

        <button type="submit" style={submitButtonStyle}>
          {isLoginMode ? '로그인' : '회원가입 시작하기'}
        </button>
      </form>

      <div style={toggleStyle} onClick={() => setIsLoginMode(!isLoginMode)}>
        {isLoginMode ? '계정이 없으신가요? 회원가입' : '이미 계정이 있나요? 로그인'}
      </div>

      <div style={{ 
        marginTop: '30px', 
        padding: '15px', 
        backgroundColor: '#f9f9f9', 
        borderRadius: '8px',
        fontSize: '12px',
        color: '#666',
        textAlign: 'left'
      }}>
        📌 <strong>로그인이 필요한 이유</strong>
        <ul style={{ paddingLeft: '20px', margin: '5px 0' }}>
          <li>이벤트 중복 참여 방지</li>
          <li>비동기 요청 결과(요청 ID) 영구 보관</li>
          <li>당첨 시 본인 확인 및 안내</li>
        </ul>
      </div>
    </div>
  );
};

export default Login;