// [테스트 목적]
// 평균적인 서비스 운영 상황을 가정하여, 목표 TPS 및 응답시간을 만족하는지 판단하기 위함
//
// [사용자의 유형 설정]
//
// - 하루동안 방문하는 사용자(DAU)는 10만명이라고 가정
//
//     - 90% 사용자는 단순히 콘서트를 둘러보는 서핑 목적이다.
//         - 평균적으로 100번의 API Request를 발생시킨다고 가정
//
//     - 10% 사용자는 실제 콘서트 좌석 티켓팅 목적
//         - 평균적으로 50번의 API Request를 발생시킨다고 가정
//
// [평균 TPS 계산]
//
// - 위 계산을 토대로 하루 동안의 평균 QPS를 계산하면 다음과 같다.
//
//     - 서핑 목적 (90%)
//        - 인원: 100,000명 × 90% = 90,000명
//        - 하루 총 요청: 90,000명 × 100회 = 9,000,000건
//        - 평균 TPS: 9,000,000 ÷ 86,400초 = 약 104 TPS
//
//     - 티켓팅 목적 (10%)
//        - 인원: 100,000명 × 10% = 10,000명
//        - 하루 총 요청: 10,000명 × 50회 = 500,000건
//        - 평균 TPS: 500,000 ÷ 86,400초 = 약 6 TPS
//
//
// [목표 처리량 계산 및 목표 응답 시간 설정]
//
// - 목표 TPS
//     - 평균적인 상황에서의 목표 처리량 **TPS = 110**으로 잡는다.
//     - 하지만 피크 타임 때도 감안 + 10~20% 여유분을 두어 목표치는 **TPS = 300** 으로 잡는다.
//
// - 목표 Latency
//     - 목표 응답 속도를 잡아야한다. 개별 API별로 목표치를 잡는게 좋겠지만, 여기서는 간단하게 하기 위해 전체 API **P95 300ms**로 잡았다.
//
// [.env]
// K6_WEB_DASHBOARD=true                                                  # 5665 실시간 웹 대시보드 노출
// K6_PROMETHEUS_RW_SERVER_URL=http://<prometheus-host>:9090/api/v1/write # Prometheus 원격 Write
// K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),avg,max"                     # 전송할 Trend 통계
// BASE_URL=https://...                                                   # API 서버 URL (ALB)
// CONCERT_ID=1                                                           # 콘서트 ID
// CONCERT_SCHEDULE_ID=1                                                  # 콘서트 회차 ID
// SEAT_COUNT=3000                                                        # 전체 좌석 수
// TARGET=300                                                             # 전체 VU 수 (VU의 10%는 구매, 90%는 서핑)
// RAMP_UP_DURATION=1m                                                    # ramp up 시간
// HOLD_DURATION=5m                                                       # 유지 시간
//
// [커맨드]
// set -a && source .env
// k6 run -o experimental-prometheus-rw normal.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CONCERT_ID = __ENV.CONCERT_ID || '1';
const CONCERT_SCHEDULE_ID = Number(__ENV.CONCERT_SCHEDULE_ID || '1');
const SEAT_COUNT = Number(__ENV.SEAT_COUNT || '3000');

// 티켓팅 사용자가 호출하는 엔드포인트 Trend
const enterDuration = new Trend('enter_duration', true);      // 대기열 진입
const statusDuration = new Trend('status_duration', true);    // 대기열 상태 조회
const seatsDuration = new Trend('seats_duration', true);      // 전체 좌석 조회
const occupyDuration = new Trend('occupy_duration', true);    // 좌석 점유 시도
const orderDuration = new Trend('order_duration', true);      // 주문 생성
const confirmDuration = new Trend('confirm_duration', true);  // 주문 확정

// 서핑 사용자가 호출하는 엔드포인트 Trend
const concertsDuration = new Trend('concerts_duration', true);                  // GET /concerts
const concertDuration = new Trend('concert_duration', true);                    // GET /concerts/{id}
const concertSchedulesDuration = new Trend('concert_schedules_duration', true); // GET /concerts/{id}/concert-schedules
const concertScheduleDuration = new Trend('concert_schedule_duration', true);   // GET /concerts/{id}/concert-schedules/{id}
const remainingSeatsDuration = new Trend('remaining_seats_duration', true);     // GET /concert-schedules/{id}/schedule-seats/remaining
const myOccupyDuration = new Trend('my_occupy_duration', true);                 // GET /users/occupy

const errorCount = new Counter('error_count');

export const options = {
  stages: [
    { target: __ENV.TARGET, duration: __ENV.RAMP_UP_DURATION },   // 램프업 시간
    { target: __ENV.TARGET, duration: __ENV.HOLD_DURATION },      // 유지 시간
  ],
  systemTags: ['proto', 'subproto', 'status', 'method', 'name', 'group', 'check', 'error', 'error_code', 'tls_version', 'scenario', 'service', 'expected_response'],
};

