import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

import EventCard from '../../components/EventCard';

const api = axios.create({
  baseURL: 'http://alb-async-ingest-1521062058.ap-northeast-2.elb.amazonaws.com',
  withCredentials: true,
});

function EventList() {
  const navigate = useNavigate();
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchEvents = async () => {
      try {
        const accessToken = localStorage.getItem('accessToken');
        const tokenType = localStorage.getItem('tokenType') || 'Bearer';

        if (!accessToken) {
          // 토큰 없으면 로그인 유도(원하면 navigate('/login')로 보내도 됨)
          alert('로그인이 필요합니다.');
          setEvents([]);
          return;
        }

        const res = await api.get('/api/events', {
          headers: {
            Authorization: `${tokenType} ${accessToken}`,
          },
        });

        // res.data 가 배열이라고 가정
        setEvents(Array.isArray(res.data) ? res.data : []);
      } catch (err) {
        console.error('이벤트 목록 조회 실패:', err);

        const msg =
          err?.response?.data?.message ||
          err?.response?.data?.error ||
          err?.message ||
          '이벤트 목록 조회 실패';

        alert(msg);
      } finally {
        setLoading(false);
      }
    };

    fetchEvents();
  }, []);

  const toDeadlineText = (event) => {
    // FIRST_COME: 선착순 n명
    if (event.eventType === 'FIRST_COME') {
      return `선착순 ${event.capacityTotal ?? '-'}명`;
    }
    // LOTTERY: 마감 시각
    return event.lotteryCutoffAt ?? '-';
  };

  const toStatusText = (event) => {
    // 백엔드 status가 OPEN/CLOSED 등이라면 프론트 표시용으로 변환
    if (event.status === 'OPEN') return '오픈';
    if (event.status === 'CLOSED') return '마감';
    return event.status ?? '-';
  };

  return (
    <div style={{
      padding: '40px 20px',
      backgroundColor: '#f9f9f9',
      minHeight: '100vh',
      display: 'flex',
      justifyContent: 'center'
    }}>
      <div style={{ width: '100%', maxWidth: '1100px' }}>
        <h2 style={{ marginBottom: '30px', textAlign: 'left', paddingLeft: '10px' }}>
          1️⃣ 진행 중인 이벤트
        </h2>

        {loading ? (
          <div style={{ paddingLeft: '10px' }}>불러오는 중...</div>
        ) : (
          <div style={{
            display: 'flex',
            gap: '20px',
            flexWrap: 'wrap',
            justifyContent: 'center'
          }}>
            {events.map((event) => {
              const stableId = event.eventId ?? event.pk?.replace("EVENT#", "");
              const key = stableId ?? `${event.pk}#${event.sk}`;

              return (
                <div
                  key={key}
                  onClick={() => {
                    if (!stableId) return alert("id가 없는 이벤트입니다.");
                    navigate(`/event/${stableId}`); // ✅ state 안 넘김
                  }}
                  style={{ cursor: 'pointer' }}
                >
                  <EventCard
                    title={event.title ?? '(제목 없음)'}
                    type={event.eventType ?? 'UNKNOWN'}
                    deadline={toDeadlineText(event)}
                    status={toStatusText(event)}
                  />
                </div>
              );
            })}

            {!events.length && (
              <div style={{ paddingLeft: '10px' }}>현재 진행 중인 이벤트가 없습니다.</div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

export default EventList;
