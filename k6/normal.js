// **[사용자의 유형 설정]**
//
// - 하루동안 방문하는 사용자(DAU)는 10만명이라고 가정
//     - 90% 사용자는 단순히 콘서트를 둘러보는 서핑 목적이다.
//         - 평균적으로 100번의 API Request를 발생시킨다고 가정
//     - 10% 사용자는 실제 콘서트 좌석 구매 목적
//         - 평균적으로 50번의 API Request를 발생시킨다고 가정
//
// **[평균 TPS 계산]**
//
// - 위 계산을 토대로 하루 동안의 평균 QPS를 계산하면 다음과 같다.
//
//     #### 구경 목적 (90%)
//
//     - 인원: 100,000명 × 90% = 90,000명
//     - 하루 총 요청: 90,000명 × 100회 = 9,000,000건
//     - 평균 TPS: 9,000,000 ÷ 86,400초 = 약 104 TPS
//
//     #### 구매 목적 (10%)
//
//     - 인원: 100,000명 × 10% = 10,000명
//     - 하루 총 요청: 10,000명 × 50회 = 500,000건
//     - 평균 TPS: 500,000 ÷ 86,400초 = 약 6 TPS
//
//
// **[목표 처리량 및 응답 시간 계산]**
//
// - 목표 TPS
//     - 평균적인 상황에서의 목표 처리량 **TPS = 110**으로 잡는다.
//     - 하지만 피크 타임 때도 감안 + 10~20% 여유분을 두어 목표치는 **TPS = 300** 으로 잡는다.
// - 목포 Latency
//     - 목표 응답 속도를 잡아야한다. 개별 API별로 목표치를 잡는게 좋겠지만, 여기서는 간단하게 하기 위해 전체 **평균 300ms**로 잡았다.
//
// [.env]
// K6_WEB_DASHBOARD=true                                                  # 5665 실시간 웹 대시보드 노출
// K6_PROMETHEUS_RW_SERVER_URL=http://<prometheus-host>:9090/api/v1/write # Prometheus 원격 Write
// BASE_URL=https://...                                                   # API 서버 URL (ALB)
// CONCERT_ID=1                                                           # 콘서트 ID
// CONCERT_SCHEDULE_ID=1                                                  # 콘서트 회차 ID
// SEAT_COUNT=3000                                                        # 전체 좌석 수
// TARGET=300                                                             # 전체 VU 수 (VU의 10%는 구매, 90%는 서핑)
// START_DURATION=5m                                                      # ramp up 시간
// MIDDLE_DURATION=3m                                                     # 유지 시간
//
// [커맨드]
// set -a && source .env
// k6 run -o experimental-prometheus-rw normal.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CONCERT_ID = __ENV.CONCERT_ID || '1';
const CONCERT_SCHEDULE_ID = __ENV.CONCERT_SCHEDULE_ID || '1';
const SEAT_COUNT = Number(__ENV.SEAT_COUNT || '3000');

const ALREADY_JOINED_STATUS = 409;   // 이미 참여/Active 중 -> takeover로 전환

const enterDuration = new Trend('enter_duration', true);      // 대기열 진입
const statusDuration = new Trend('status_duration', true);    // 대기열 상태 조회
const seatsDuration = new Trend('seats_duration', true);      // 전체 좌석 조회
const occupyDuration = new Trend('occupy_duration', true);    // 좌석 점유 시도
const orderDuration = new Trend('order_duration', true);      // 주문 생성
const confirmDuration = new Trend('confirm_duration', true);  // 주문 확정

const errorCount = new Counter('error_count');

export const options = {
  stages: [
    { target: Number(__ENV.TARGET || '300'), duration: __ENV.START_DURATION || '2m' },    // 램프업 시간
    { target: Number(__ENV.TARGET || '300'), duration: __ENV.MIDDLE_DURATION || '2m' },   // 유지 시간
  ],
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

// 서핑 사용자가 호출하는 조회 엔드포인트
const SURF_TARGETS = [
  { name: 'GET /concerts', url: (userId) => `${BASE_URL}/api/v1/concerts?userId=${userId}` },
  { name: 'GET /concerts/{id}', url: (userId) => `${BASE_URL}/api/v1/concerts/${CONCERT_ID}?userId=${userId}` },
  { name: 'GET /concerts/{id}/concert-schedules', url: (userId) => `${BASE_URL}/api/v1/concerts/${CONCERT_ID}/concert-schedules?userId=${userId}` },
  { name: 'GET /concert-schedules/{id}/schedule-prices', url: (userId) => `${BASE_URL}/api/v1/concert-schedules/${CONCERT_SCHEDULE_ID}/schedule-prices?userId=${userId}` },
];

// 시나리오: VU의 10%는 구매 퍼널, 90%는 서핑
export default function () {
  if (__VU % 10 === 0) {
    purchase();
  } else {
    surfing();
  }
}

// 서핑: iteration당 조회 GET 1개 (VU ≈ TPS가 되도록 sleep(1)로 페이싱)
function surfing() {
  const userId = __VU;
  const target = SURF_TARGETS[Math.floor(Math.random() * SURF_TARGETS.length)];

  const res = http.get(target.url(userId), { tags: { name: target.name } });

  if (!check(res, { 'surf 200': (r) => r.status === 200 })) {
    errorCount.add(1);
  }

  sleep(1);
}

// 구매
function purchase() {
  const userId = __VU;

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
  } else if (enterRes.status === ALREADY_JOINED_STATUS) {
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
  } else {
    errorCount.add(1);
    sleep(1);
    return;
  }

  if (!queueToken) {
    sleep(1);
    return;
  }

  // 활성화(Active)될 때까지 상태 폴링 (takeover로 이미 Active면 건너뜀)
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
        sleep((statusRes.json('result.retryAfterMs') || 1000) / 1000);
      } else {
        errorCount.add(1);
        sleep(1);
      }
    }
  }

  // 전체 좌석 조회 + 점유 시도
  let seatIds;
  while (true) {
    const seatsRes = http.get(
      `${BASE_URL}/api/v1/concert-schedules/${CONCERT_SCHEDULE_ID}/schedule-seats?userId=${userId}&token=${queueToken}`,
      { tags: { name: 'GET /concert-schedules/{id}/schedule-seats' } }
    );
    seatsDuration.add(seatsRes.timings.duration);

    if (check(seatsRes, { 'seats 200': (r) => r.status === 200 })) {
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

  // 주문 생성
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

  // 결제 확정
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
