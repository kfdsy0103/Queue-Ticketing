-- 여러 좌석의 occupy Key를, 여전히 지정된 userId 소유일 때만 원자적으로 해제한다.
-- 이미 다른 사용자가 재점유했다면 (소유자가 다르면) 그 Key는 건드리지 않고 건너뛴다.
-- KEYS[1..n]: 해제할 좌석들의 occupy Key 목록
-- KEYS[n+1] : 사용자별 조회용 Key
-- ARGV[1]: 호출하는 사람의 userId
-- ARGV[2..]: 점유 좌석들의 PK 값
local seatCount = #KEYS - 1
local userIndexKey = KEYS[seatCount + 1]

for i = 1, seatCount do
	local occupiedBy = redis.call('GET', KEYS[i])
	if occupiedBy == ARGV[1] then
		redis.call('DEL', KEYS[i])
	end
	redis.call('ZREM', userIndexKey, ARGV[1 + i])
end

return 1
