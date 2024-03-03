-- 1.参数列表
-- 1.1.优惠券id
local voucherId = ARGV[1]
-- 1.2.⽤户id
local userId = ARGV[2]
-- 1.3.订单id
local orderId = ARGV[3]
-- 2.数据key
-- 2.1.库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2.订单key
local orderKey = 'seckill:order:' .. voucherId


-- 3.4.扣库存 incrby stockKey -1
redis.call('incrby', stockKey, 1)
-- 3.5.下单（保存⽤户）sadd orderKey userId
redis.call('srem', orderKey, userId)
---- 3.6.发送消息到队列中， XADD stream.orders * k1 v1 k2 v2 ...
--redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id',
--        orderId)
return 0
