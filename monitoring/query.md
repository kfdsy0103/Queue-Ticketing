# 모니터링 쿼리

Grafana 대시보드에서 쓰는 PromQL 및 LogQL 쿼리 모음.
스크래핑 설정은 `prometheus/prometheus.yml`, 대시보드 ID 목록은 `dashboard.txt`를 참고.

> **Gauge**는 오르내리는 현재 값(메모리 사용량, CPU 사용률)을 의미, **Counter**는 단조 증가만 하는 누적 값(GC 횟수, 요청 수)을 의미하여 `rate()`로 증가 속도를 구해서 사용.

`네이밍` 컬럼은 어디까지나 가이드이니 커스텀하게 수정해도 된다.
`라벨` 도 마찬가지로 필요한 경우 추가, 단 카디널리티에 유의
`단위` 는 패널의 **Standard options → Unit** 에 넣는 Grafana 단위 ID.
`추천 패널` 은 Visualization 종류이며, 추이를 봐야 하면 Time series, 현재 값 하나만 크게 띄우려면 Stat, 항목끼리 비교하려면 Bar gauge 나 Table 을 사용.

---

## 1. EC2 · JVM (Node Exporter + Spring Actuator)

| 의미 | 타입 | 단위 | 추천 패널 | 라벨 | 네이밍 | 쿼리 |
|---|---|---|---|---|---|---|
| 노드 CPU 사용률 (%) | Counter | `percent` | Time series | `instance`, `mode` | `node_cpu_usage_percent` | `100 - (avg by(instance) (rate(node_cpu_seconds_total{mode="idle", instance="$node"}[$__rate_interval])) * 100)` |
| 노드 메모리 사용률 (%) | Gauge | `percent` | Time series | `instance` | `node_memory_usage_percent` | `(1 - (node_memory_MemAvailable_bytes{instance="$node"} / node_memory_MemTotal_bytes{instance="$node"})) * 100` |
| GC 1회 평균 정지 시간 | Counter | `s` | Time series | `instance`, `application`, `namespace`, `action`, `cause` | `jvm_gc_pause_seconds_avg` | `sum by(action, cause) (rate(jvm_gc_pause_seconds_sum{instance="$instance", application="$application", namespace="$Namespace"}[$__rate_interval])) / sum by(action, cause) (rate(jvm_gc_pause_seconds_count{instance="$instance", application="$application", namespace="$Namespace"}[$__rate_interval]))` |
| GC 초당 발생 횟수 | Counter | `cps` | Time series | `instance`, `application`, `action`, `cause` | `jvm_gc_pause_count_rate` | `sum by(action, cause) (rate(jvm_gc_pause_seconds_count{instance="$instance", application="$application"}[$__rate_interval]))` |
| JIT 누적 컴파일 시간 | Counter | `ms` | Time series | `instance`, `application`, `compiler` | `jvm_jit_compilation_time_total` | `jvm_compilation_time_ms_total{instance="$instance", application="$application"}` |
| 초당 JIT 컴파일 시간 | Counter | `short` | Time series | `instance`, `application` | `jvm_jit_compilation_rate` | `rate(jvm_compilation_time_ms_total{instance="$instance", application="$application"}[$__rate_interval])` |
| 로드된 클래스 수 | Gauge | `short` | Time series | `instance`, `application` | `jvm_classes_loaded` | `jvm_classes_loaded_classes{instance="$instance", application="$application"}` |

### 1-1. 워밍업이 끝났는지 판단하기

`jvm_compilation_time_ms_total` 은 JIT 컴파일러가 코드를 기계어로 번역하는 데 쓴 누적 시간이다. `JvmCompilationMetrics` 가 기본 등록되므로 별도 설정 없이 수집된다.

**누적값 그래프가 평평해지고 `초당 JIT 컴파일 시간` 이 0 근처로 떨어지면 워밍업이 끝난 것이다.** `로드된 클래스 수` 도 같이 보면 확실하다. 이 둘이 계속 오르는 구간에서 잰 응답시간은 정상 성능이 아니므로 지표에서 분리해야 한다.

