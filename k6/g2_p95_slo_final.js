import http from "k6/http";
import { check, sleep } from "k6";

/**
 * ===============================
 * ENV
 * ===============================
 * BASE_URL
 * EVENT_ID
 */

const BASE_URL = __ENV.BASE_URL;
const EVENT_ID = __ENV.EVENT_ID;

export const options = {
    discardResponseBodies: true,

    scenarios: {
        slo_measurement: {
            executor: "constant-arrival-rate",
            rate: 30,              // ✅ 초당 30 req (SLO 분모)
            timeUnit: "1s",
            duration: "5m",        // ✅ 충분한 샘플 수
            preAllocatedVUs: 200,
            maxVUs: 800,
        },
    },

    thresholds: {
        http_req_failed: ["rate<0.01"],

        // ✅ SLO 핵심 지표
        http_req_duration: ["p(95)<3000"], // p95 < 3s
    },
};

export default function () {
    const res = http.post(
        `${BASE_URL}/events/${EVENT_ID}/participations`,
        null,
        {
            timeout: "10s",
        }
    );

    check(res, {
        "202 accepted": (r) => r.status === 202,
    });

    // 살짝만 쉬어도 충분
    sleep(0.01);
}
