import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const EVENT_ID = __ENV.EVENT_ID || "EVT-004";
const VUS = Number(__ENV.VUS || 300);
const DURATION = __ENV.DURATION || "60s";

export const options = {
    scenarios: {
        spike: { executor: "constant-vus", vus: VUS, duration: DURATION },
    },
    thresholds: {
        http_req_failed: ["rate<0.01"],  // 실패율 1% 미만(가드레일)
    },
};

function safeJson(res) {
    try { return res.json(); } catch (_) { return null; }
}

export default function () {
    const url = `${BASE_URL}/events/${EVENT_ID}/participations`;

    const res = http.post(url, null, {
        headers: { "Content-Type": "application/json" },
        timeout: "5s",
    });

    const body = safeJson(res);

    check(res, {
        "status is 202": (r) => r.status === 202,
        "has requestId": (_) => body && typeof body.requestId === "string" && body.requestId.length > 0,
    });

    sleep(0.05);
}