`초당 JIT 컴파일 시간` 은 ms/s 라 단위 없는 비율에 가깝다. 값이 1이면 컴파일러 스레드가 초당 1초어치 CPU를 쓰고 있다는 뜻이라, 이 구간에 CPU 사용률이 함께 치솟는 것이 정상이다.

메트릭 이름이 다르게 보이면 실제 노출값을 확인한다.

```bash
curl -s localhost:8080/actuator/prometheus | grep compilation
```


---

## 2. RDS (MySQL Exporter + CloudWatch Exporter)

CloudWatch로 스크랩하려는 리소스에는 **태그를 하나 이상 부여**해야 yace가 식별합니다.

| 의미 | 타입 | 단위 | 추천 패널 | 라벨 | 네이밍 | 쿼리 |
|---|---|---|---|---|---|---|
| 커밋 TPS | Counter | `ops` | Time series | `command`, `role`, `replica` | `mysql_commit_tps` | `rate(mysql_global_status_commands_total{command="commit"}[1m])` |
| RDS CPU 사용률 (%) | Gauge | `percent` | Time series | `dimension_DBInstanceIdentifier` | `rds_cpu_utilization_percent` | `aws_rds_cpuutilization_average{dimension_DBInstanceIdentifier!=""}` |
| RDS 여유 메모리 | Gauge | `bytes` | Time series | `dimension_DBInstanceIdentifier` | `rds_freeable_memory_bytes` | `aws_rds_freeable_memory_average{dimension_DBInstanceIdentifier!=""}` |

---

## 3. Redis (Redis Exporter)

| 의미 | 타입 | 단위 | 추천 패널 | 라벨 | 네이밍 | 쿼리 |
|---|---|---|---|---|---|---|
| Redis CPU 사용률 (%) | Counter | `percent` | Time series | `instance` | `redis_cpu_usage_percent` | `sum(rate(redis_cpu_user_seconds_total{instance=~"$instance"}[1m]) + rate(redis_cpu_sys_seconds_total{instance=~"$instance"}[1m])) by (instance) * 100` |
| Redis 메모리 사용률 (%) | Gauge | `percent` | Time series + Stat | `instance` | `redis_memory_usage_percent` | `100 * (redis_memory_used_bytes{instance=~"$instance"} / redis_memory_max_bytes{instance=~"$instance"})` |

---

## 4. 부하 테스트 (K6)

| 의미 | 타입 | 단위 | 추천 패널 | 라벨 | 네이밍 | 쿼리 |
|---|---|---|---|---|---|---|
| 엔드포인트별 응답시간 p99 | Gauge | `ms` | Bar gauge 또는 Table | `__name__` | `k6_endpoint_duration_p99` | `{__name__=~"k6_(enter\|status\|seats\|occupy\|order\|confirm\|concerts\|concert\|concert_schedules\|concert_schedule\|remaining_seats\|my_occupy)_duration_p99"}` |
| 전체 응답시간 p99 | Gauge | `ms` | Stat | 없음 | `k6_http_duration_p99_avg` | `avg(k6_http_req_duration_p99)` |
| 엔드포인트별 응답시간 p95 | Gauge | `ms` | Bar gauge 또는 Table | `__name__` | `k6_endpoint_duration_p95` | `{__name__=~"k6_(enter\|status\|seats\|occupy\|order\|confirm\|concerts\|concert\|concert_schedules\|concert_schedule\|remaining_seats\|my_occupy)_duration_p95"}` |
| 전체 응답시간 p95 | Gauge | `ms` | Stat | 없음 | `k6_http_duration_p95_avg` | `avg(k6_http_req_duration_p95)` |

