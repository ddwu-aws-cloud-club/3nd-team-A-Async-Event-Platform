import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL;
const EVENT_ID = __ENV.EVENT_ID;
const TOKEN = __ENV.TOKEN;

export const options = {
    discardResponseBodies: true,
    scenarios: {
        rate_80: {
            executor: "constant-arrival-rate",
            rate: 80,
            timeUnit: "1s",
            duration: "5m",
            preAllocatedVUs: 200,
            maxVUs: 1500,
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.01"],
        http_req_duration: ["p(95)<3000"],
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