// 1~4개 좌석을 랜덤 선택하는 유틸 메서드
function pickRandomSeatIds() {
  const count = Math.floor(Math.random() * 4) + 1;
  const seatIds = new Set();
  while (seatIds.size < count) {
    seatIds.add(Math.floor(Math.random() * SEAT_COUNT) + 1);
  }
  return [...seatIds];
}

// 서핑 사용자가 호출하는 조회 엔드포인트
const SURF_TARGETS = [
  { name: 'GET /concerts', trend: concertsDuration, url: `${BASE_URL}/api/v1/concerts` },
  { name: 'GET /concerts/{id}', trend: concertDuration, url: `${BASE_URL}/api/v1/concerts/${CONCERT_ID}` },
  { name: 'GET /concerts/{id}/concert-schedules', trend: concertSchedulesDuration, url: `${BASE_URL}/api/v1/concerts/${CONCERT_ID}/concert-schedules` },
  { name: 'GET /concerts/{id}/concert-schedules/{id}', trend: concertScheduleDuration, url: `${BASE_URL}/api/v1/concerts/${CONCERT_ID}/concert-schedules/${CONCERT_SCHEDULE_ID}` },
  { name: 'GET /concert-schedules/{id}/schedule-seats/remaining', trend: remainingSeatsDuration, url: `${BASE_URL}/api/v1/concert-schedules/${CONCERT_SCHEDULE_ID}/schedule-seats/remaining` },
  { name: 'GET /users/occupy', trend: myOccupyDuration, url: `${BASE_URL}/api/v1/users/occupy` },
];

const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

const USER_ID = __VU;
let queueToken = null;
let isActive = false;
let seatIds = null;
let orderId = null;

// VU의 10%는 구매 목적, 90%는 티켓팅 목적
export default function () {
  if (USER_ID % 10 === 0) {
    purchase();
  } else {
    surfing();
  }
}

// 서핑 목적
function surfing() {

  const target = SURF_TARGETS[Math.floor(Math.random() * SURF_TARGETS.length)];

  const res = http.get(`${target.url}?userId=${USER_ID}`, { tags: { name: target.name } });
  target.trend.add(res.timings.duration);

  if (!check(res, { 'surf 200': (r) => r.status === 200 })) {
    errorCount.add(1);
  }

  sleep(1);
}

// 티켓팅 목적
function purchase() {

  seatIds = null;
  orderId = null;

  if (!isActive) {
    enterQueue();
    polling();
  }

  while (!seatIds) {
    additionalAPIs();
    searchSeats();
    occupySeats();
  }

  createOrder();
  confirmOrder();

  sleep(1);
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

  const candidates = pickRandomSeatIds();
  const occupyRes = http.post(
    `${BASE_URL}/api/v1/concert-schedules/${CONCERT_SCHEDULE_ID}/schedule-seats/occupy?userId=${USER_ID}`,
    JSON.stringify({ scheduleSeatIds: candidates, token: queueToken }),
    { ...JSON_HEADERS, tags: { name: 'POST /concert-schedules/{id}/schedule-seats/occupy' } }
  );
  occupyDuration.add(occupyRes.timings.duration);

  // 좌석 경합은 정상
  if (!check(occupyRes, { 'occupy 200 or conflict': (r) => r.status === 200 || r.status === 409 })) {
    errorCount.add(1);
    return;
  }

  // 점유에 성공한 좌석을 주문에 넘기기 위함
  if (occupyRes.status === 200) {
    seatIds = candidates;
  }
}

// 주문 생성
function createOrder() {

  const orderRes = http.post(
    `${BASE_URL}/api/v1/orders/create?userId=${USER_ID}`,
    JSON.stringify({ scheduleSeatIds: seatIds, paymentMethod: 'KAKAO_PAY' }),
    { ...JSON_HEADERS, tags: { name: 'POST /orders/create' } }
  );
  orderDuration.add(orderRes.timings.duration);

  if (!check(orderRes, { 'order 201': (r) => r.status === 201 })) {
    errorCount.add(1);
    return;
  }

  orderId = orderRes.json('result.orderId');
}

// 결제 확정
function confirmOrder() {

  const confirmRes = http.post(
    `${BASE_URL}/api/v1/orders/${orderId}/confirm?userId=${USER_ID}`,
    JSON.stringify({ pgToken: 'mock_pg_token' }),
    { ...JSON_HEADERS, tags: { name: 'POST /orders/{id}/confirm' } }
  );
  confirmDuration.add(confirmRes.timings.duration);

  if (!check(confirmRes, { 'confirm 200': (r) => r.status === 200 })) {
    errorCount.add(1);
  }
}
