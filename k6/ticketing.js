// [테스트 목적]
// '티켓팅' 행위와 관련된 API들만 실행, 목표 응답 시간을 만족하는 TPS를 측정하여 대기열에서 사용자를 꺼내주는 스케쥴러의 처리량 산정을 위함
//
// [.env]
// K6_WEB_DASHBOARD=true                                                  # 5665 실시간 웹 대시보드 노출
// K6_PROMETHEUS_RW_SERVER_URL=http://<prometheus-host>:9090/api/v1/write # Prometheus 원격 Write
// K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),avg,max"                     # 전송할 Trend 통계
// BASE_URL=https://...                                                   # API 서버 URL (ALB)
// CONCERT_ID=1                                                           # 콘서트 ID
// CONCERT_SCHEDULE_ID=1                                                  # 콘서트 회차 ID
// SEAT_COUNT=3000                                                        # 전체 좌석 수
// TARGET=200                                                             # 전체 VU 수
// RAMP_UP_DURATION=1m                                                    # ramp up 시간
// HOLD_DURATION=5m                                                       # 유지 시간
//
// [커맨드]
// set -a && source .env
// k6 run -o experimental-prometheus-rw ticketing.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CONCERT_ID = __ENV.CONCERT_ID || '1';
const CONCERT_SCHEDULE_ID = Number(__ENV.CONCERT_SCHEDULE_ID || '1');
const SEAT_COUNT = Number(__ENV.SEAT_COUNT || '3000');

// 예매 관련 API 측정용 커스텀 Trend
const concertDuration = new Trend('concert_duration', true);                  // 콘서트 관련 부가 정보
const concertScheduleDuration = new Trend('concert_schedule_duration', true); // 콘서트 회차 관련 부가 정보
const enterDuration = new Trend('enter_duration', true);                      // 대기열 진입
const statusDuration = new Trend('status_duration', true);                    // 대기열 상태 조회
const seatsDuration = new Trend('seats_duration', true);                      // 좌석 검색
const occupyDuration = new Trend('occupy_duration', true);                    // 좌석 점유

const errorCount = new Counter('error_count');          // 예상치 못한 요청 실패

export const options = {
  stages: [
    { target: __ENV.TARGET, duration: __ENV.RAMP_UP_DURATION },   // 램프업 시간
    { target: __ENV.TARGET, duration: __ENV.HOLD_DURATION },      // 유지 시간 (P95는 이 구간으로 판단)
  ],
  // SLO 목표: 전체 및 API별 P95 200ms
  thresholds: {
    http_req_duration: ['p(95)<200'],          // 전체
    concert_duration: ['p(95)<200'],           // GET /concerts/{id}
    concert_schedule_duration: ['p(95)<200'],  // GET /concerts/{id}/concert-schedules/{id}
    enter_duration: ['p(95)<200'],             // POST /queue/enter, POST /queue/takeover
    status_duration: ['p(95)<200'],            // GET /queue/status
    seats_duration: ['p(95)<200'],             // GET /concert-schedules/{id}/schedule-seats
    occupy_duration: ['p(95)<200'],            // POST /concert-schedules/{id}/schedule-seats/occupy
  },
  systemTags: ['proto', 'subproto', 'status', 'method', 'name', 'group', 'check', 'error', 'error_code', 'tls_version', 'scenario', 'service', 'expected_response'],
};

// 1~2개의 좌석을 랜덤 선택하는 유틸 메서드
function pickRandomSeatIds() {
  const count = Math.floor(Math.random() * 2) + 1;
  const seatIds = new Set();
  while (seatIds.size < count) {
    seatIds.add(Math.floor(Math.random() * SEAT_COUNT) + 1);
  }
  return [...seatIds];
}

const USER_ID = __VU;
let queueToken = null;
let isActive = false;

const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

export default function () {

  if (!isActive) {
    enterQueue();
    polling();
  }

  additionalAPIs();
  searchSeats();
  occupySeats();
}