k6 의 duration 은 **밀리초로 나오므로 `ms`** 를 써야 한다. `s` 로 두면 500ms 가 8분으로 표시된다.
엔드포인트별은 어느 API 가 느린지 순위를 보는 게 목적이라 Time series 보다 Bar gauge 나 Table 이 읽기 쉽다. Table 로 쓸 땐 Transform 에 **Labels to fields** 를 걸어 `__name__` 을 열로 뽑는다.

---

## 5. 로그 (Loki + Alloy)

| 의미 | 타입 | 단위 | 추천 패널 | 라벨 | 네이밍 | 쿼리 |
|---|---|---|---|---|---|---|
| 두 서버 전체 INFO | — | — | Logs | `service`, `level` | `logs_all_info` | `{service=~"ticketing\|queue", level="INFO"}` |
| 두 서버 전체 WARN | — | — | Logs | `service`, `level` | `logs_all_warn` | `{service=~"ticketing\|queue", level="WARN"}` |
| 두 서버 전체 ERROR | — | — | Logs | `service`, `level` | `logs_all_error` | `{service=~"ticketing\|queue", level="ERROR"}` |
| 티켓팅 서버 WARN·ERROR | — | — | Logs | `service`, `level` | `logs_ticketing_warn_error` | `{service="ticketing", level=~"WARN\|ERROR"}` |
| 대기열 서버 WARN·ERROR | — | — | Logs | `service`, `level` | `logs_queue_warn_error` | `{service="queue", level=~"WARN\|ERROR"}` |
| 레벨별 초당 로그 발생량 | — | `cps` | Time series | `service`, `level` | `logs_rate_by_level` | `sum by(service, level) (rate({service=~"ticketing\|queue"}[$__rate_interval]))` |

### 5-1. MDC 기반 요청 단위 추적

`MDCLoggingFilter` 가 요청마다 `requestId` 와 `userId` 를 MDC 에 심고, `logback-spring.xml` 의 패턴이 이를 로그 라인에 박아 넣는다.

```
2026-08-25 14:03:11.482 [http-nio-8080-exec-3] INFO  [3f7a9c21] [userId=42] t.d.o.o.c.OrderController - create() 호출
                                                     └requestId┘ └ userId ┘
```

| 의미 | 타입 | 단위 | 추천 패널 | 라벨 | 네이밍 | 쿼리 |
|---|---|---|---|---|---|---|
| 요청 하나의 전체 로그 (단일 서버) | — | — | Logs | `service` | `logs_by_request_id` | `{service="$service"} \|= "$requestId"` |
| 특정 사용자의 전체 로그 | — | — | Logs | `service` | `logs_by_user_id` | `{service=~"ticketing\|queue"} \|= "[userId=$userId]"` |
| 특정 사용자의 경고·에러만 | — | — | Logs | `service`, `level` | `logs_error_by_user_id` | `{service=~"ticketing\|queue", level=~"WARN\|ERROR"} \|= "[userId=$userId]"` |

`requestId` 와 `userId` 는 **Loki 라벨로 만들지 않는다.** 라벨(`service`, `level`)로 스트림을 좁힌 뒤 라인 필터(`|=`)로 본문을 스캔하는 것이 Loki 가 의도한 사용법이고, 위 쿼리가 그 방식이다. 이유는 5-2 참고.

추출이 필요할 때는 `| pattern` 보다 `| regexp` 를 쓴다. `%-5level` 이 `INFO` 는 5칸으로 패딩하고 `ERROR` 는 안 해서 공백 개수가 달라지는데, `pattern` 파서는 리터럴 공백을 정확히 맞춰야 해서 깨진다.

Grafana 대시보드 변수로 `requestId`, `userId` 를 **Textbox** 타입으로 만들어 두면 위 쿼리를 그대로 붙여 쓸 수 있다. Loki 데이터소스의 **Derived fields** 에 `requestId` 추출 정규식을 걸면 로그 패널에서 ID 를 클릭해 바로 해당 요청 전체를 여는 것도 가능하다.

### 5-2. 라벨 카디널리티와 스트림

