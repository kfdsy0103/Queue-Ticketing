-- 여러 좌석이 모두 지정된 userId에 의해 점유 중인지 확인한다. (읽기 전용, 상태 변경 없음)
-- 좌석이 하나라도 점유되어 있지 않거나 다른 사용자가 점유 중이면 실패로 판단한다.
-- KEYS: 확인할 좌석들의 Redis Key 목록
-- ARGV[1]: 확인할 userId
local userId = ARGV[1]

for i = 1, #KEYS do
	local occupiedBy = redis.call('GET', KEYS[i])
	if occupiedBy ~= userId then
		return 0
	end
end

return 1
