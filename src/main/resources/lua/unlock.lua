-- 这⾥的 KEYS[1] 就是锁的key，这⾥的ARGV[1] 就是当前线程标示
-- 获取锁中的标示，判断是否与当前线程标示⼀致
if (redis.call('GET', KEYS[1]) == ARGV[1]) then
    -- ⼀致，则删除锁
    return redis.call('DEL', KEYS[1])
end
-- 不⼀致，则直接返回
return 0
