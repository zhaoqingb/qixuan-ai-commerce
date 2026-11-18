-- 修复：取消订单后无法再次购买。
--
-- 问题根因：uk_seckill_user_item(user_id, item_id) 只看这两列，不看 status。
-- 用户取消订单后行还在，再次抢购时 INSERT IGNORE 会撞唯一索引被静默跳过 ——
-- 库存扣了、订单没落库，名额凭空消失。
--
-- 解法：加 active 列参与唯一索引，利用 MySQL「唯一索引里多个 NULL 不算重复」的规则：
--   有效订单  active = 1     → (user_id, item_id, 1) 冲突，防重复下单
--   已取消    active = NULL  → (user_id, item_id, NULL) 不参与冲突，可以再买
--   多条已取消               → 互相也不冲突

ALTER TABLE `seckill_order`
  ADD COLUMN `active` tinyint NULL DEFAULT 1
  COMMENT '1=有效订单，NULL=已取消。NULL 不参与唯一索引，允许取消后重新购买';

-- 历史数据：已经取消过的记录必须置 NULL，否则用户仍然买不了
UPDATE `seckill_order` SET `active` = NULL WHERE `status` = 'CANCELLED';

DROP INDEX `uk_seckill_user_item` ON `seckill_order`;

CREATE UNIQUE INDEX `uk_seckill_user_item` ON `seckill_order` (`user_id`, `item_id`, `active`);
