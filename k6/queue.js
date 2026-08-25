// [테스트 목적]
// 대기열 입장인 enter API, 대기열 순번 조회인 status API를 호출하여 대기열 서버의 TPS 측정을 하기 위함
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
const CONCERT_SCHEDULE_ID = Number(__ENV.CONCERT_SCHEDULE_ID || '1');

// 대기열 API 측정용 커스텀 Trend
const enterDuration = new Trend('enter_duration', true);      // 대기열 입장
const statusDuration = new Trend('status_duration', true);    // 대기열 상태 조회

const errorCount = new Counter('error_count');

export const options = {
  scenarios: {
    // 워밍업: JIT 컴파일·커넥션 풀·DB 커넥션·캐시를 데우는 구간.
    // phase 태그를 붙여, 이 구간의 느린 응답이 지표와 threshold 에 섞이지 않도록 분리한다.
    warmup: {
      executor: 'constant-vus',
      vus: 5,
      duration: '30s',
      tags: { phase: 'warmup' },
    },
    // 실제 측정 구간. 워밍업이 끝나는 30초 지점부터 시작하며,
    // startVUs 를 워밍업 VU 수에 맞춰 0 으로 떨어뜨렸다 다시 올리지 않는다.
    main: {
      executor: 'ramping-vus',
      startTime: '30s',
      startVUs: 5,
      stages: [
        { target: __ENV.TARGET, duration: __ENV.RAMP_UP_DURATION },   // Spike 구간
        { target: __ENV.TARGET, duration: __ENV.HOLD_DURATION },      // 유지 구간
        { target: 0, duration: '30s' },                               // graceful 종료: 진행 중인 요청을 마치고 내려간다
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
