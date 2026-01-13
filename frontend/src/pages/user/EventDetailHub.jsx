import React from 'react';
import { useLocation } from 'react-router-dom';
import EventDetailAsync from './EventDetailAsync';
import EventDetailLottery from './EventDetailLottery';

// 1. App에서 준 addHistory 받음 
const EventDetailHub = ({ addHistory }) => {
  const location = useLocation();
  const event = location.state?.eventData; // EventList에서 넘겨준 데이터

  if (!event) return <div>이벤트를 찾을 수 없습니다.</div>;

  // 타입에 따라 다른 컴포넌트를 리턴함
  return event.type === "FIRST_COME" 
    ? <EventDetailAsync eventData={event} addHistory={addHistory} /> 
    : <EventDetailLottery eventData={event} addHistory={addHistory} />;
};

export default EventDetailHub;