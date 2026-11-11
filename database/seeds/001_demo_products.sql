USE qixuan_ai;

INSERT INTO
    app_user (id, username, phone, status)
VALUES (
        1,
        'demo_merchant',
        '13800000000',
        'ACTIVE'
    );

INSERT INTO
    store (
        id,
        store_name,
        owner_user_id,
        status
    )
VALUES (1, '栖选数码体验店', 1, 'ACTIVE');

INSERT INTO brand (id, name) VALUES (1, '栖选自营');

INSERT INTO
    category (
        id,
        parent_id,
        name,
        sort_order,
        enabled
    )
VALUES (1, NULL, '数码电子', 1, 1),
    (2, NULL, '服装鞋包', 2, 1),
    (3, NULL, '家用电器', 3, 1),
    (4, NULL, '美妆护肤', 4, 1),
    (5, NULL, '食品生鲜', 5, 1),
    (6, NULL, '运动户外', 6, 1);

INSERT INTO
    category (
        id,
        parent_id,
        name,
        sort_order,
        enabled
    )
VALUES (7, 1, '智能手机', 1, 1);

INSERT INTO
    product_spu (
        id,
        store_id,
        category_id,
        brand_id,
        title,
        description,
        status
    )
VALUES (
        1,
        1,
        1,
        1,
        '无线蓝牙耳机 Pro',
        '主动降噪、长续航无线耳机',
        'ON_SALE'
    );

INSERT INTO
    product_sku (
        id,
        spu_id,
        sku_code,
        spec_json,
        sale_price,
        list_price,
        status
    )
VALUES (
        1,
        1,
        'QX-EAR-BLACK',
        '{"颜色":"曜石黑"}',
        299.00,
        399.00,
        'ACTIVE'
    ),
    (
        2,
        1,
        'QX-EAR-WHITE',
        '{"颜色":"珍珠白"}',
        319.00,
        399.00,
        'ACTIVE'
    );

INSERT INTO
    sku_inventory (
        sku_id,
        available_qty,
        locked_qty,
        sold_qty,
        version
    )
VALUES (1, 20, 0, 0, 0),
    (2, 15, 0, 0, 0);