-- 여러 좌석의 occupy Key를, 여전히 지정된 userId 소유일 때만 원자적으로 해제한다.
-- 이미 다른 사용자가 재점유했다면 (소유자가 다르면) 그 Key는 건드리지 않고 건너뛴다.
-- KEYS: 해제할 좌석들의 occupy Key 목록
-- ARGV[1]: 원래 점유자 userId
for i = 1, #KEYS do
	local occupiedBy = redis.call('GET', KEYS[i])
	if occupiedBy == ARGV[1] then
		redis.call('DEL', KEYS[i])
	end
end

return 1
