# queueServer 동시 JOIN 세션 무효화 수정

작업일 2026-08-28 / 대상 모듈 `queueServer`

## 배경

`QueueService.enter()`에서 동시에 들어온 JOIN 요청이 **서로의 세션을 무효화**하는 문제가 있었다.

```java
// 변경 전
long score = redisUtil.increment(counterKey);
redisUtil.zAddIfAbsent(waitingKey, userId, score);        // ZADD NX — 원자적
redisUtil.set(userInfoKey, queueSessionId, SESSION_TTL);  // 평범한 SET — 무조건 덮어씀
```

`zAddIfAbsent`는 ZADD NX라 대기열 중복 등재를 막지만, 바로 다음 줄의 `set`에는 NX가 없어 나중 요청이 `userInfoKey`를 덮어썼다.

재현 경로는 다음과 같다.

1. 사용자가 예매하기를 더블클릭 → JOIN 요청 A, B가 동시 도착
2. A, B 모두 사전 검사(`hasKey(activeKey)` / `zRank`)를 통과
3. ZADD NX는 하나만 성공하지만, A와 B **둘 다** `userInfoKey`에 서로 다른 `queueSessionId`를 씀
4. Redis에는 나중 것만 남고, 둘 다 토큰을 발급받음
5. 먼저 토큰을 받은 클라이언트가 첫 폴링에서 `status()`의 세션 비교에 걸려 `SESSION_REVOKED`

결과적으로 사용자는 한 번 눌렀는데 "다른 화면에서 예매를 이어받았습니다" 모달을 보게 된다. 스파이크 트래픽에서 재현 확률이 올라간다.

## 변경 내용

### 1. `RedisUtil.setIfAbsent()` 추가

`queueServer/src/main/java/queue/global/util/RedisUtil.java`

```java
/**
 * Key가 없을 때만 값을 TTL과 함께 저장합니다. (SET NX EX) 저장 성공 여부를 반환합니다.
 */
public boolean setIfAbsent(String key, String value, Duration ttl) {
    return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, ttl));
}
```

기존 메서드는 변경하지 않았다. 반환 타입은 `hasKey()`와 동일하게 `Boolean.TRUE.equals(...)`로 언박싱해 `boolean`으로 맞췄다.

### 2. `QueueService.enter()` JOIN 경로 원자화

`queueServer/src/main/java/queue/domain/queue/service/QueueService.java`

```java
long score = redisUtil.increment(counterKey);
boolean addedToQueue = Boolean.TRUE.equals(redisUtil.zAddIfAbsent(waitingKey, userId, score));

if (command.getEnterType() == EnterType.JOIN) {
    if (!addedToQueue || !redisUtil.setIfAbsent(userInfoKey, queueSessionId, SESSION_TTL)) {
        throw new GeneralException(QueueErrorCode.ALREADY_JOINED);
    }
} else {
    redisUtil.set(userInfoKey, queueSessionId, SESSION_TTL);
}
```

두 개의 원자적 관문을 통과해야 진행한다.

| 관문 | 지키는 불변식 |
|---|---|
| `zAddIfAbsent` 반환값 | 이미 줄 서 있는가 — 순번 불변식 |
| `setIfAbsent` | 이미 화면을 점유했는가 — 세션 불변식 |

기존에 버려지던 `zAddIfAbsent`의 반환값을 게이트로 활용했다. 짧은 회로 평가라 `addedToQueue`가 false면 `setIfAbsent`는 호출되지 않는다.

**설계 판단 두 가지**

- **JOIN에만 게이트를 건다.** REJOIN은 "새로 입장하기"가 의도라 덮어쓰기가 정상 동작이다. 여기에 게이트를 걸면 모달의 새로 입장하기 버튼이 동작하지 않는다.
- **사전 검사(1번 블록)는 그대로 둔다.** `userInfoKey`는 3분, `activeKey`는 7분 TTL이라 만료 시점이 어긋난다. 승격 후 폴링을 멈춘 사용자는 `userInfoKey`만 먼저 만료되어 `setIfAbsent`가 통과해버리므로 `activeKey` 검사가 여전히 필요하다.

