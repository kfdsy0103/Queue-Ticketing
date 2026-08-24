# 2계층 캐시 및 스템피드 대응

# 배경

- 콘서트 티켓팅에 입장하면 좌석 정보 뿐만 아니라, 콘서트와 관련된 부가적인 정보도 함께 노출이 된다.
- 만약 수만명이 해당 정보에 대해 조회를 요청하여 바로 DB로 향하는 경우 서버가 죽을 위험이 있다.
- 콘서트에 대한 정보는 갱신이 자주 이루어지지 않고 여러 사용자가 조회하기에 캐싱의 대상으로 적절하다.

## 캐싱 위치 선정

- Redis는 이미 대기열 기능으로 사용 중이며, 역인덱스를 걸어 여러 조회 API에서 레디스를 활발히 사용중이다.
- 따라서 콘서트 상세 조회처럼 트래픽이 몰리는 캐시를 Redis(L2) 단독으로만 처리하면, 이미 대기열/좌석 점유/역인덱스로 부하가 걸려있는 Redis에 조회성 트래픽까지 얹는 셈이라 병목이 커진다.
- 반대로 애플리케이션 로컬 메모리(L1) 단독으로만 처리하면, 인스턴스마다 캐시 적재/만료 시점이 제각각이라 다중 인스턴스 환경에서 정합성이 깨지고, TTL 만료도 인스턴스 수만큼 따로따로 발생해 스템피드가 인스턴스 수에 비례해 커진다.
- 그래서 로컬(L1, Caffeine)을 대부분의 트래픽을 흡수하는 1차 방어선으로 두고, Redis(L2)는 인스턴스 간 공유되는 진실 소스이자 L1 미스 시의 2차 방어선으로 두는 2계층(L1+L2) 합성 구조를 택했다. 실제로 Redis까지 도달하는 트래픽은 "로컬 캐시가 비어있는 순간"으로만 제한되므로, 기존 대기열/역인덱스 트래픽과의 경합을 최소화할 수 있다.

# 2계층 캐시 구조

`ticketing.global.cache` 패키지에 `CacheType` 3종으로 계층을 구분한다.

| CacheType | 설명 | 사용처 |
|---|---|---|
| `LOCAL` | 로컬(Caffeine) 캐시만 적용 | 현재 미사용 (필요 시 확장 지점) |
| `GLOBAL` | Redis 캐시만 적용 | 현재 미사용 (필요 시 확장 지점) |
| `COMPOSITE` | 로컬 + Redis 모두 적용 | `CONCERT_DETAIL`, `CONCERT_SCHEDULE_DETAIL` |

`CacheGroup` enum이 그룹별로 L1/L2 TTL과 L1 최대 크기를 따로 갖는다.

| CacheGroup | CacheType | L2(Redis) TTL | L1(Caffeine) TTL | L1 maximumSize |
|---|---|---|---|---|
| `CONCERT_DETAIL` | COMPOSITE | 1분 | 10초 | 1000 |
| `CONCERT_SCHEDULE_DETAIL` | COMPOSITE | 1분 | 10초 | 1000 |

L1 TTL을 L2보다 훨씬 짧게 준 이유는, 로컬 캐시는 인스턴스 개수만큼 존재해 방치하면 인스턴스 간 데이터가 오래 벌어질 수 있기 때문이다. L1은 짧은 주기로 자주 갱신하되, 그 갱신 원본은 매번 DB가 아니라 Redis(L2)를 보게 해서 DB 부하를 늘리지 않는다.

이 캐시는 스프링 `org.springframework.cache.Cache` SPI를 구현한 `TieredCache`(`ticketing/global/cache/spring/TieredCache.java`)로 구현되어 있어, 비즈니스 코드는 표준 `@Cacheable`만 붙이면 된다.

```java
@Cacheable(cacheNames = CacheName.CONCERT_DETAIL, key = "#command.concertId", sync = true)
public FindDTO.Result find(FindDTO.Command command) { ... }
```

# 캐시 스템피드 대응

캐시가 비거나 만료되는 순간 수만 명의 요청이 동시에 원본(Redis 또는 DB)으로 몰리는 문제(cache stampede)를, 상황별로 3중 방어로 대응한다.

| 상황 | 방어 기법 | 동작 위치 | 핵심 아이디어 |
|---|---|---|---|
| TTL 만료 임박 | PER (확률적 조기 재계산) | L1/L2 조회 경로 공통 | 실제로 만료되기 전에 일부 요청만 확률적으로 백그라운드 갱신을 트리거해, "동시에 만료되어 한꺼번에 몰리는" 상황 자체를 분산시킨다 |
| L1 미스 → L2 조회 | SingleFlight | 인스턴스(JVM) 로컬 | 같은 인스턴스 안에서 동시에 들어온 같은 키 요청은 1개 스레드(리더)만 실제로 조회하고, 나머지는 그 결과를 기다렸다 공유받는다 |
| L2 미스 → DB 조회 | 분산락 (Redisson) | 인스턴스 간 (Redis) | 여러 인스턴스가 동시에 L2도 비어있는 걸 보면, 분산락으로 1개 인스턴스만 DB를 조회하고 나머지는 대기 후 갱신된 L2를 다시 읽는다 |

### PER (확률적 조기 재계산)

`TieredCache.shouldRecompute(timeToCompute, remainingTTL)`가 다음 공식으로 조기 갱신 여부를 확률적으로 결정한다.

```
timeToCompute * BETA * -log(random()) > remainingTTL
```

