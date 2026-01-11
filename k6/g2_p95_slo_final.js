import http from "k6/http";
import { check, sleep } from "k6";

/**
 * ENV
 * BASE_URL=http://alb-async-ingest-xxxx.ap-northeast-2.elb.amazonaws.com
 * EVENT_ID=EVT-P95-1
 */
const BASE_URL = __ENV.BASE_URL;
const EVENT_ID = __ENV.EVENT_ID;

export const options = {
    discardResponseBodies: true,

    scenarios: {
        slo: {
            executor: "constant-arrival-rate",
            rate: 30,            // 초당 30 req (SLO용 안정 트래픽)
            timeUnit: "1s",
            duration: "5m",
            preAllocatedVUs: 200,
            maxVUs: 800,
        },
    },

    thresholds: {
        http_req_failed: ["rate<0.01"],      // 실패율 < 1%
        http_req_duration: ["p(95)<3000"],   // p95 < 3s (SLO 핵심)
    },
};

export default function () {
    /**
     * 중요:
     * - requestId는 서버에서 생성해도 되지만
     * - 명시적으로 보내도 문제 없음
     * - userId는 이제 멱등성과 무관
     */
    const payload = JSON.stringify({
        userId: `user-${__VU}-${__ITER}`, // 있어도 되고 없어도 됨
    });

    const res = http.post(
        `${BASE_URL}/events/${EVENT_ID}/participations`,
        payload,
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
