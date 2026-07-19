-- 여러 좌석을 한 번에 원자적으로 점유(선점) 처리한다.
-- 다른 사용자가 점유한 좌석이 하나라도 있으면 전체 실패 (all-or-nothing).
-- KEYS: 점유할 좌석들의 occupy Key 목록
-- ARGV[1]: 점유자 userId
-- ARGV[2]: 점유 유지 TTL(초)
local userId = ARGV[1]
local ttlSeconds = ARGV[2]

for i = 1, #KEYS do
	local occupiedBy = redis.call('GET', KEYS[i])
	if occupiedBy and occupiedBy ~= userId then
		return 0
	end
end

for i = 1, #KEYS do
	redis.call('SET', KEYS[i], userId, 'EX', ttlSeconds)
end

return 1
