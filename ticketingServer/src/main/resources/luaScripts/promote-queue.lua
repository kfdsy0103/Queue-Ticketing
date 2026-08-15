-- 대기열(ZSET)에서 상위 N명을 ZPOPMIN으로 꺼내 Active 진입 처리.
-- 진입 시 사용자의 세션 키인 queueSessionId를 작업열 Key 값으로 복사.
-- KEYS[1]: 대기열 ZSET Key
-- KEYS[2]: 작업열 Key 접두어 :{userId}
-- KEYS[3]: 사용자 세션 Key 접두어 :{userId}
-- ARGV[1]: 이번에 승격시킬 인원 수
-- ARGV[2]: 작업열 Key의 TTL(초)
local waitingKey = KEYS[1]
local activeKeyPrefix = KEYS[2]
local userInfoKeyPrefix = KEYS[3]
local batchSize = tonumber(ARGV[1])
local ttlSeconds = ARGV[2]

local popped = redis.call('ZPOPMIN', waitingKey, batchSize)

local count = 0
for i = 1, #popped, 2 do
	local userId = popped[i]
	local sessionId = redis.call('GET', userInfoKeyPrefix .. userId)
	if sessionId then
		redis.call('SET', activeKeyPrefix .. userId, sessionId, 'EX', ttlSeconds)
		count = count + 1
	end
end

return count
