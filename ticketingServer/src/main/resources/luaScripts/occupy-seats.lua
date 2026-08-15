-- 여러 좌석을 한 번에 원자적으로 점유(선점) 처리한다.
-- 이미 점유된 좌석이 하나라도 있으면 전체 실패 (all-or-nothing).
-- KEYS[1..n]: 점유할 좌석들의 occupy Key 목록
-- KEYS[n+1] : 사용자별 조회용 Key
-- KEYS[n+2] : 회차별 조회용 Key
-- ARGV[1]: 점유자 userId
-- ARGV[2]: 점유 유지 TTL(초)
-- ARGV[3]: 점유 만료 시각(epoch millis)
-- ARGV[4..]: 점유할 좌석들의 PK 값
local seatCount = #KEYS - 2
local userIndexKey = KEYS[seatCount + 1]
local scheduleIndexKey = KEYS[seatCount + 2]
local userId = ARGV[1]
local ttlSeconds = ARGV[2]
local expiresAt = ARGV[3]

for i = 1, seatCount do
	local occupiedBy = redis.call('GET', KEYS[i])
	if occupiedBy then
		return 0
	end
end

for i = 1, seatCount do
	redis.call('SET', KEYS[i], userId, 'EX', ttlSeconds)
	redis.call('ZADD', userIndexKey, expiresAt, ARGV[3 + i])
	redis.call('ZADD', scheduleIndexKey, expiresAt, ARGV[3 + i])
end

redis.call('EXPIRE', userIndexKey, ttlSeconds)
redis.call('EXPIRE', scheduleIndexKey, ttlSeconds)

return 1
