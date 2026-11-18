-- 秒杀活动库存预留。
--
-- 问题：秒杀库存（seckill_item.seckill_stock）和 sku 可用库存（sku_inventory.available_qty）
-- 原本是两本互不相干的账。活动配了 500 件，sku 可能只有 138 件可用，
-- 前 136 单能转单，第 137 单才会在「用户点支付」时报库存不足 —— 太晚了，用户已经抢到。
--
-- 解法：活动上线前把这批货从 sku 的 available 划到 locked（预留），
-- 秒杀卖的就是这批预留货，转单时不再重复锁，支付时 locked → sold。
--
-- reserved_qty 记录预留了多少，用于：
--   1. 判断是否已预留（防止重复预留）
--   2. 活动结束时把没卖完的部分还回 available
ALTER TABLE `seckill_item`
  ADD COLUMN `reserved_qty` int NOT NULL DEFAULT 0
  COMMENT '已从 sku_inventory 预留的件数。大于 0 表示转单时无需再锁库存';
