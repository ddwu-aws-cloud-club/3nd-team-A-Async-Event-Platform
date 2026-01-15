import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const EVENT_ID = __ENV.EVENT_ID || "EVT-009";
const VUS = Number(__ENV.VUS || 300);
const DURATION = __ENV.DURATION || "60s";

export const options = {
    scenarios: {
        spike: {
            executor: "constant-vus",
            vus: VUS,
            duration: DURATION,
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.01"],
    },
};

function safeJson(res) {
    try {
        return res.json();
    } catch (e) {
        return null;
    }
}

// ✅ VU마다 고정 userId 하나 배정 (멱등성 회피)
function vuUserId() {
    const n = String(__VU).padStart(6, "0"); // __VU는 1부터 시작
    return `user-${n}`; // user-000001 ...
}

// ✅ VU별 토큰 캐시
let token = null;

function loginIfNeeded() {
    if (token) return token;

    const userId = vuUserId();
    const res = http.post(
        `${BASE_URL}/auth/login`,
        JSON.stringify({ userId }),
        {
            headers: { "Content-Type": "application/json" },
            timeout: "5s",
        }
    );

    const body = safeJson(res);

    const ok = check(res, {
        "login 200": (r) => r.status === 200,
        "login has accessToken": () => body && typeof body.accessToken === "string" && body.accessToken.length > 0,
    });

    if (!ok) {
        // 로그인 실패면 참여 요청은 전부 401이라 의미 없음 → 잠깐 쉬고 종료 느낌
        sleep(1);
        return null;
    }

    token = body.accessToken;
    return token;
}

export default function () {
    const t = loginIfNeeded();
    if (!t) return;

    const url = `${BASE_URL}/events/${EVENT_ID}/participations`;

    const res = http.post(url, null, {
        headers: {
            "Authorization": `Bearer ${t}`,
        },
        timeout: "5s",
    });

    const body = safeJson(res);

    check(res, {
        "status is 202": (r) => r.status === 202,
        "has requestId": () =>
            body &&
            typeof body.requestId === "string" &&
            body.requestId.length > 0,
    });

    sleep(0.05);
}
