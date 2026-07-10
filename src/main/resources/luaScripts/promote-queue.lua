-- 대기열(ZSET)에서 상위 N명을 원자적으로 꺼내 작업열(Hash) 진입 처리한다.
-- KEYS[1]: 대기열 ZSET Key
-- KEYS[2]: 작업열 Hash Key
-- ARGV[1]: 이번에 승격시킬 인원 수
-- ARGV[2]: 작업열 진입 필드의 TTL(초)
local waitingKey = KEYS[1]
local activeKey = KEYS[2]
local batchSize = tonumber(ARGV[1])
local ttlSeconds = ARGV[2]

local popped = redis.call('ZPOPMIN', waitingKey, batchSize)

local count = 0
for i = 1, #popped, 2 do
	local userId = popped[i]
	redis.call('HSET', activeKey, userId, '1')
	redis.call('HEXPIRE', activeKey, ttlSeconds, 'FIELDS', 1, userId)
	count = count + 1
end

return count