- `timeToCompute`: 마지막으로 원본(DB)을 조회하는 데 걸린 시간.
- `remainingTTL`: 캐시가 실제로 만료되기까지 남은 시간.
- 남은 TTL이 얼마 없거나, 원본 조회 자체가 오래 걸리는 값일수록 조기 갱신될 확률이 높아진다. 값이 실제로 만료되어 "전원 캐시 미스"가 나기 전에, 소수의 요청만 미리 백그라운드에서 갱신해두는 방식이라 TTL이 동시에 끝나는 상황 자체를 줄인다.
- 조기 갱신은 `submitRefreshTaskInBackground`로 별도 스레드풀에서 비동기 수행되며, 같은 키에 대한 갱신 작업이 중복 제출되지 않도록 `inFlightKeys`(ConcurrentHashMap)로 막는다. 즉 이 경로는 원래 요청의 응답 속도에 영향을 주지 않는다.

### SingleFlight (L1 미스 시, 인스턴스 로컬 방어)

`SingleFlightExecutor`는 분산락 없이 JVM 내부 `ConcurrentHashMap<String, CompletableFuture<?>>`만으로 동작한다.

- 같은 키로 먼저 들어온 요청이 "리더"가 되어 실제 조회(L2 또는 DB)를 수행한다.
- 뒤이어 들어온 같은 키의 요청들은 리더의 `CompletableFuture`가 끝날 때까지 `join()`으로 대기했다가 같은 결과를 공유받는다.
- 분산락을 쓰지 않는 이유는 이 방어가 "같은 인스턴스 안에서의 중복 조회"만 막으면 되기 때문이다. 인스턴스가 여러 대여도 각 인스턴스는 자신의 로컬 캐시(L1)를 갖고 있으므로, 인스턴스 간 조율은 다음 단계(L2 조회 시 분산락)에서 처리한다.

### 분산락 (L2 미스 시, 인스턴스 간 방어)

`RedisLockService`(Redisson 기반)로 L2(Redis)도 비어있을 때 여러 인스턴스가 동시에 DB를 때리는 것을 막는다.

- 락 획득 대기 2초(`LOCK_WAIT`), 락 유지 5초(`LOCK_LEASE`).
- 락을 잡지 못한 인스턴스도 무한정 대기하지 않는다. 락 획득에 실패해도 일단 L2를 다시 한 번 조회해보고(그 사이 락을 잡은 다른 인스턴스가 이미 채워놨을 수 있음), 그래도 비어있으면 락 없이 DB 조회를 강행한다(가용성을 정합성보다 우선). 이 경로는 "완전 차단"이 아니라 "최대한 한 곳으로 몰아주는" 완화책에 가깝다.

# 캐시 무효화 (Pub/Sub)

L2(Redis)에 새 값을 쓸 때마다 로컬(L1)도 즉시 갱신해야 인스턴스 간 데이터가 벌어지지 않는다. 이를 위해 Redis Pub/Sub 채널(`cache:evict`)로 무효화 이벤트를 전파한다.

- `CacheEvictPublisher`가 L2에 값을 쓰는 시점(`saveIntoGlobal`)에 `(cacheName, cacheKey, instanceId)`를 발행한다.
- `CacheEvictSubscriber`가 이를 구독해 다른 인스턴스들의 L1 캐시를 무효화시킨다.
- 발행자 자신의 `instanceId`와 일치하는 메시지는 무시해서, 이미 갱신을 마친 인스턴스가 스스로를 다시 무효화하는 불필요한 낭비를 막는다.
- 발행 실패 시 `@Retryable`로 최대 3회, 100ms 간격 재시도하고, 최종 실패는 로그로만 남긴다(재시도 소진 시 해당 인스턴스의 L1은 최대 L1 TTL(10초)까지만 stale 상태로 남고, 그 이후엔 TTL 만료로 자연 정리되므로 별도 보정 로직은 두지 않았다).

# 캐시 워밍업

티켓 오픈 순간 첫 조회 자체가 몰려서 스템피드가 나는 것을 막기 위해, 오픈 전에 미리 캐시를 채워둔다.

- `ConcertScheduleScheduler`가 티켓 오픈 10분 전부터 10초 간격으로 `warmUpCache()`를 호출한다.
- `warmUpCache()`도 `find()`와 동일한 `@Cacheable(..., sync = true)`가 걸려 있어, 이 호출 자체가 L1+L2를 미리 채워놓는 효과를 낸다. 오픈 순간 실제 사용자 요청이 몰려도 이미 캐시가 채워져 있어 DB까지 내려가는 요청이 없다.

# 캐싱 대상에서 제외한 것

다음은 "값이 자주 안 바뀌고 여럿이 읽는" 캐시 성격이 아니라 상태/락 관리이므로 이 2계층 캐시 대상에서 제외했다.

- 좌석 점유(`ScheduleSeatFacadeService`) — 점유 여부 자체가 매 순간 바뀌는 상태값이라 TTL 기반 캐시가 아니라 Redis Lua 스크립트로 원자적으로 관리한다.
- 대기열(`QueuePromotionService`) — 마찬가지로 계속 바뀌는 순번/상태값.
- 주문 확정 락(`OrderCommandService`) — 분산락이지 조회 캐시가 아니다.

# 현재 적용 현황

| 대상 | CacheGroup | 위치 |
|---|---|---|
| 콘서트 상세 조회 | `CONCERT_DETAIL` | `ConcertQueryService.find()` / `warmUpCache()` |
| 콘서트 회차 상세 조회 | `CONCERT_SCHEDULE_DETAIL` | `ConcertScheduleQueryService.find()` / `warmUpCache()` |
