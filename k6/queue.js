// [테스트 목적]
// 대기열(Queue) 서버만 격리하여 처리량과 응답 시간을 관찰하기 위함
//
// [시나리오]
// VU 1명 = 대기자 1명으로 두고 다음을 반복한다.
//   1. 대기열에 진입해 토큰을 받는다.
//   2. 서버가 내려주는 주기(retryAfterMs)에 맞춰 상태를 폴링한다.
//   3. 승격되면 다시 대기열에 REJOIN으로 줄을 서서 부하를 유지
//
// [.env]
// K6_WEB_DASHBOARD=true                                                  # 5665 실시간 웹 대시보드 노출
// K6_PROMETHEUS_RW_SERVER_URL=http://<prometheus-host>:9090/api/v1/write # Prometheus 원격 Write
// K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),avg,max"                     # 전송할 Trend 통계
// BASE_URL=https://...                                                   # 대기열 서버 URL
// CONCERT_SCHEDULE_ID=1                                                  # 콘서트 회차 ID
// TARGET=10000                                                           # 전체 VU 수
// RAMP_UP_DURATION=3s                                                    # ramp up 시간
// HOLD_DURATION=5m                                                       # 유지 시간
//
// [커맨드]
// set -a && source .env
// k6 run -o experimental-prometheus-rw queue.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 대기열 API 측정용 커스텀 Trend
const enterDuration = new Trend('enter_duration', true);     // 대기열 입장
const statusDuration = new Trend('status_duration', true);   // 대기열 폴링

const errorCount = new Counter('error_count');

export const options = {
  stages: [
    { target: __ENV.TARGET, duration: __ENV.RAMP_UP_DURATION },   // Spike 구간
    { target: __ENV.TARGET, duration: __ENV.HOLD_DURATION },      // 유지 구간
  ],
  systemTags: ['proto', 'subproto', 'status', 'method', 'name', 'group', 'check', 'error', 'error_code', 'tls_version', 'scenario', 'service', 'expected_response'],
};

const USER_ID = __VU;
let queueToken = null;

const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

export default function () {
  if (queueToken === null) {
    enterQueue();
  }
  polling();
}

// 대기열 입장
function enterQueue() {

  const enterRes = http.post(
    `${BASE_URL}/api/v1/queue/enter?userId=${USER_ID}`,
    JSON.stringify({ concertScheduleId: __ENV.CONCERT_SCHEDULE_ID, enterType: 'REJOIN' }),
    { ...JSON_HEADERS, tags: { name: 'POST /queue/enter' } }
  );
  enterDuration.add(enterRes.timings.duration);

  if (!check(enterRes, { 'enter 201': (r) => r.status === 201 })) {
    errorCount.add(1);
    return;
  }

  queueToken = enterRes.json('result.token');
}

// 폴링
function polling() {

  const statusRes = http.get(
    `${BASE_URL}/api/v1/queue/status?userId=${USER_ID}&token=${queueToken}`,
    { tags: { name: 'GET /queue/status' } }
  );
  statusDuration.add(statusRes.timings.duration);

  if (!check(statusRes, { 'status 200': (r) => r.status === 200 })) {
    errorCount.add(1);
    queueToken = null;
    return;
  }

  if (statusRes.json('result.isActive') === true) {
    queueToken = null;
    return;
  }

  sleep((statusRes.json('result.retryAfterMs') || 1000) / 1000);  // 서버가 내려주는 주기로 폴링
}
