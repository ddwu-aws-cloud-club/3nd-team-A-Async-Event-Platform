import http from "k6/http";
import { check, sleep } from "k6";

/**
 * =========================
 * 환경 변수
 * =========================
 */
const BASE_URL = __ENV.BASE_URL;
const EVENT_ID = __ENV.EVENT_ID;

// ⚠️ 일부러 작게
const VUS = Number(__ENV.VUS || 50);
const DURATION = __ENV.DURATION || "60s";

/**
 * =========================
 * 시나리오
 * =========================
 * - 낮은 동시성
 * - 일정 속도로 유입
 * - Queue가 폭증하지 않게 유지
 */
export const options = {
    scenarios: {
        baseline: {
            executor: "constant-vus",
            vus: VUS,
            duration: DURATION,
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.01"], // 안정성만 체크
    },
};

/**
 * =========================
 * 유틸
 * =========================
 */
function randomUserId(vu) {
    return `user-g2-${vu}-${Math.random().toString(36).substring(2, 8)}`;
}

let token;

export default function () {
    /**
     * 1️⃣ VU별 최초 1회 로그인
     */
    if (!token) {
        const userId = randomUserId(__VU);

        const loginRes = http.post(
            `${BASE_URL}/auth/login`,
            JSON.stringify({ userId }),
            { headers: { "Content-Type": "application/json" } }
        );

        check(loginRes, {
            "login 200": (r) => r.status === 200,
        });

        token = loginRes.json("accessToken");
    }

    /**
     * 2️⃣ 참여 요청 (enqueue)
     */
    const res = http.post(
        `${BASE_URL}/events/${EVENT_ID}/participations`,
        null,
        {
            headers: {
                Authorization: `Bearer ${token}`,
            },
        }
    );

    check(res, {
        "202 accepted": (r) => r.status === 202,
        "has requestId": (r) => r.json("requestId") !== undefined,
    });

    /**
     * 너무 빨라지지 않게
     * → Worker가 즉시 따라잡는 수준 유지
     */
    sleep(0.2);
}
