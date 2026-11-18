-- 秒杀模块三张表。
--
-- 之前是手工建在开发库里的，没进版本控制 —— 新环境拉下代码会缺表，
-- 这里按当时的实际结构补录，保证「clone 下来就能跑」。
--
-- 用 IF NOT EXISTS：已有开发库里表已存在，直接跳过。

CREATE TABLE IF NOT EXISTS `seckill_activity` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `start_at` datetime(3) NOT NULL,
  `end_at` datetime(3) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ONLINE/OFFLINE',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_activity_time` (`start_at`,`end_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动';

CREATE TABLE IF NOT EXISTS `seckill_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `activity_id` bigint unsigned NOT NULL,
  `sku_id` bigint unsigned NOT NULL,
  `seckill_price` decimal(10,2) NOT NULL,
  `seckill_stock` int NOT NULL COMMENT '活动剩余库存',
  `sold_count` int NOT NULL DEFAULT '0' COMMENT '已售（与 seckill_stock 之和恒等于初始库存）',
  `per_user_limit` int NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_sku` (`activity_id`,`sku_id`),
  KEY `idx_item_sku` (`sku_id`),
  CONSTRAINT `fk_item_activity` FOREIGN KEY (`activity_id`) REFERENCES `seckill_activity` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀商品';

CREATE TABLE IF NOT EXISTS `seckill_order` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `activity_id` bigint unsigned NOT NULL,
  `item_id` bigint unsigned NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `sku_id` bigint unsigned NOT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `amount` decimal(10,2) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED/CANCELLED',
  `trade_order_id` bigint unsigned DEFAULT NULL COMMENT '转成正式订单后的 id',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seckill_order_no` (`order_no`),
  UNIQUE KEY `uk_seckill_user_item` (`user_id`,`item_id`),
  KEY `idx_seckill_item` (`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单';
