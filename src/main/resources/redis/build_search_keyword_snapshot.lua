if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
redis.call('ZUNIONSTORE', KEYS[2], #KEYS - 2, unpack(KEYS, 3))
redis.call('EXPIRE', KEYS[2], 60)
return 1
