-- KEYS[1]: Refresh Token 세션 Hash 키
-- KEYS[2]: 사용자의 sessionId 목록 Set 키
--
-- ARGV[1]: 제출된 RT의 tokenId
-- ARGV[2]: 제출된 RT의 hash
-- ARGV[3]: 새 RT의 tokenId
-- ARGV[4]: 새 RT의 hash
-- ARGV[5]: 현재 시각 epoch millis
-- ARGV[6]: 새 previous token grace 만료 시각 epoch millis
-- ARGV[7]: 새 RT 만료 시각 epoch millis
-- ARGV[8]: 새 Redis TTL millis
-- ARGV[9]: sessionId
-- ARGV[10]: userId
--
-- 반환값
-- 0: NOT_FOUND
-- 1: ROTATED
-- 2: REUSE_DETECTED

local sessionKey = KEYS[1]
local sessionsKey = KEYS[2]

if redis.call('EXISTS', sessionKey) == 0 then
    return 0
end

local storedUserId =
    redis.call('HGET', sessionKey, 'userId')

if storedUserId ~= ARGV[10] then
    redis.call('DEL', sessionKey)
    redis.call('SREM', sessionsKey, ARGV[9])
    return 2
end

local currentTokenId =
    redis.call('HGET', sessionKey, 'currentTokenId')

local currentTokenHash =
    redis.call('HGET', sessionKey, 'currentTokenHash')

local previousTokenId =
    redis.call('HGET', sessionKey, 'previousTokenId')

local previousTokenHash =
    redis.call('HGET', sessionKey, 'previousTokenHash')

local previousValidUntil =
    redis.call('HGET', sessionKey, 'previousValidUntil')

local presentedTokenId = ARGV[1]
local presentedTokenHash = ARGV[2]
local now = tonumber(ARGV[5])

local matchesCurrent =
    currentTokenId == presentedTokenId
    and currentTokenHash == presentedTokenHash

local matchesPrevious =
    previousTokenId == presentedTokenId
    and previousTokenHash == presentedTokenHash

if matchesCurrent then
    redis.call(
        'HSET',
        sessionKey,
        'previousTokenId', currentTokenId,
        'previousTokenHash', currentTokenHash,
        'previousValidUntil', ARGV[6],
        'currentTokenId', ARGV[3],
        'currentTokenHash', ARGV[4],
        'expiresAt', ARGV[7]
    )

    redis.call('PEXPIRE', sessionKey, ARGV[8])
    redis.call('SADD', sessionsKey, ARGV[9])
    redis.call('PEXPIRE', sessionsKey, ARGV[8])

    return 1
end

if matchesPrevious then
    local graceDeadline =
        tonumber(previousValidUntil)

    if graceDeadline ~= nil and now <= graceDeadline then
        -- 직전 RT의 grace 사용을 한 번 허용한다.
        -- 현재 RT를 previous로 옮기고 새 RT를 current로 저장한다.
        redis.call(
            'HSET',
            sessionKey,
            'previousTokenId', currentTokenId,
            'previousTokenHash', currentTokenHash,
            'previousValidUntil', ARGV[6],
            'currentTokenId', ARGV[3],
            'currentTokenHash', ARGV[4],
            'expiresAt', ARGV[7]
        )

        redis.call('PEXPIRE', sessionKey, ARGV[8])
        redis.call('SADD', sessionsKey, ARGV[9])
        redis.call('PEXPIRE', sessionsKey, ARGV[8])

        return 1
    end
end

-- 현재 또는 허용된 previous RT가 아니라면 재사용으로 간주한다.
-- 해당 세션은 즉시 제거하고 Java에서 사용자의 나머지 세션도 제거한다.
redis.call('DEL', sessionKey)
redis.call('SREM', sessionsKey, ARGV[9])

return 2
