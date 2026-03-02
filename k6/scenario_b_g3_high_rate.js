import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL;
const EVENT_ID = __ENV.EVENT_ID;
const TOKEN = __ENV.TOKEN;

export const options = {
    discardResponseBodies: true,
    scenarios: {
        g3_high_rate: {
            executor: "constant-arrival-rate",
            rate: Number(__ENV.RATE || 300),
            timeUnit: "1s",
            duration: __ENV.DURATION || "10m",
            preAllocatedVUs: 500,
            maxVUs: 3000,
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.01"], // 실패율 1% 미만
        http_req_duration: ["p(95)<3000"], // 참고용
    },
};

function buildHeaders(userId) {
    const headers = {
        "Content-Type": "application/json",
        "X-User-Id": userId,
    };

    if (TOKEN) {
        headers.Authorization = `Bearer ${TOKEN}`;
    }
    return headers;
}

export default function () {
    const userId = `user-${__VU}-${__ITER}-${Date.now()}`;

    // 현재 배포된 API는 body가 필수로 보이지 않지만,
    // 안전하게 빈 JSON을 보냄
    const payload = JSON.stringify({});

    const res = http.post(
        `${BASE_URL}/api/events/${EVENT_ID}/apply?eventType=FIRST_COME`,
        payload,
        { headers: buildHeaders(userId) }
    );

    check(res, {
        "status is 202": (r) => r.status === 202,
    });
}