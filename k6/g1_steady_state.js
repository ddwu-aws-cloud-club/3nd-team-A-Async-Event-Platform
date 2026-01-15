import http from "k6/http";
import { check, sleep } from "k6";

/**
 * =========================
 * 환경 변수
 * =========================
 */
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const EVENT_ID = __ENV.EVENT_ID || "EVT-008";
const VUS = Number(__ENV.VUS || 2000);
const DURATION = __ENV.DURATION || "5m"; // steady-state 유지시간

/**
 * =========================
 * Steady-State 시나리오
 * =========================
 */
export const options = {
    scenarios: {
        steady: {
            executor: "constant-vus",
            vus: VUS,
            duration: DURATION,
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.01"],
    },
};

/**
 * =========================
 * 유틸
 * =========================
 */
function randomUserId(vu) {
    return `user-steady-${vu}-${Math.random().toString(36).substring(2, 10)}`;
}

/**
 * =========================
 * VU별 토큰 캐시
 * =========================
 */
let token;

/**
 * =========================
 * VU 실행
 * =========================
 */
export default function () {
    // 1) VU별 최초 1회 로그인
    if (!token) {
        const userId = randomUserId(__VU);

        const loginRes = http.post(
            `${BASE_URL}/auth/login`,
            JSON.stringify({ userId }),
            { headers: { "Content-Type": "application/json" } }
        );

        check(loginRes, {
            "login status 200": (r) => r.status === 200,
            "has accessToken": (r) => {
                try {
                    return r.json("accessToken") !== undefined;
                } catch (e) {
                    return false;
                }
            },
        });

        token = loginRes.json("accessToken");
    }

    // 2) 참여 요청 (비동기 enqueue) - body 검증 X
    const res = http.post(
        `${BASE_URL}/events/${EVENT_ID}/participations`,
        null,
        { headers: { Authorization: `Bearer ${token}` } }
    );

    check(res, {
        "status is 202": (r) => r.status === 202,
    });

    sleep(0.1);
}
