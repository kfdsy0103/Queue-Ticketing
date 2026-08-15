-- 여러 좌석의 occupy Key를, 여전히 지정된 userId 소유일 때만 원자적으로 해제한다.
-- 이미 다른 사용자가 재점유했다면 (소유자가 다르면) 그 Key는 건드리지 않고 건너뛴다.
-- KEYS[1..n]: 해제할 좌석들의 occupy Key 목록
-- KEYS[n+1] : 사용자별 조회용 Key
-- KEYS[n+2] : 회차별 조회용 Key
-- ARGV[1]: 호출자 userId
-- ARGV[2..]: 해제할 좌석들의 PK 값
local seatCount = #KEYS - 2
local userIndexKey = KEYS[seatCount + 1]
local scheduleIndexKey = KEYS[seatCount + 2]

for i = 1, seatCount do
	local occupiedBy = redis.call('GET', KEYS[i])
	-- 호출자와 실제 점유자 일치 검증
	if occupiedBy == ARGV[1] then
		redis.call('DEL', KEYS[i])
		redis.call('ZREM', scheduleIndexKey, ARGV[1 + i])
	end
	redis.call('ZREM', userIndexKey, ARGV[1 + i])
end

return 1
