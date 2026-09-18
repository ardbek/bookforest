-- All keys share the ranking hash tag; arguments are server-validated.
if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
local minute = redis.call('INCR', KEYS[3])
redis.call('EXPIREAT', KEYS[3], ARGV[5])
local hour = redis.call('INCR', KEYS[4])
redis.call('EXPIREAT', KEYS[4], ARGV[6])
if minute > tonumber(ARGV[7]) or hour > tonumber(ARGV[8]) then return 2 end
if ARGV[9] ~= '1' then return 3 end
if redis.call('EXISTS', KEYS[2]) == 1 then return 4 end
redis.call('SET', KEYS[2], '1', 'EX', ARGV[2])
redis.call('ZINCRBY', KEYS[5], 1, ARGV[3])
redis.call('EXPIRE', KEYS[5], ARGV[4])
return 1
