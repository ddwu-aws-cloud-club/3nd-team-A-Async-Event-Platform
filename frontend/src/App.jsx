import React, { useState } from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';

import EventList from './pages/user/EventList';
import ParticipationHistory from './pages/user/ParticipationHistory';
import EventDetailHub from './pages/user/EventDetailHub';
import EventDetailAsync from './pages/user/EventDetailAsync';
import Result from './pages/user/Result';
import Login from './pages/user/Login';
import Navbar from './components/Navbar';

import AdminLayout from './pages/admin/AdminLayout';
import AdminDashboard from './pages/admin/AdminDashboard';
import Analytics from './pages/admin/Analytics';
import DLQManager from './pages/admin/DLQManager';
import RequestDetail from './pages/admin/RequestDetail'

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
        <Routes>
          {/* 1. 사용자 레이아웃*/}
          <Route path="/*" element={
            <>
              <Navbar userName={userName} />
              <Routes>
                <Route path="/" element={<EventList />} />
                <Route path="/login" element={<Login onLoginSuccess={setUserName} />} />
                <Route path="/event/:id" element={<EventDetailHub addHistory={addHistory} />} />
                <Route path="/event/:id" element={<EventDetailAsync addHistory={addHistory} />} />
                <Route path="/history" element={<ParticipationHistory history={history} />} />
                <Route path="/result/:requestId" element={<Result />} />
              </Routes>
            </>
          } />

          {/* 2. 관리자 레이아웃 */}
          <Route path="/admin/*" element={
            <AdminLayout>
              <Routes>
                <Route path="/" element={<AdminDashboard />} />
                <Route path="/analytics" element={<Analytics />} />
                <Route path="/dlq" element={<DLQManager />} />
                <Route path="/search" element={<RequestDetail />} />
              </Routes>
            </AdminLayout>
          } />
        </Routes>
      </div>
    </Router>
  );
}

export default App;