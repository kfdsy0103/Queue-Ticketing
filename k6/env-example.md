# k6 환경변수

루트 `k6/` 스크립트는 설정을 `__ENV` 로 받는다. 이 디렉터리에 `.env` 를 만들어 값을 채우고 실행한다.

```bash
set -a && source .env
k6 run -o experimental-prometheus-rw <스크립트>.js
```

`.env` 는 gitignore 대상이므로 저장소에 올라가지 않는다.

## 공통

네 스크립트가 모두 쓰는 값이다.

| 변수 | 예시 | 설명 |
|---|---|---|
| `K6_WEB_DASHBOARD` | `true` | 5665 포트에 실시간 웹 대시보드 노출 |
| `K6_PROMETHEUS_RW_SERVER_URL` | `http://<prometheus-host>:9090/api/v1/write` | Prometheus 원격 Write 주소 |
| `K6_PROMETHEUS_RW_TREND_STATS` | `p(95),p(99),avg,max` | 전송할 Trend 통계 |
| `BASE_URL` | `https://...` | 대상 서버 URL |
| `CONCERT_SCHEDULE_ID` | `1` | 콘서트 회차 ID |
| `TARGET` | 스크립트별 상이 | 전체 VU 수 |
| `RAMP_UP_DURATION` | 스크립트별 상이 | ramp up 시간 |
| `HOLD_DURATION` | `5m` | 유지 시간 |

Prometheus 로 보내지 않고 웹 대시보드만 볼 거라면 `K6_PROMETHEUS_RW_*` 는 비워도 되고, 실행 시 `-o experimental-prometheus-rw` 를 빼면 된다.

## 스크립트별 값

| | `queue.js` | `normal.js` | `spike.js` | `ticketing.js` |
|---|---|---|---|---|
| 목적 | 대기열 서버 TPS | 평상시 부하 | 순간 폭주 | 티켓팅 API TPS |
| `BASE_URL` | 대기열 서버 | API 서버 (ALB) | API 서버 (ALB) | API 서버 (ALB) |
| `CONCERT_ID` | 사용 안 함 | `1` | `1` | `1` |
| `CONCERT_SCHEDULE_ID` | `1` | `1` | `1` | `1` |
| `TARGET` | `10000` | `300` | `10000` | `200` |
| `RAMP_UP_DURATION` | `3s` | `1m` | `3s` | `1m` |
| `HOLD_DURATION` | `5m` | `5m` | `5m` | `5m` |

`normal.js` 의 `TARGET` 은 VU 의 10% 가 구매, 90% 가 서핑 시나리오로 나뉜다.

## .env 예시

### queue.js

```bash
K6_WEB_DASHBOARD=true
K6_PROMETHEUS_RW_SERVER_URL=http://<prometheus-host>:9090/api/v1/write
K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),avg,max"
BASE_URL=https://...
CONCERT_SCHEDULE_ID=1
TARGET=10000
RAMP_UP_DURATION=3s
HOLD_DURATION=5m
```

### normal.js / spike.js / ticketing.js

`CONCERT_ID` 가 추가로 필요하고, `TARGET` 과 `RAMP_UP_DURATION` 만 위 표에 맞춰 바꾼다.

```bash
K6_WEB_DASHBOARD=true
K6_PROMETHEUS_RW_SERVER_URL=http://<prometheus-host>:9090/api/v1/write
K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),avg,max"
BASE_URL=https://...
CONCERT_ID=1
CONCERT_SCHEDULE_ID=1
TARGET=300
RAMP_UP_DURATION=1m
HOLD_DURATION=5m
```

## 커널 설정

VU 가 수천 이상이면 부하 생성기 쪽 한계에 먼저 걸린다. 실행 전에 `ulimit.text` 의 설정을 적용한다.
