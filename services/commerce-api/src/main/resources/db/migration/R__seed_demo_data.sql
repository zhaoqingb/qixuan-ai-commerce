-- 开发环境种子数据
--
-- `R__` 前缀 = Flyway 可重复迁移（Repeatable）：文件内容变化时会重新执行。
--
-- 幂等写法用 `ON DUPLICATE KEY UPDATE id = id`，**不用 `INSERT IGNORE`**：
--   INSERT IGNORE 会吞掉所有错误（外键违反、类型不符、非空约束），
--   表现为 Flyway 报 success 但数据根本没进去 —— 排查起来极其痛苦。
--   ON DUPLICATE KEY UPDATE 只处理"主键冲突"，其他错误照常抛出。

-- ① 店铺（商品的外键依赖）
INSERT INTO store (id, store_name, owner_user_id, logo_url, description, status)
VALUES (1, '栖选自营旗舰店', 1, NULL, '官方直营店铺', 'ACTIVE')
ON DUPLICATE KEY UPDATE id = id;

-- ② 分类
-- 注意 parent_id：这张表有自引用外键（父分类必须存在），
-- 根节点必须写 NULL，写 0 会违反外键约束。
INSERT INTO category (id, parent_id, name, sort_order, enabled)
VALUES (1, NULL, '电脑外设', 1, 1)
ON DUPLICATE KEY UPDATE id = id;

-- ③ 商品 SPU
--    注意状态值：SPU 用 ON_SALE，SKU 用 ACTIVE —— 项目里这两个语义不同名，
--    SPU 的列表查询条件是 status='ON_SALE'，SKU 的加购校验是 status='ACTIVE'。
INSERT INTO product_spu
    (id, store_id, category_id, brand_id, title, subtitle, main_image_url, description, status)
VALUES
    (1, 1, 1, NULL, '无线鼠标 静音办公款', '2.4G 无线 · 静音按键',
     'https://picsum.photos/seed/mouse/600/600', '适合办公场景的静音无线鼠标', 'ON_SALE')
ON DUPLICATE KEY UPDATE id = id;

-- ④ SKU
INSERT INTO product_sku
    (id, spu_id, sku_code, spec_json, sale_price, list_price, status)
VALUES
    (1, 1, 'MS-001', '{"color":"黑色"}', 129.00, 169.00, 'ACTIVE')
ON DUPLICATE KEY UPDATE id = id;

-- ⑤ 库存：给足量，方便反复压测（这张表主键是 sku_id，没有 id 列）
INSERT INTO sku_inventory (sku_id, available_qty, locked_qty, sold_qty, version)
VALUES (1, 1000, 0, 0, 0)
ON DUPLICATE KEY UPDATE sku_id = sku_id;

-- ⑥ 秒杀活动：时间相对当前时间计算，保证环境重建后立刻可用
--    1 小时后开始（留足缓冲，避免测试时撞上「至少提前 5 分钟」的校验），持续 10 小时
INSERT INTO seckill_activity (id, name, start_at, end_at, status)
SELECT 1, '无线鼠标 限时秒杀',
       DATE_ADD(NOW(), INTERVAL 1 HOUR),
       DATE_ADD(NOW(), INTERVAL 11 HOUR),
       'DRAFT'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM seckill_activity WHERE id = 1);

-- ⑦ 秒杀商品：99 元、500 件、每人限购 1 件
INSERT INTO seckill_item
    (id, activity_id, sku_id, seckill_price, seckill_stock, sold_count, per_user_limit, reserved_qty)
SELECT 1, 1, 1, 99.00, 500, 0, 1, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM seckill_item WHERE id = 1);
