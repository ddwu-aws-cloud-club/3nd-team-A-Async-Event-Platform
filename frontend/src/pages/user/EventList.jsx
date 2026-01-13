import React from 'react';
import { useNavigate } from 'react-router-dom';

import EventCard from '../../components/EventCard';

// 메인 랜딩 페이지 컴포넌트
function EventList() {
  const navigate = useNavigate(); // 페이지 이동 도와주는 도구 
  // const [events, setEvents] = useState([]); // 백엔드 데이터를 담을 상태

  // useEffect(() => {
  //   // 백엔드 API 호출
  //   axios.get('http://localhost:8080/events')
  //     .then(res => setEvents(res.data))
  //     .catch(err => console.error(err));
  // }, []);

  const eventList = [
    {
      id: 1,
      title: "두쫀쿠는 누가 가져갈것인가?",
      type: "FIRST_COME",
      deadline: "선착순 100명",
      status: "오픈"
    },
    {
      id: 2,
      title: "럭키드로우 이벤트",
      type: "LOTTERY",
      deadline: "2026-01-20 23:59",
      status: "오픈"
    }
  ];

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
        
        <div style={{ 
          display: 'flex', 
          gap: '20px', 
          flexWrap: 'wrap',
          justifyContent: 'center'
        }}>
         
          {eventList.map(event => (
            <div 
            key={event.eventId} // event.id 대신 event.eventId 사용
            // onClick={() => navigate(`/event/${event.eventId}`)}
            onClick={() => navigate(`/event/${event.id}`, { state: { eventData: event } })}
            style={{ cursor: 'pointer' }}
          >
            <EventCard 
              title={event.title}
              type={event.type}
              // deadline={event.eventType === 'FIRST_COME' 
              //       ? `선착순 ${event.capacityTotal}명` 
              //       : event.lotteryCutoffAt}
              // status={event.status === 'OPEN' ? '오픈' : '마감'}      
              deadline={event.deadline}
              status={event.status}
            />
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

export default EventList; // 다른 파일에서 부를 수 있게 