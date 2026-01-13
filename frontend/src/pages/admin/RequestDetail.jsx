const RequestDetail = ({ item }) => {
  return (
    <div className="p-6 bg-white border rounded-lg shadow-sm">
      <div className="flex justify-between items-center mb-6">
        <h3 className="text-lg font-bold">Request Detail: {item.requestId}</h3>
        <span className={`px-3 py-1 rounded-full text-sm ${getStatusBadgeColor(item.status)}`}>
          {item.status}
        </span>
      </div>

      <div className="grid grid-cols-2 gap-4 mb-8">
        <div>
          <p className="text-sm text-gray-500">Event Type</p>
          <p className="font-medium">{item.eventType}</p>
        </div>
        <div>
          <p className="text-sm text-gray-500">Result Code</p>
          <p className={`font-medium ${item.resultCode === 'SUCCESS' ? 'text-blue-600' : 'text-red-600'}`}>
            {item.resultCode}
          </p>
        </div>
      </div>

      {/* 시각적 타임라인 */}
      <div className="relative border-l-2 border-gray-200 ml-3 pl-6 space-y-6">
        <TimelineStep 
          label="요청 수신 (Queued)" 
          time={formatDate(item.queuedAt)} 
          isCompleted={true} 
        />
        <TimelineStep 
          label="처리 결과 (ResultCode)" 
          desc={item.resultCode} 
          isCompleted={!!item.resultCode} 
        />
        <TimelineStep 
          label="최종 UI 상태 (UiResult)" 
          desc={item.uiResult} 
          isCompleted={item.uiResult !== 'PENDING'} 
          isLast={true}
        />
      </div>
    </div>
  );
};