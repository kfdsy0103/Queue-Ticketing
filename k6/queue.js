// [테스트 목적]
// 대기열 입장인 enter API, 대기열 순번 조회인 status API를 호출하여 대기열 서버의 TPS 측정을 하기 위함
//
// [.env]
// 필요한 환경변수와 예시 값은 env-example.md 참고
//
// [커맨드]
// set -a && source .env
// k6 run -o experimental-prometheus-rw queue.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CONCERT_SCHEDULE_ID = Number(__ENV.CONCERT_SCHEDULE_ID || '1');

// 대기열 API 측정용 커스텀 Trend
const enterDuration = new Trend('enter_duration', true);      // 대기열 입장
const statusDuration = new Trend('status_duration', true);    // 대기열 상태 조회

const errorCount = new Counter('error_count');

export const options = {
  scenarios: {
    main: {
      executor: 'ramping-vus',
      stages: [
        { target: __ENV.TARGET, duration: __ENV.RAMP_UP_DURATION },   // Spike 구간
        { target: __ENV.TARGET, duration: __ENV.HOLD_DURATION },      // 유지 구간
        { target: 0, duration: '30s' },                               // graceful 종료
      ],
      tags: { phase: 'main' },
    },
  },
  systemTags: ['proto', 'subproto', 'status', 'method', 'name', 'group', 'check', 'error', 'error_code', 'tls_version', 'scenario', 'service', 'expected_response'],
};

const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

const USER_ID = __VU;

export default function () {

  // 1. 대기열 입장
  let queueToken = enterQueue();
  if (queueToken === null) {
    return;
  }

  // 2. 대기열 상태 폴링
  polling(queueToken);
}

// 대기열 입장
function enterQueue() {

  const enterRes = http.post(
    `${BASE_URL}/api/v1/queue/enter?userId=${USER_ID}`,
    JSON.stringify({ concertScheduleId: CONCERT_SCHEDULE_ID, enterType: 'REJOIN' }),
    { ...JSON_HEADERS, tags: { name: 'POST /queue/enter' } }
  );
  enterDuration.add(enterRes.timings.duration);

  if (!check(enterRes, { 'enter 201': (r) => r.status === 201 })) {
    errorCount.add(1);
    return null;
  }

  return enterRes.json('result.token');
}

// 대기열 상태 폴링
function polling(queueToken) {

  while (true) {
    const statusRes = http.get(
      `${BASE_URL}/api/v1/queue/status?userId=${USER_ID}&token=${queueToken}`,
      { tags: { name: 'GET /queue/status' } }
    );
    statusDuration.add(statusRes.timings.duration);

    if (!check(statusRes, { 'status 200': (r) => r.status === 200 })) {
      errorCount.add(1);
      return;
    }
    if (statusRes.json('result.isActive') === true) {
      return;
    }

    sleep((statusRes.json('result.retryAfterMs') || 1000) / 1000);  // 서버가 내려주는 주기로 폴링
  }
}
