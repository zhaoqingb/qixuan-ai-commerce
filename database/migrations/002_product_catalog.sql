USE qixuan_ai;

-- ========================================
-- 1. 商家店铺表
-- ========================================

CREATE TABLE store (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '店铺ID',
    name VARCHAR(100) NOT NULL COMMENT '店铺名称',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1正常',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商家店铺表';

-- ========================================
-- 2. SPU 商品表
-- ========================================

CREATE TABLE product_spu (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'SPU ID',
    store_id BIGINT UNSIGNED NOT NULL COMMENT '所属店铺',
    category_id BIGINT UNSIGNED NOT NULL COMMENT '商品分类',
    name VARCHAR(200) NOT NULL COMMENT '商品名称',
    description TEXT COMMENT '商品描述',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿，1上架',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_spu_store (store_id),
    KEY idx_spu_category (category_id),
    CONSTRAINT fk_spu_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT fk_spu_category FOREIGN KEY (category_id) REFERENCES category (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品SPU表';

-- ========================================
-- 3. SKU 商品规格表
-- ========================================

CREATE TABLE product_sku (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
    spu_id BIGINT UNSIGNED NOT NULL COMMENT '所属SPU',
    sku_code VARCHAR(64) NOT NULL COMMENT 'SKU唯一编码',
    spec_json JSON NOT NULL COMMENT '规格属性JSON',
    price DECIMAL(12, 2) NOT NULL COMMENT '商品售价',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0停用，1启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_code (sku_code),
    KEY idx_sku_spu (spu_id),
    CONSTRAINT fk_sku_spu FOREIGN KEY (spu_id) REFERENCES product_spu (id),
    CONSTRAINT chk_sku_price CHECK (price >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品SKU表';

-- ========================================
-- 4. SKU 库存表
-- ========================================

CREATE TABLE sku_inventory (
    sku_id BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID，同时作为主键',
    available_qty INT NOT NULL DEFAULT 0 COMMENT '可售库存',
    locked_qty INT NOT NULL DEFAULT 0 COMMENT '已锁定库存',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (sku_id),
    CONSTRAINT fk_inventory_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id),
    CONSTRAINT chk_available_qty CHECK (available_qty >= 0),
    CONSTRAINT chk_locked_qty CHECK (locked_qty >= 0),
    CONSTRAINT chk_inventory_version CHECK (version >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'SKU库存表';