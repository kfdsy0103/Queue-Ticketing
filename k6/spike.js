// [테스트 목적]
// 실제 사용자가 순식간에 입장하고 예매하는 시나리오를 가정, 시스템의 한계 및 동작을 관찰하기 위함
//
// [시나리오]
//
//   1. 좌석을 전체 조회한다.
//   2. 조회된 좌석 중 AVAILABLE인 것을 골라 점유를 시도한다.
//      2-1. 점유에 성공하면 주문 생성 -> 결제 확정까지 수행한다.
//      2-2. 점유에 실패하면(좌석 경합) 1번부터 다시 시도한다.
//   3. 결제까지 마치거나 매진을 만나면 대기열에 다시 줄을 서서 부하를 유지한다.
//
// [사용자 행동 패턴 가정]
//
// - Think Time 가정
//     - '좌석 전체 조회 API -> 좌석들 보고 결정하는 Think Time -> 점유 시도' 니까 대략 2초로 가정.
//
// - '좌석 전체 조회 API' 호출 빈도 가정
//     - 사용자는 점유를 3번 정도 시도해보고 새로고침할 것이라고 가정.
//
// [.env]
// K6_WEB_DASHBOARD=true                                                  # 5665 실시간 웹 대시보드 노출
// K6_PROMETHEUS_RW_SERVER_URL=http://<prometheus-host>:9090/api/v1/write # Prometheus 원격 Write
// K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),avg,max"                     # 전송할 Trend 통계
// BASE_URL=https://...                                                   # API 서버 URL (ALB)
// CONCERT_ID=1                                                           # 콘서트 ID
// CONCERT_SCHEDULE_ID=1                                                  # 콘서트 회차 ID
// TARGET=10000                                                           # 전체 VU 수
// RAMP_UP_DURATION=3s                                                    # ramp up 시간
// HOLD_DURATION=5m                                                       # 유지 시간
//
// [커맨드]
// set -a && source .env
// k6 run -o experimental-prometheus-rw spike.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CONCERT_ID = __ENV.CONCERT_ID || '1';
const CONCERT_SCHEDULE_ID = Number(__ENV.CONCERT_SCHEDULE_ID || '1');
const TARGET = Number(__ENV.TARGET || '10000');
const RAMP_UP_DURATION = __ENV.RAMP_UP_DURATION || '3s';
const HOLD_DURATION = __ENV.HOLD_DURATION || '5m';

// 티켓팅 관련 API 측정용 커스텀 Trend
const enterDuration = new Trend('enter_duration', true);                      // 대기열 입장
const statusDuration = new Trend('status_duration', true);                    // 대기열 상태 조회
const concertDuration = new Trend('concert_duration', true);                  // 콘서트 조회
const concertScheduleDuration = new Trend('concert_schedule_duration', true); // 콘서트 회차 조회
const remainingSeatsDuration = new Trend('remaining_seats_duration', true);   // 등급별 잔여 좌석 조회
const seatsDuration = new Trend('seats_duration', true);                      // 전체 좌석 조회
const occupyDuration = new Trend('occupy_duration', true);                    // 좌석 점유 시도
const myOccupyDuration = new Trend('my_occupy_duration', true);               // 내 점유 좌석 조회
const orderDuration = new Trend('order_duration', true);                      // 주문 생성 (PG ready 포함)
const confirmDuration = new Trend('confirm_duration', true);                  // 주문 확정 (PG approve 포함)

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
        { target: TARGET, duration: RAMP_UP_DURATION },   // 스파이크 구간 (3초 만에 1만 VU까지 끌어올린다)
        { target: TARGET, duration: HOLD_DURATION },      // 유지 시간
        { target: 0, duration: '30s' },                   // graceful 종료: 진행 중인 요청을 마치고 내려간다
      ],
      tags: { phase: 'main' },
    },
  },
  // 한계 관측이 목적이므로 thresholds를 두지 않는다
  systemTags: ['proto', 'subproto', 'status', 'method', 'name', 'group', 'check', 'error', 'error_code', 'tls_version', 'scenario', 'service', 'expected_response'],
};

// 조회된 AVAILABLE 좌석 중 1~2개를 랜덤 선택하는 유틸 메서드
function pickAvailableSeatIds(availableSeatIds) {
  const count = Math.min(Math.floor(Math.random() * 2) + 1, availableSeatIds.length);
  const seatIds = new Set();
  while (seatIds.size < count) {
    seatIds.add(availableSeatIds[Math.floor(Math.random() * availableSeatIds.length)]);
  }
  return [...seatIds];
}

const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

const USER_ID = __VU;

