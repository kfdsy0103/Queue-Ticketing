// [.env]
// K6_WEB_DASHBOARD=true                                                  # 실시간 웹 대시보드 노출
// K6_PROMETHEUS_RW_SERVER_URL=http://<prometheus-host>:9090/api/v1/write # Prometheus 원격 Write
// BASE_URL=https://...                                                   # API 서버 URL
// CONCERT_SCHEDULE_ID=1                                                  # 콘서트 회차 ID
// SEAT_COUNT=100                                                         # 전체 좌석 수
// TARGET=100                                                             # Vuser 수
// DURATION=5m                                                            # 테스트 지속 시간
//
// [커맨드]
// set -a && source .env
// k6 run -o experimental-prometheus-rw script.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';         // API 서버
const CONCERT_SCHEDULE_ID = __ENV.CONCERT_SCHEDULE_ID || '1';       // 콘서트 회차 ID
const SEAT_COUNT = Number(__ENV.SEAT_COUNT || '100'); // 전체 좌석 수

const enterDuration = new Trend('enter_duration', true);      // 대기열 진입
const statusDuration = new Trend('status_duration', true);    // 대기열 내 상태 조회
const seatsDuration = new Trend('seats_duration', true);      // 전체 좌석 조회
const occupyDuration = new Trend('occupy_duration', true);    // 좌석 점유 시도
const orderDuration = new Trend('order_duration', true);      // 주문 생성
const confirmDuration = new Trend('confirm_duration', true);  // 주문 확정

const errorCount = new Counter('error_count');  // 요청 실패 Counter

export const options = {
  stages: [
    { target: Number(__ENV.TARGET || '100'), duration: __ENV.DURATION || '5m' },
  ],
  // url 태그는 userId/token 등 매 요청 유니크 값이 섞여 카디널리티가 폭발하므로 제외 (name 태그로 라우트 패턴만 집계)
  systemTags: ['proto', 'subproto', 'status', 'method', 'name', 'group', 'check', 'error', 'error_code', 'tls_version', 'scenario', 'service', 'expected_response'],
};

const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

// 1~4개 좌석을 랜덤 선택하는 유틸 메서드
function pickRandomSeatIds() {
  const count = Math.floor(Math.random() * 4) + 1;
  const seatIds = new Set();
  while (seatIds.size < count) {
    seatIds.add(Math.floor(Math.random() * SEAT_COUNT) + 1);
  }
  return [...seatIds];
}

// 시나리오
export default function () {
  const userId = __VU;

  // 1. 대기열 진입 (이미 참여/Active 중이면 물려받기로 전환하여 기존 순번/Active를 이어받음)
  let queueToken;
  let alreadyActive = false;

  const enterRes = http.post(
    `${BASE_URL}/api/v1/queue/enter?userId=${userId}`,
    JSON.stringify({ concertScheduleId: Number(CONCERT_SCHEDULE_ID), enterType: 'JOIN' }),
    { ...JSON_HEADERS, tags: { name: 'POST /queue/enter' } }
  );
  enterDuration.add(enterRes.timings.duration);

  if (check(enterRes, { 'enter 201': (r) => r.status === 201 })) {
    queueToken = enterRes.json('result.token');
  }
  else if (enterRes.status === 409) {
    // 이미 참여/Active 중인 정상 시나리오 -> 물려받기로 전환 (errorCount 미집계)
    const takeoverRes = http.post(
      `${BASE_URL}/api/v1/queue/takeover?userId=${userId}`,
      JSON.stringify({ concertScheduleId: Number(CONCERT_SCHEDULE_ID) }),
      { ...JSON_HEADERS, tags: { name: 'POST /queue/takeover' } }
    );
    enterDuration.add(takeoverRes.timings.duration);

    if (check(takeoverRes, { 'takeover 201': (r) => r.status === 201 })) {
      queueToken = takeoverRes.json('result.token');
      alreadyActive = takeoverRes.json('result.isActive') === true;
    } else {
      errorCount.add(1);
      sleep(1);
      return;
    }
  }
  else {
    errorCount.add(1);
    sleep(1);
    return;
  }

  if (!queueToken) {
    sleep(1);
    return;
  }

  // 2. 활성화(Active)될 때까지 상태 폴링 (물려받기로 이미 Active를 이어받았다면 건너뜀)
  if (!alreadyActive) {
    while (true) {
      const statusRes = http.get(
        `${BASE_URL}/api/v1/queue/status?userId=${userId}&token=${queueToken}`,
        { tags: { name: 'GET /queue/status' } }
      );
      statusDuration.add(statusRes.timings.duration);

      if (check(statusRes, { 'status 200': (r) => r.status === 200 })) {
        if (statusRes.json('result.isActive') === true) {
          break;
        }
        sleep((statusRes.json('result.retryAfterMs') || 1000) / 1000);   // 서버에서 내려주는 주기로 폴링
      } else {
        errorCount.add(1);
        sleep(1);
      }
    }
  }

  // 3. 전체 좌석 조회 및 좌석 점유 시도 (점유에 실패하면 사용자는 좌석 조회를 새롭게 하게 될 것)
  let seatIds;
  while (true) {
    // 3-1. 전체 좌석 조회
    const seatsRes = http.get(
      `${BASE_URL}/api/v1/concert-schedules/${CONCERT_SCHEDULE_ID}/schedule-seats?userId=${userId}&token=${queueToken}`,
      { tags: { name: 'GET /concert-schedules/{id}/schedule-seats' } }
    );
    seatsDuration.add(seatsRes.timings.duration);

    if (check(seatsRes, { 'seats 200': (r) => r.status === 200 })) {
      // 3-2. 좌석 점유 시도
      seatIds = pickRandomSeatIds();
      const occupyRes = http.post(
        `${BASE_URL}/api/v1/concert-schedules/${CONCERT_SCHEDULE_ID}/schedule-seats/occupy?userId=${userId}`,
        JSON.stringify({ scheduleSeatIds: seatIds, token: queueToken }),
        { ...JSON_HEADERS, tags: { name: 'POST /concert-schedules/{id}/schedule-seats/occupy' } }
      );
      occupyDuration.add(occupyRes.timings.duration);

      if (check(occupyRes, { 'occupy 200': (r) => r.status === 200 })) {
        break;
      } else if (occupyRes.status !== 409) {  // 좌석 경합(409)은 errorCount 집계에서 제외
        errorCount.add(1);
      }
    } else {
      errorCount.add(1);
    }

    sleep(1);
  }

  // 4. 주문 생성
  const orderRes = http.post(
    `${BASE_URL}/api/v1/orders/create?userId=${userId}`,
    JSON.stringify({ scheduleSeatIds: seatIds, paymentMethod: 'KAKAO_PAY' }),
    { ...JSON_HEADERS, tags: { name: 'POST /orders/create' } }
  );
  orderDuration.add(orderRes.timings.duration);

  let orderId;
  if (check(orderRes, { 'order 201': (r) => r.status === 201 })) {
    orderId = orderRes.json('result.orderId');
  } else {
    errorCount.add(1);
    return;
  }

  if (!orderId) {
    return;
  }

  // 5. 결제 확정
  const confirmRes = http.post(
    `${BASE_URL}/api/v1/orders/${orderId}/confirm?userId=${userId}`,
    JSON.stringify({ pgToken: 'mock_pg_token' }),
    { ...JSON_HEADERS, tags: { name: 'POST /orders/{id}/confirm' } }
  );
  confirmDuration.add(confirmRes.timings.duration);

  if (!check(confirmRes, { 'confirm 200': (r) => r.status === 200 })) {
    errorCount.add(1);
  }

  sleep(1);
}