| | 카디널리티 | 스트림 |
|---|---|---|
| 대상 | 라벨 **하나**의 속성 | 라벨 **조합**의 결과물 |
| 세는 법 | `level` 은 3, `service` 는 2 | `{service="ticketing", env="prod", level="INFO"}` 이 1개 |

Loki 에서 **스트림(stream)** 은 라벨 키-값 조합 하나가 만들어내는 로그 줄기다. 라벨이 하나라도 다르면 별개의 스트림이고, 스트림마다 독립적으로 청크(`/loki/chunks`)에 압축 저장된다. Prometheus 의 시계열(time series)과 같은 개념이다.

Alloy 가 붙이는 라벨은 `service`, `env`, `level` 셋뿐이라(`ASG/*/userdata.sh`) 현재 스트림은 아래가 전부다.

```
{service="ticketing", env="prod", level="INFO"}    {service="queue", env="prod", level="INFO"}
{service="ticketing", env="prod", level="WARN"}    {service="queue", env="prod", level="WARN"}
{service="ticketing", env="prod", level="ERROR"}   {service="queue", env="prod", level="ERROR"}
```

**총 6개.** ASG 로 인스턴스가 10대 떠도 라벨이 같으면 전부 같은 스트림으로 합쳐진다.

#### 스트림 수는 라벨 값 종류의 곱

라벨을 하나 추가하면 스트림이 더해지는 게 아니라 **곱해진다.** 따라서 카디널리티에 유의해야한다.
현재 라벨은 아래와 같다.

| 필드 | 서로 다른 값의 수 | 라벨 적합 | 붙였을 때 스트림 수 |
|---|---|---|---|
| `service` | 2 (ticketing, queue) | ✅ | 2 |
| `env` | 1 (prod) | ✅ | × 1 = 2 |
| `level` | 3 (INFO/WARN/ERROR) | ✅ | × 3 = **6 (현재)** |

#### 넘치면 수집이 거부된다

`loki-config.yml` 의 `limits_config` 에 스트림 한도를 명시하지 않았으므로 Loki 기본값 `max_global_streams_per_user: 5000` 이 적용된다. 6개인 지금은 여유롭지만, 5,000 을 넘는 순간 Alloy 가 `429 Maximum active stream limit exceeded` 를 받고 **로그가 버려진다.**

#### 스트림 수 감시

Loki 자체 메트릭으로 활성 스트림 수를 볼 수 있다. `prometheus.yml` 에 Loki 스크래핑을 추가하면 된다.

```yaml
  - job_name: "loki"
    static_configs:
      - targets: ["loki:3100"]
```

| 의미 | 타입 | 단위 | 추천 패널 | 라벨 | 네이밍 | 쿼리 |
|---|---|---|---|---|---|---|
| 활성 스트림 수 | Gauge | `short` | Time series | 없음 | `loki_active_streams` | `sum(loki_ingester_memory_streams)` |
| 스트림 한도 초과로 버려진 로그 | Counter | `cps` | Time series | `reason` | `loki_discarded_rate` | `sum by(reason) (rate(loki_discarded_samples_total[$__rate_interval]))` |

한도 초과는 조용히 로그가 사라지는 형태로 나타나서 알아채기 어렵다. 라벨을 늘릴 계획이 있다면 위 두 패널을 먼저 띄워두는 편이 좋다.

#### 카디널리티가 높다면? 구조화된 메타데이터 기능 활성화

Loki 3.x 의 **structured metadata** 가 `requestId` 같은 고카디널리티 필드를 스트림을 늘리지 않고 붙이기 위한 기능이다. 다만 지금은 꺼져 있다.

```yaml
# loki-config.yml
allow_structured_metadata: false  # 구조화된 메타데이터 비허용
```

현재 규모에서는 켤 이유가 없다고 판단. 로그 규모가 커지고 `requestId` 조회가 잦아지면 그때 검토할 선택지 정도로 기억해 둔다.

---

## 6. 캐시 히트율 (티켓팅 서버)