// 대기열 진입
function enterQueue() {

  const enterRes = http.post(
    `${BASE_URL}/api/v1/queue/enter?userId=${USER_ID}`,
    JSON.stringify({ concertScheduleId: CONCERT_SCHEDULE_ID, enterType: 'JOIN' }),
    { ...JSON_HEADERS, tags: { name: 'POST /queue/enter' } }
  );
  enterDuration.add(enterRes.timings.duration);

  // 이미 참여 중이면 물려받기
  if (enterRes.status === 409) {
    takeover();
    return;
  }

  // 입장 성공
  if (!check(enterRes, { 'enter 201': (r) => r.status === 201 })) {
    errorCount.add(1);
    return;
  }

  queueToken = enterRes.json('result.token');
  isActive = false;
}

// 대기열 이어받기
function takeover() {

  const takeoverRes = http.post(
    `${BASE_URL}/api/v1/queue/takeover?userId=${USER_ID}`,
    JSON.stringify({ concertScheduleId: CONCERT_SCHEDULE_ID }),
    { ...JSON_HEADERS, tags: { name: 'POST /queue/takeover' } }
  );
  enterDuration.add(takeoverRes.timings.duration);

  if (!check(takeoverRes, { 'takeover 201': (r) => r.status === 201 })) {
    errorCount.add(1);
    return;
  }

  queueToken = takeoverRes.json('result.token');
  isActive = takeoverRes.json('result.isActive') === true;
}

// Active로 승격될 때까지 status 폴링
function polling() {

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
      isActive = true;
      return;
    }

    sleep((statusRes.json('result.retryAfterMs') || 1000) / 1000);  // 서버가 내려주는 주기로 폴링
  }
}

// 콘서트 및 회차 정보와 관련된 부수적인 조회 API
function additionalAPIs() {

  const responses = http.batch([
    {
      method: 'GET',
      url: `${BASE_URL}/api/v1/concerts/${CONCERT_ID}?userId=${USER_ID}`,
      params: { tags: { name: 'GET /concerts/{id}' } },
    },
    {
      method: 'GET',
      url: `${BASE_URL}/api/v1/concerts/${CONCERT_ID}/concert-schedules/${CONCERT_SCHEDULE_ID}?userId=${USER_ID}`,
      params: { tags: { name: 'GET /concerts/{id}/concert-schedules/{id}' } },
    },
  ]);

  concertDuration.add(responses[0].timings.duration);
  concertScheduleDuration.add(responses[1].timings.duration);

  if (!check(responses[0], { 'concert 200': (r) => r.status === 200 })) {
    errorCount.add(1);
  }
  if (!check(responses[1], { 'concert schedule 200': (r) => r.status === 200 })) {
    errorCount.add(1);
  }
}

// 전체 좌석 정보 조회
function searchSeats() {

  const seatsRes = http.get(
    `${BASE_URL}/api/v1/concert-schedules/${CONCERT_SCHEDULE_ID}/schedule-seats?userId=${USER_ID}&token=${queueToken}`,
    { tags: { name: 'GET /concert-schedules/{id}/schedule-seats' } }
  );
  seatsDuration.add(seatsRes.timings.duration);

  if (!check(seatsRes, { 'seats 200': (r) => r.status === 200 })) {
    errorCount.add(1);
  }

  sleep(1);
}

// 좌석 점유 시도
function occupySeats() {

  const occupyRes = http.post(
    `${BASE_URL}/api/v1/concert-schedules/${CONCERT_SCHEDULE_ID}/schedule-seats/occupy?userId=${USER_ID}`,
    JSON.stringify({ scheduleSeatIds: pickRandomSeatIds(), token: queueToken }),
    { ...JSON_HEADERS, tags: { name: 'POST /concert-schedules/{id}/schedule-seats/occupy' } }
  );
  occupyDuration.add(occupyRes.timings.duration);

  // 좌석 경합은 정상
  if (!check(occupyRes, { 'occupy 200 or conflict': (r) => r.status === 200 || r.status === 409 })) {
    errorCount.add(1);
  }

  sleep(1);
}
