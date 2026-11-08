USE qixuan_ai;

-- 创建商家店铺
INSERT INTO store (name, status) VALUES ('栖选数码体验店', 1);

SET @store_id = LAST_INSERT_ID();

-- 创建商品 SPU
INSERT INTO
    product_spu (
        store_id,
        category_id,
        name,
        description,
        status
    )
VALUES (
        @store_id,
        1,
        '无线蓝牙耳机 Pro',
        '主动降噪、长续航无线耳机',
        1
    );

SET @spu_id = LAST_INSERT_ID();

-- 创建 SKU
INSERT INTO
    product_sku (
        spu_id,
        sku_code,
        spec_json,
        price,
        status
    )
VALUES (
        @spu_id,
        'QX-EAR-BLACK',
        '{"颜色":"曜石黑"}',
        299.00,
        1
    ),
    (
        @spu_id,
        'QX-EAR-WHITE',
        '{"颜色":"珍珠白"}',
        319.00,
        1
    );

-- 初始化库存
INSERT INTO
    sku_inventory (
        sku_id,
        available_qty,
        locked_qty,
        version
    )
SELECT
    id,
    CASE
        WHEN sku_code = 'QX-EAR-BLACK' THEN 20
        ELSE 15
    END,
    0,
    0
FROM product_sku
WHERE
    spu_id = @spu_id;