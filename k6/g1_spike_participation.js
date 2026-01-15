import http from "k6/http";
import { check, sleep } from "k6";

/**
 * =========================
 * 환경 변수
 * =========================
 */
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const EVENT_ID = __ENV.EVENT_ID || "EVT-009";
const VUS = Number(__ENV.VUS || 2000);
const DURATION = __ENV.DURATION || "90s";

/**
 * =========================
 * G1 스파이크 시나리오
 * =========================
 * - 빠른 유입
 * - 일정 시간 유지
 * - Worker가 처리율 유지하는지 관찰
 */
export const options = {
    scenarios: {
        spike: {
            executor: "ramping-vus",
            startVUs: 0,
            stages: [
                { duration: "10s", target: VUS },     // 급격한 증가
                { duration: DURATION, target: VUS },  // 유지
                { duration: "10s", target: 0 },       // 급격한 감소
            ],
            gracefulRampDown: "30s",
        },
    },
    thresholds: {
        // ✅ G1에서는 “죽지 않는지”만 본다
        http_req_failed: ["rate<0.01"],
    },
};

/**
 * =========================
 * 유틸
 * =========================
 */
function randomUserId(vu) {
    return `user-g1-${vu}-${Math.random().toString(36).substring(2, 10)}`;
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
    /**
     * 1️⃣ VU별 최초 1회 로그인
     */
    if (!token) {
        const userId = randomUserId(__VU);

        const loginRes = http.post(
            `${BASE_URL}/auth/login`,
            JSON.stringify({ userId }),
            {
                headers: {
                    "Content-Type": "application/json",
                },
            }
        );

        check(loginRes, {
            "login status 200": (r) => r.status === 200,
            "has accessToken": (r) => r.json("accessToken") !== undefined,
        });

        token = loginRes.json("accessToken");
    }

    /**
     * 2️⃣ 참여 요청 (비동기 enqueue)
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
        "status is 202": (r) => r.status === 202,
        "has requestId": (r) => r.json("requestId") !== undefined,
    });

    /**
     * Worker 관찰을 위한 완급 조절
     */
    sleep(0.1);
}
