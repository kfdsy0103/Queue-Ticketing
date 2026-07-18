-- 대기열(ZSET)에서 상위 N명을 원자적으로 꺼내 작업열(Hash) 진입 처리한다.
-- 진입 시 세션 Hash의 queueSessionId를 작업열 필드 값으로 복사한다. (좌석 점유 단계의 화면 판별용)
-- KEYS[1]: 대기열 ZSET Key
-- KEYS[2]: 작업열 Hash Key
-- KEYS[3]: 사용자 세션 Hash Key (field: userId, value: queueSessionId)
-- ARGV[1]: 이번에 승격시킬 인원 수
-- ARGV[2]: 작업열 진입 필드의 TTL(초)
local waitingKey = KEYS[1]
local activeKey = KEYS[2]
local userInfoKey = KEYS[3]
local batchSize = tonumber(ARGV[1])
local ttlSeconds = ARGV[2]

local popped = redis.call('ZPOPMIN', waitingKey, batchSize)

local count = 0
for i = 1, #popped, 2 do
	local userId = popped[i]
	local sessionId = redis.call('HGET', userInfoKey, userId)
	-- 세션이 만료된(폴링이 끊긴 지 오래된) 사용자는 보고 있는 화면이 없으므로 승격하지 않고 버린다
	if sessionId then
		redis.call('HSET', activeKey, userId, sessionId)
		redis.call('HEXPIRE', activeKey, ttlSeconds, 'FIELDS', 1, userId)
		count = count + 1
	end
end

return count