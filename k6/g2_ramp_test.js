import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL;
const EVENT_ID = __ENV.EVENT_ID;

export const options = {
    discardResponseBodies: true,

    scenarios: {
        ramp_test: {
            executor: "ramping-arrival-rate",
            timeUnit: "1s",
            stages: [
                { target: 30, duration: "1m" },  // G2 안정 구간
                { target: 60, duration: "1m" },
                { target: 120, duration: "1m" },
                { target: 240, duration: "1m" },
                { target: 300, duration: "1m" }, // 한계 탐색
            ],
            preAllocatedVUs: 200,
            maxVUs: 1000,
        },
    },

    thresholds: {
        http_req_failed: ["rate<0.01"],
        http_req_duration: ["p(95)<3000"], // 깨지기 시작하는 지점 관찰
    },
};

export default function () {
    const userId = `k6-user-${__VU}-${__ITER}`;

    const res = http.post(
        `${BASE_URL}/events/${EVENT_ID}/participations`,
        null,
        {
            timeout: "20s",
            headers: { "X-Test-User": userId },
        }
    );

    check(res, {
        "202 accepted": (r) => r.status === 202,
    });

    sleep(0.01);
}
