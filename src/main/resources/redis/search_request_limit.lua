local count = redis.call('INCR', KEYS[1])
redis.call('EXPIREAT', KEYS[1], ARGV[1])
if count > tonumber(ARGV[2]) then return 0 end
return 1
