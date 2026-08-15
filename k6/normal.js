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
//
// [티켓팅 시 사용자의 행동 패턴 가정]
//
// - Think Time 가정
//     - 보통 티켓팅을 하면 흐름이, '좌석 전체 조회 API -> 좌석들 보고 결정하는 Think Time -> 점유 시도' 니까 대략 2초로 가정.
//
// - '좌석 전체 조회 API' 호출 빈도 가정
//     - 사용자는 점유를 3번 정도 시도해보고 새로고침할 것이라고 가정.
//
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
//
// [.env]
// K6_WEB_DASHBOARD=true                                                  # 5665 실시간 웹 대시보드 노출
// K6_PROMETHEUS_RW_SERVER_URL=http://<prometheus-host>:9090/api/v1/write # Prometheus 원격 Write
// K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),avg,max"                     # 전송할 Trend 통계
// BASE_URL=https://...                                                   # API 서버 URL (ALB)
// CONCERT_ID=1                                                           # 콘서트 ID
// CONCERT_SCHEDULE_ID=1                                                  # 콘서트 회차 ID
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

// 조회된 AVAILABLE 좌석 중 1~2개를 랜덤 선택하는 유틸 메서드
function pickAvailableSeatIds(availableSeatIds) {
  const count = Math.min(Math.floor(Math.random() * 2) + 1, availableSeatIds.length);
  const seatIds = new Set();
  while (seatIds.size < count) {
    seatIds.add(availableSeatIds[Math.floor(Math.random() * availableSeatIds.length)]);
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
];

const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

const USER_ID = __VU;

// 10%는 티켓팅 목적, 90%는 서핑 목적
export default function () {
  if (Math.random() < 0.1) {
    purchase();
  } else {
    surfing();
  }
}

// 서핑
function surfing() {

  const target = SURF_TARGETS[Math.floor(Math.random() * SURF_TARGETS.length)];
  const res = http.get(`${target.url}?userId=${USER_ID}`, { tags: { name: target.name } });
  target.trend.add(res.timings.duration);

  if (!check(res, { 'surf 200': (r) => r.status === 200 })) {
    errorCount.add(1);
  }

  sleep(1);
}

// 티켓팅
function purchase() {

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

  // 4. 좌석 조회 및 점유 시도, 좌석 새로고침은 매번 X
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

  // 5. 나의 점유 중인 좌석 조회
  let myOccupy = searchMyOccupy();

  // 6. 주문 및 결제
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

// 예약 가능 좌석 전체 조회
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
