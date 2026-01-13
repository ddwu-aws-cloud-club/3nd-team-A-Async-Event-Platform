import http from "k6/http";
import { check, sleep } from "k6";
import { Counter } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL;
const EVENT_ID = __ENV.EVENT_ID;

// 🔎 실패 원인 분류용 커스텀 메트릭
export const timeout_errors = new Counter("timeout_errors");
export const status_0_errors = new Counter("status_0_errors");
export const status_429_errors = new Counter("status_429_errors");
export const status_5xx_errors = new Counter("status_5xx_errors");

export const options = {
    discardResponseBodies: true,

    scenarios: {
        g3_backlog_build: {
            executor: "constant-arrival-rate",
            rate: Number(__ENV.RATE || 80),
            timeUnit: "1s",
            duration: __ENV.DURATION || "5m",
            preAllocatedVUs: 300,
            maxVUs: 1500,
        },
    },

    thresholds: {
        http_req_failed: ["rate<0.01"],
        http_req_duration: ["p(95)<3000"],

        // 🔎 원인별 threshold (테스트용)
        timeout_errors: ["count<50"],
        status_0_errors: ["count<50"],
        status_429_errors: ["count<1"],
        status_5xx_errors: ["count<1"],
    },
};

export default function () {
    const userId = `k6-user-${__VU}-${__ITER}-${Date.now()}`;

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

    // ✅ 정상 응답 체크
    const ok = check(res, {
        "202 accepted": (r) => r && r.status === 202,
    });

    // ❌ 실패 원인 분류
    if (!ok) {
        let d = 0;
        if (res && res.timings && typeof res.timings.duration === "number") {
            d = res.timings.duration;
        }

        if (!res || res.status === 0) {
            status_0_errors.add(1);

            // timeout 근처면 timeout으로도 집계
            if (d >= 14900) {
                timeout_errors.add(1);
            }
        } else if (res.status === 429) {
            status_429_errors.add(1);
        } else if (res.status >= 500) {
            status_5xx_errors.add(1);
        }
    }

    sleep(0.01);
}