| 의미 | 타입 | 단위 | 추천 패널 | 라벨 | 네이밍 | 쿼리 |
|---|---|---|---|---|---|---|
| 캐시 그룹별 전체 히트율 | Counter | `percentunit` | Time series + Stat | `group`, `result` | `cache_hit_ratio_total` | `sum(rate(ticketing_cache_gets_total{result=~"global_hit\|local_hit"}[$__rate_interval])) by (group) / sum(rate(ticketing_cache_gets_total[$__rate_interval])) by (group)` |
| 캐시 그룹별 글로벌(Redis) 히트율 | Counter | `percentunit` | Time series | `group`, `result` | `cache_hit_ratio_global` | `sum(rate(ticketing_cache_gets_total{result="global_hit"}[$__rate_interval])) by (group) / sum(rate(ticketing_cache_gets_total[$__rate_interval])) by (group)` |
| 캐시 그룹별 로컬(Caffeine) 히트율 | Counter | `percentunit` | Time series | `group`, `result` | `cache_hit_ratio_local` | `sum(rate(ticketing_cache_gets_total{result="local_hit"}[$__rate_interval])) by (group)  / sum(rate(ticketing_cache_gets_total[$__rate_interval])) by (group)` |

---

## 7. 가상 스레드 Pinning (대기열 서버)

| 의미 | 타입 | 단위 | 추천 패널 | 라벨 | 네이밍 | 쿼리 |
|---|---|---|---|---|---|---|
| 가상 스레드 pinning 초당 발생 횟수 | Counter | `cps` | Time series | `application` | `jvm_virtual_thread_pinned_rate` | `rate(jvm_threads_virtual_pinned_seconds_count{application="queue"}[3m])` |

평상시 0 이 정상이고 0 이 아닌 구간이 생기는지만 보면 되므로, Time series 에 Thresholds 를 `0` 초과로 걸어두어도 좋다.

---

## 8. 톰캣 스레드 (티켓팅 서버)

`server.tomcat.mbeanregistry.enabled: true` 가 켜져 있어야 수집된다.

| 의미 | 타입 | 단위 | 추천 패널 | 라벨 | 네이밍 | 쿼리 |
|---|---|---|---|---|---|---|
| 처리 중인 스레드 수 | Gauge | `short` | Time series | `instance`, `application` | `tomcat_threads_busy` | `tomcat_threads_busy_threads{application="$application", instance="$instance"}` |
| 생성된 스레드 수 | Gauge | `short` | Time series | `instance`, `application` | `tomcat_threads_current` | `tomcat_threads_current_threads{application="$application", instance="$instance"}` |
| 설정된 최대 스레드 | Gauge | `short` | Stat | `instance`, `application` | `tomcat_threads_max` | `tomcat_threads_config_max_threads{application="$application", instance="$instance"}` |

---

## 부록. 슬로우 쿼리 로그 활성화

슬로우 쿼리 설정 절차

```sql
-- 활성화 현황 및 저장 위치 확인
SHOW VARIABLES LIKE 'slow_query_log';
SHOW VARIABLES LIKE 'slow_query_log_file';
SHOW VARIABLES LIKE 'long_query_time';

-- 슬로우 쿼리 로그 기록 활성화
SET GLOBAL log_output = 'TABLE';                      -- 파일 대신 테이블로
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 1;                       -- 1초 이상으로 정의
SET GLOBAL log_queries_not_using_indexes = 'ON';
```

로그 파일을 열면 아래 형태로 확인됩니다.

```
[root@localhost mysql]# cat localhost-slow.log
/usr/libexec/mysqld, Version: 8.0.41 (Source distribution). started with:
Tcp port: 3306  Unix socket: /var/lib/mysql/mysql.sock
Time                 Id Command    Argument
# Time: 2025-07-27T02:49:52.528663Z
# User@Host: admin[admin] @  [172.30.1.25]  Id:  4008
# Query_time: 32.438005  Lock_time: 0.000003 Rows_sent: 0  Rows_examined: 13716675

query...
```
