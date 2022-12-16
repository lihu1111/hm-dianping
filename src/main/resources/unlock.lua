local key = KEYS[1]
local threadId = ARGV[1]
-- 锁的key
-- 获取锁中的线程标识 get key\
local id = redis.call('get', key)
if(id == threadId) then
    return redis.call('del', key)
end
return 0