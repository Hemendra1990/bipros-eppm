-- Per-user fixed-hour rate limit. KEYS[1] is the bucket key
-- (e.g. analytics:rl:<userId>:<floor(epochSeconds/3600)>). One INCR + EXPIRE on
-- the first hit of the window; subsequent hits just INCR. Returns three values:
--   allowed (1|0), current count, ttl seconds remaining.
-- Atomic: the full check-and-increment runs in a single Redis round trip.
--
-- ARGV[1] = limit (max queries per window)
-- ARGV[2] = window seconds (e.g. 3600)
local cur = redis.call('INCR', KEYS[1])
if cur == 1 then
  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
end
local ttl = redis.call('TTL', KEYS[1])
local allowed = (cur <= tonumber(ARGV[1])) and 1 or 0
return {allowed, cur, ttl}
