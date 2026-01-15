import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL;
const EVENT_ID = __ENV.EVENT_ID;

export const options = {
    discardResponseBodies: true,

    scenarios: {
        slo: {
            executor: "constant-arrival-rate",
            rate: 30,              // ✅ 초당 30건 (SLO 안정 구간)
            timeUnit: "1s",
            duration: "5m",
            preAllocatedVUs: 200,
            maxVUs: 800,           // ✅ Insufficient VUs 방지
        },
    },

    thresholds: {
        http_req_failed: ["rate<0.01"],     // 실패율 < 1%
        http_req_duration: ["p(95)<3000"],  // p95 < 3초 (SLO)
    },
};

export default function () {
    // ✅ 완전 유니크 userId (중복/락 문제 방지)
    const userId = `user-${__VU}-${__ITER}-${Date.now()}`;

    const res = http.post(
        `${BASE_URL}/events/${EVENT_ID}/participations`,
        JSON.stringify({ userId }),
        {
            headers: {
                "Content-Type": "application/json",
            },
            timeout: "15s",
        }
    );

    check(res, {
        "202 accepted": (r) => r.status === 202,
    });

    sleep(0.01);
}
