import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import axios from "axios";

const api = axios.create({
  baseURL: "http://alb-async-ingest-1521062058.ap-northeast-2.elb.amazonaws.com",
  withCredentials: true,
});

const EventDetailAsync = ({ addHistory }) => {
  const { id } = useParams();         // ✅ /event/:id
  const navigate = useNavigate();

  const [event, setEvent] = useState(null);
  const [loading, setLoading] = useState(true);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isApplied, setIsApplied] = useState(false);
  const [requestId, setRequestId] = useState("");
  const [errorMsg, setErrorMsg] = useState("");

  const authHeader = () => {
    const accessToken = localStorage.getItem("accessToken");
    const tokenType = localStorage.getItem("tokenType") || "Bearer";
    return { Authorization: `${tokenType} ${accessToken}` };
  };

  // ✅ 1) 상세 조회
  useEffect(() => {
    const fetchDetail = async () => {
      try {
        setLoading(true);
        setErrorMsg("");

        const res = await api.get(`/api/events/${id}`, {
          headers: authHeader(),
        });

        setEvent(res.data);
      } catch (err) {
        const msg =
          err?.response?.data?.message ||
          err?.response?.data?.error ||
          err?.message ||
          "이벤트 상세 조회 실패";
        setErrorMsg(msg);
      } finally {
        setLoading(false);
      }
    };

    fetchDetail();
  }, [id]);

  // ✅ 2) 참여하기 (apply)
  const handleApply = async () => {
    if (isSubmitting) return;
    setIsSubmitting(true);
    setErrorMsg("");

    try {
      const res = await api.post(`/api/events/${id}/apply`, {}, {
        headers: authHeader(),
      });

      const { requestId, isDuplicate } = res.data || {};

      if (!requestId) throw new Error("응답에 requestId가 없습니다.");

      setRequestId(requestId);
      setIsApplied(true);

      // ✅ 중복이면 UX를 다르게
      if (isDuplicate) {
        alert("이미 신청한 이벤트입니다.");
      } else {
        alert("✅ 접수가 완료되었습니다. (202 Accepted)");
      }

      // ✅ 히스토리에 기록 (원하면 isDuplicate=false일 때만)
      addHistory?.({
        id: requestId,
        title: event?.title ?? `EVENT ${id}`,
        status: "RECEIVED",
        result: "PROCESSING",
        appliedAt: new Date().toLocaleString(),
      });
    } catch (err) {
      const msg =
        err?.response?.data?.message ||
        err?.response?.data?.error ||
        err?.message ||
        "참여 실패";
      setErrorMsg(msg);
      alert(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (loading) return <div style={{ padding: 40 }}>불러오는 중...</div>;
  if (!event) return <div style={{ padding: 40, color: "red" }}>{errorMsg}</div>;

  const eventType = event.eventType; // FIRST_COME / LOTTERY
  const deadlineText =
    eventType === "FIRST_COME"
      ? `선착순 ${event.capacityTotal ?? "-"}명`
      : (event.lotteryCutoffAt ?? "-");

  return (
    <div style={{ padding: "40px 20px", maxWidth: 600, margin: "0 auto", textAlign: "center" }}>
      <h2>{event.title ?? `이벤트 ${id}`}</h2>

      <div style={{ background: "#f0f4f8", padding: 20, borderRadius: 12, marginBottom: 20, textAlign: "left" }}>
        <div>방식: <b>{eventType}</b></div>
        <div>마감/정원: <b>{deadlineText}</b></div>
        <div>상태: <b>{event.status ?? "-"}</b></div>
      </div>

      {errorMsg && <div style={{ color: "red", marginBottom: 12 }}>{errorMsg}</div>}

      {!isApplied ? (
        <button
          onClick={handleApply}
          disabled={isSubmitting}
          style={{
            width: "100%",
            padding: "15px 30px",
            borderRadius: 8,
            fontWeight: "bold",
            fontSize: 18,
            cursor: isSubmitting ? "not-allowed" : "pointer",
            backgroundColor: "#93C572",
            border: "2px solid #5D4037",
          }}
        >
          {isSubmitting ? "요청 처리 중..." : "참여하기"}
        </button>
      ) : (
        <div>
          <button disabled style={{ width: "100%", padding: "15px 30px", borderRadius: 8, fontWeight: "bold" }}>
            이미 신청된 이벤트입니다
          </button>

          <div style={{ marginTop: 20, padding: 15, border: "1px dashed #5D4037", borderRadius: 8 }}>
            요청 ID: <b>{requestId}</b>
            <button
              onClick={() => navigator.clipboard.writeText(requestId)}
              style={{ marginLeft: 10, padding: "2px 8px", fontSize: 12, cursor: "pointer" }}
            >
              복사
            </button>
          </div>

          {/* 원하면 결과 페이지 이동 버튼 */}
          <button
            onClick={() => navigate(`/result/${requestId}`)}
            style={{ marginTop: 12, padding: "10px 14px", cursor: "pointer" }}
          >
            결과 확인하러 가기
          </button>
        </div>
      )}

      <p style={{ marginTop: 20, fontSize: 13, color: "#888" }}>
        * 202 Accepted: 요청이 시스템에 안전하게 전달되었습니다.
      </p>
    </div>
  );
};

export default EventDetailAsync;
