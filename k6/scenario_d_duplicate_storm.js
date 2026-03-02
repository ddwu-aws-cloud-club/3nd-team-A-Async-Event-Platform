import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL;
const EVENT_ID = __ENV.EVENT_ID;
const TOKEN = __ENV.TOKEN;
const USER_POOL_SIZE = Number(__ENV.USER_POOL_SIZE || 50);

export const options = {
    discardResponseBodies: true,
    scenarios: {
        duplicate_storm: {
            executor: "constant-arrival-rate",
            rate: Number(__ENV.RATE || 200),
            timeUnit: "1s",
            duration: __ENV.DURATION || "10m",
            preAllocatedVUs: 300,
            maxVUs: 2000,
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.01"],
    },
};

function buildHeaders(userId) {
    const headers = {
        "Content-Type": "application/json",
        "X-User-Id": userId,
    };

    if (TOKEN) {
        headers.Authorization = `Bearer ${TOKEN}`;
    }
    return headers;
}

export default function () {
    const idx = Math.floor(Math.random() * USER_POOL_SIZE);
    const userId = `dup-user-${idx}`;
    const payload = JSON.stringify({});

    const res = http.post(
        `${BASE_URL}/api/events/${EVENT_ID}/apply?eventType=FIRST_COME`,
        payload,
        { headers: buildHeaders(userId) }
    );

    check(res, {
        "status is 202": (r) => r.status === 202,
    });
}