export default function () {

  // 1. 대기열 입장
  let queueToken = enterQueue();
  if (queueToken === null) {
    return;
  }

  // 2. 대기열 상태 폴링
  let isActive = polling(queueToken);
  if (!isActive) {
    return;
  }

  // 3. 콘서트 관련 부가적인 정보 조회
  concertInfos();

  // 4. 등급별 잔여 좌석 조회
  searchRemainingSeats();

  // 5. 좌석 조회 및 점유 시도, 좌석 새로고침은 매번 X
  let seatIds = null;
  while (!seatIds) {
    let availableSeatIds = searchAvailableSeats(queueToken);
    if (availableSeatIds.length === 0) {
      return;
    }

    // 사용자는 보고있는 화면 기준에서 점유 3번정도 시도해보고 새로고침 할 것이라고 가정
    for (let attempt = 0; attempt < 3 && !seatIds; attempt++) {
      sleep(2);  // 좌석 보고 결정하는, 사용자의 Think Time은 2초라고 가정
      seatIds = occupySeats(queueToken, availableSeatIds);
    }
  }

  // 6. 나의 점유 중인 좌석 조회
  let myOccupy = searchMyOccupy();

  // 7. 주문 및 결제
  let orderId = createOrder(myOccupy);
  confirmOrder(orderId);

  sleep(1);
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
      return false;
    }
    if (statusRes.json('result.isActive') === true) {
      return true;
    }

    sleep((statusRes.json('result.retryAfterMs') || 1000) / 1000);  // 서버가 내려주는 주기로 폴링
  }
}

// 콘서트 및 회차 정보와 관련된 부수적인 조회 API
function concertInfos() {

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

// 등급별 잔여 좌석 조회
function searchRemainingSeats() {

  const remainingRes = http.get(
    `${BASE_URL}/api/v1/concert-schedules/${CONCERT_SCHEDULE_ID}/schedule-seats/remaining?userId=${USER_ID}`,
    { tags: { name: 'GET /concert-schedules/{id}/schedule-seats/remaining' } }
  );
  remainingSeatsDuration.add(remainingRes.timings.duration);

  if (!check(remainingRes, { 'remaining 200': (r) => r.status === 200 })) {
    errorCount.add(1);
  }
}

// 전체 좌석 조회 API + 그 중에서 예약 가능한 좌석 목록 반환
function searchAvailableSeats(queueToken) {

  const seatsRes = http.get(
    `${BASE_URL}/api/v1/concert-schedules/${CONCERT_SCHEDULE_ID}/schedule-seats?userId=${USER_ID}&token=${queueToken}`,
    { tags: { name: 'GET /concert-schedules/{id}/schedule-seats' } }
  );
  seatsDuration.add(seatsRes.timings.duration);

  if (!check(seatsRes, { 'seats 200': (r) => r.status === 200 })) {
    errorCount.add(1);
    return [];
  }

  return (seatsRes.json('result.scheduleSeats') || [])
    .filter((seat) => seat.seatStatus === 'AVAILABLE')
    .map((seat) => seat.scheduleSeatId);
}

// 좌석 점유 시도
function occupySeats(queueToken, availableSeatIds) {

  const occupyRes = http.post(
    `${BASE_URL}/api/v1/concert-schedules/${CONCERT_SCHEDULE_ID}/schedule-seats/occupy?userId=${USER_ID}`,
    JSON.stringify({ scheduleSeatIds: pickAvailableSeatIds(availableSeatIds), token: queueToken }),
    { ...JSON_HEADERS, tags: { name: 'POST /concert-schedules/{id}/schedule-seats/occupy' } }
  );
  occupyDuration.add(occupyRes.timings.duration);

  // 좌석 경합은 정상
  if (!check(occupyRes, { 'occupy 200 or conflict': (r) => r.status === 200 || r.status === 409 })) {
    errorCount.add(1);
  }

  return occupyRes.status === 200 ? occupyRes.json('result.scheduleSeatIds') : null;
}

// 내가 점유 중인 좌석 조회
function searchMyOccupy() {

  const myOccupyRes = http.get(
    `${BASE_URL}/api/v1/users/occupy?userId=${USER_ID}`,
    { tags: { name: 'GET /users/occupy' } }
  );
  myOccupyDuration.add(myOccupyRes.timings.duration);

  if (!check(myOccupyRes, { 'my occupy 200': (r) => r.status === 200 })) {
    errorCount.add(1);
    return [];
  }

  return (myOccupyRes.json('result.seats') || [])
    .filter((seat) => seat.concertScheduleId === CONCERT_SCHEDULE_ID)
    .map((seat) => seat.scheduleSeatId);
}

// 주문 생성
function createOrder(seatIds) {

  const orderRes = http.post(
    `${BASE_URL}/api/v1/orders/create?userId=${USER_ID}`,
    JSON.stringify({ scheduleSeatIds: seatIds, paymentMethod: 'KAKAO_PAY' }),
    { ...JSON_HEADERS, tags: { name: 'POST /orders/create' } }
  );
  orderDuration.add(orderRes.timings.duration);

  if (!check(orderRes, { 'order 201': (r) => r.status === 201 })) {
    errorCount.add(1);
    return null;
  }

  return orderRes.json('result.orderId');
}

// 결제 및 주문 확정
function confirmOrder(orderId) {

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
