import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL;
const EVENT_ID = __ENV.EVENT_ID;
const TOKEN = __ENV.ACCESS_TOKEN;

export const options = {
    scenarios: {
        spike: {
            executor: "ramping-arrival-rate",
            startRate: 200,
            timeUnit: "1s",
            preAllocatedVUs: 500,
            maxVUs: 2000,
            stages: [
                { target: 500, duration: "30s" },
                { target: 2000, duration: "60s" },
                { target: 2000, duration: "30s" },
            ],
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.01"],
    },
};

export default function () {
    // 🔑 요청마다 고유 userId
    const userId = `user-${__VU}-${__ITER}-${Date.now()}`;

    const url = `${BASE_URL}/events/${EVENT_ID}/participations`;

    const payload = JSON.stringify({
        userId,
    });

    const params = {
        headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${TOKEN}`,
        },
        timeout: "5s",
    };

    const res = http.post(url, payload, params);

    // ✅ G2에서는 이것만 본다
    check(res, {
        "202 accepted": (r) => r.status === 202,
    });

    sleep(0.01);
}
