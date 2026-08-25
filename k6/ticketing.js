// [테스트 목적]
// 대기열 통과 이후 티켓팅 과정에서 호출되는 API들에 대하여, 목표 SLO를 만족할 때의 TPS를 측정하기 위함
// 여기서 얻은 TPS 기반으로 스케쥴러의 1초당 입장량을 설정하는 데 활용을 할 예정.
//
//
// [SLO 목표]
//
// - 티켓팅 과정에서 호출되는 전체 API P95 200ms
// - 실제로는 더 개별적으로 설정하여 나눠야할 것. 편의를 위해 전체 200ms으로 일단 잡는다, 여기서 결제 관련 API는 Thread sleep으로 모방만 했으니 제외
//
//
// [사용자 행동 패턴 가정]
//
// - Think Time 가정
//     - '좌석 전체 조회 API -> 좌석들 보고 결정하는 Think Time -> 점유 시도' 니까 대략 2초로 가정.
//
// - '좌석 전체 조회 API' 호출 빈도 가정
//     - 사용자는 점유를 3번 정도 시도해보고 새로고침할 것이라고 가정.
//
//
// [.env]
// 필요한 환경변수와 예시 값은 env-example.md 참고
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

// 티켓팅 관련 API 측정용 커스텀 Trend
const enterDuration = new Trend('enter_duration', true);                      // 대기열 입장
const statusDuration = new Trend('status_duration', true);                    // 대기열 상태 조회
const concertDuration = new Trend('concert_duration', true);                  // 콘서트 조회
const concertScheduleDuration = new Trend('concert_schedule_duration', true); // 콘서트 회차 조회
const remainingSeatsDuration = new Trend('remaining_seats_duration', true);   // 등급별 잔여 좌석 조회
const seatsDuration = new Trend('seats_duration', true);                      // 전체 좌석 조회
const occupyDuration = new Trend('occupy_duration', true);                    // 좌석 점유 시도
const myOccupyDuration = new Trend('my_occupy_duration', true);               // 내 점유 좌석 조회
const orderDuration = new Trend('order_duration', true);                      // 주문 생성 (SLO에서 일단 제외)
const confirmDuration = new Trend('confirm_duration', true);                  // 주문 확정 (얘도 제외)

const errorCount = new Counter('error_count');

export const options = {
  scenarios: {
    main: {
      executor: 'ramping-vus',
      stages: [
        { target: __ENV.TARGET, duration: __ENV.RAMP_UP_DURATION },   // 램프업 시간
        { target: __ENV.TARGET, duration: __ENV.HOLD_DURATION },      // 유지 시간
        { target: 0, duration: '30s' },                               // graceful 종료
      ],
      tags: { phase: 'main' },
    },
  },
  // SLO 목표: 티켓팅 과정에서 발생하는 전체 API P95 200ms
  // {phase:main} 으로 한정해 워밍업 구간의 표본을 판정에서 제외한다.
  thresholds: {
    'enter_duration{phase:main}': ['p(95)<200'],             // POST /queue/enter
    'status_duration{phase:main}': ['p(95)<200'],            // GET /queue/status
    'concert_duration{phase:main}': ['p(95)<200'],           // GET /concerts/{id}
    'concert_schedule_duration{phase:main}': ['p(95)<200'],  // GET /concerts/{id}/concert-schedules/{id}
    'remaining_seats_duration{phase:main}': ['p(95)<200'],   // GET /concert-schedules/{id}/schedule-seats/remaining
    'seats_duration{phase:main}': ['p(95)<200'],             // GET /concert-schedules/{id}/schedule-seats
    'occupy_duration{phase:main}': ['p(95)<200'],            // POST /concert-schedules/{id}/schedule-seats/occupy
    'my_occupy_duration{phase:main}': ['p(95)<200'],         // GET /users/occupy
  },
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