### 3. 테스트

`queueServer/src/test/java/queue/domain/queue/service/QueueServiceTest.java`

- `카운터로_받은_순번으로_줄서고_저장된_세션ID가_토큰에도_담긴다` — JOIN이 `set` 대신 `setIfAbsent`를 쓰도록 바뀌었으므로 스텁과 검증을 교체
- `JOIN인데_동시_요청이_먼저_줄서면_ALREADY_JOINED_예외가_발생한다` — 신규. `zAddIfAbsent`가 false일 때 `setIfAbsent`가 호출되지 않고 토큰도 발급되지 않는지 확인
- `JOIN인데_동시_요청이_먼저_화면을_점유하면_ALREADY_JOINED_예외가_발생한다` — 신규. `zAddIfAbsent`는 통과했지만 `setIfAbsent`가 false인 경우

## 검증 결과

```
./gradlew :queueServer:test --tests '*QueueServiceTest'
BUILD SUCCESSFUL — 24 tests, 0 failures, 0 skipped
```

| 블록 | 테스트 수 |
|---|---|
| Enter | 6 (기존 4 + 신규 2) |
| Status | 5 |
| Takeover | 3 |
| PollInterval | 10 |

전체 모듈 실행(`./gradlew :queueServer:test`) 시 `QueueApplicationTests.contextLoads()` 1건이 실패하는데, `RedisTestContainersConfig`가 Testcontainers로 Redis를 띄우려다 Docker Desktop 미실행으로 나는 환경 문제다. 이번 변경과 무관하며 Docker를 켜면 해소된다.

## 후속 과제

코드 리뷰에서 함께 확인된 항목이다. 이번 작업 범위에는 넣지 않았다.

| 심각도 | 항목 | 위치 |
|---|---|---|
| HIGH | Actuator `env`·`loggers`가 미인증 노출. `loggers`는 POST로 런타임 로그 레벨 변경 가능 | `application-prod.yaml:28` |
| MEDIUM | `ZPOPMIN`이 이탈자까지 배치 정원으로 소비해 승격 처리량이 설정값 이하로 떨어짐 | `promote-queue.lua:14` |
| MEDIUM | `takeover()`에서 `activeKey` TTL이 두 검사 사이에 만료되면 `rank: 0, isActive: false`라는 불가능한 상태 반환 | `QueueService.java:166-204` |
| MEDIUM | 기본 프로파일이 `local`이고 개발용 JWT 시크릿이 저장소에 커밋됨 | `application.yaml:3`, `application-local.yaml:20` |
| LOW | `counterKey`에 TTL이 없어 회차 수만큼 영구 누적 | `QueueRedisKeys.java:20` |
| LOW | `REDIRECT_ENDPOINT` 하드코딩 + TODO | `QueueService.java:29` |
| LOW | `QueueService:80`만 `opsForZSet()`으로 캡슐화를 우회 | `RedisUtil.java:34` |

원자성을 더 끌어올리려면 `enter()` 전체를 Lua로 묶는 방법이 있다. 사전 검사와 ZADD 사이에 승격 스케줄러가 끼어들어 대기열과 작업열에 동시 존재하게 되는 좁은 경합은 애플리케이션 레벨에서 막을 수 없다. Redis 왕복도 5회에서 1회로 줄어든다.

다만 Lua로 갈 때는 직렬화 함정을 주의해야 한다. `RedisConfig`가 값 직렬화에 `GenericJackson2JsonRedisSerializer`를 쓰므로 `RedisUtil.set()`이 저장하는 실제 바이트는 따옴표가 붙은 `"550e8400-..."`이다. Lua에서 날 문자열로 쓰면 이후 `RedisUtil.get()`의 Jackson 파싱이 실패한다. 기존 `promote-queue.lua`가 멀쩡한 것은 GET한 바이트를 그대로 SET해 바이트 투명하게 복사하기 때문이다.
