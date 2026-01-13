import React, { useState } from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';

import EventList from './pages/user/EventList'; 
import ParticipationHistory from './pages/user/ParticipationHistory';
import Result from './pages/user/Result';
import Login from './pages/user/Login';
import Navbar from './components/Navbar'; // 상단바 추가
import EventDetailHub from './pages/user/EventDetailHub';

// import AdminDashboard from './pages/admin/AdminDashboard';
// import EventCreate from './pages/admin/EventCreate';

function App() {
  const [userName, setUserName] = useState(null);
  const [history, setHistory] = useState([]);   // 신청 내역 저장소

  // 새로운 신청 내역을 추가
  const addHistory = (newEntry) => {
    // 기존 내역에 새 내역을 추가 (최신순으로 보이게 앞에 추가)
    setHistory((prevHistory) => [newEntry, ...prevHistory]);
  };
  
  return (
    <Router>
      <div className="App">
        {/* 주소에 따라 바뀌는 영역 */}
        <Navbar userName={userName} />
        <Routes>
          <Route path="/" element={<EventList />} />
          <Route path="/login" element={<Login onLoginSuccess={setUserName} />} />
          
          {/* 4. EventDetailHub에 addHistory 함수를 전달
             허브가 이 함수를 받아 내부의 Async나 Lottery 상세 페이지로 다시 던짐
          */}
          <Route 
            path="/event/:id" 
            element={<EventDetailHub addHistory={addHistory} />} 
          />

          {/* 5. 내역 페이지에 쌓인 history 데이터를 전달 */}
          <Route 
            path="/history" 
            element={<ParticipationHistory history={history} />} 
          />

          {/* 6. 결과 상세 (요청 ID를 주소에 담을 수 있게 설정) */}
          <Route path="/result/:requestId" element={<Result />} />
        </Routes>
      </div>
    </Router>
  );
}

export default App;