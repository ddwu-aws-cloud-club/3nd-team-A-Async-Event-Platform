import http from "k6/http";
import { check, sleep } from "k6";

/**
 ENV
 BASE_URL
 EVENT_ID
 */
const BASE_URL = __ENV.BASE_URL;
const EVENT_ID = __ENV.EVENT_ID;

export const options = {
    discardResponseBodies: false, // 디버깅 중엔 false

    scenarios: {
        slo_measurement: {
            executor: "constant-arrival-rate",
            rate: 30, // 초당 30 req
            timeUnit: "1s",
            duration: "5m",
            preAllocatedVUs: 200,
            maxVUs: 800,
        },
    },

    thresholds: {
        http_req_failed: ["rate<0.01"],
        http_req_duration: ["p(95)<3000"], // p95 < 3s
    },
};

export default function () {
    // 요청마다 다른 userId (멱등키 충돌 방지)
    const userId = `k6-user-${__VU}-${__ITER}`;

    const res = http.post(
        `${BASE_URL}/events/${EVENT_ID}/participations`,
        null,
        {
            timeout: "15s",
            headers: {
                "X-Test-User": userId,
            },
        }
    );

    check(res, {
        "202 accepted": (r) => r.status === 202,
        "body has requestId": (r) =>
            r.body && r.body.includes("requestId"),
    });

    sleep(0.01);
}
