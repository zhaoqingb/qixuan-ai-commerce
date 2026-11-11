-- 1. 统一用户：买家、店主、员工都使用一套身份
CREATE TABLE app_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    phone VARCHAR(32) NULL,
    email VARCHAR(128) NULL,
    password_hash VARCHAR(255) NULL,
    avatar_url VARCHAR(512) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_phone (phone),
    UNIQUE KEY uk_user_email (email)
) ENGINE = InnoDB COMMENT = '统一用户账号';

-- 2. 商家店铺及门店成员（店铺内角色由后端验证）
CREATE TABLE store (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    store_name VARCHAR(128) NOT NULL,
    owner_user_id BIGINT UNSIGNED NOT NULL,
    logo_url VARCHAR(512) NULL,
    description TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_store_owner (owner_user_id),
    CONSTRAINT fk_store_owner FOREIGN KEY (owner_user_id) REFERENCES app_user (id)
) ENGINE = InnoDB COMMENT = '店铺';

CREATE TABLE store_member (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    store_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    role_code VARCHAR(30) NOT NULL DEFAULT 'STAFF',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_store_member (store_id, user_id),
    KEY idx_store_member_user (user_id),
    CONSTRAINT fk_member_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB COMMENT = '店铺成员（OWNER/MANAGER/STAFF）';

-- 3. 平台统一商品类目与品牌
CREATE TABLE category (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_id BIGINT UNSIGNED NULL,
    name VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_category_parent (parent_id),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (id)
) ENGINE = InnoDB COMMENT = '多级商品分类';

CREATE TABLE brand (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    logo_url VARCHAR(512) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_brand_name (name)
) ENGINE = InnoDB COMMENT = '品牌';

-- 4. SPU 定义商品，SKU 定义可交易的具体规格
CREATE TABLE product_spu (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    store_id BIGINT UNSIGNED NOT NULL,
    category_id BIGINT UNSIGNED NOT NULL,
    brand_id BIGINT UNSIGNED NULL,
    title VARCHAR(255) NOT NULL,
    subtitle VARCHAR(500) NULL,
    main_image_url VARCHAR(512) NULL,
    description LONGTEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_spu_store_status (store_id, status, updated_at),
    KEY idx_spu_category_status (category_id, status),
    CONSTRAINT fk_spu_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT fk_spu_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_spu_brand FOREIGN KEY (brand_id) REFERENCES brand (id)
) ENGINE = InnoDB COMMENT = '商品 SPU';

CREATE TABLE product_sku (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    spu_id BIGINT UNSIGNED NOT NULL,
    sku_code VARCHAR(80) NOT NULL,
    spec_json JSON NOT NULL,
    sale_price DECIMAL(12, 2) NOT NULL,
    list_price DECIMAL(12, 2) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_spu_sku_code (spu_id, sku_code),
    CONSTRAINT fk_sku_spu FOREIGN KEY (spu_id) REFERENCES product_spu (id)
) ENGINE = InnoDB COMMENT = 'SKU 可购买规格，例如颜色/容量';

CREATE TABLE sku_inventory (
    sku_id BIGINT UNSIGNED NOT NULL,
    available_qty INT NOT NULL DEFAULT 0,
    locked_qty INT NOT NULL DEFAULT 0,
    sold_qty BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (sku_id),
    CONSTRAINT fk_inventory_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id),
    CONSTRAINT ck_inventory_nonnegative CHECK (
        available_qty >= 0
        AND locked_qty >= 0
        AND sold_qty >= 0
    )
) ENGINE = InnoDB COMMENT = 'SKU 库存：可售/锁定/历史售出';

CREATE TABLE product_image (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    spu_id BIGINT UNSIGNED NOT NULL,
    sku_id BIGINT UNSIGNED NULL,
    image_url VARCHAR(512) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_image_spu (spu_id, sort_order),
    CONSTRAINT fk_image_spu FOREIGN KEY (spu_id) REFERENCES product_spu (id),
    CONSTRAINT fk_image_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id)
) ENGINE = InnoDB COMMENT = '商品图片';

-- 5. 买家收藏、购物车、收货地址
CREATE TABLE product_favorite (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    spu_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorite (user_id, spu_id),
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_favorite_spu FOREIGN KEY (spu_id) REFERENCES product_spu (id)
) ENGINE = InnoDB COMMENT = '商品收藏';

CREATE TABLE cart_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    sku_id BIGINT UNSIGNED NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    selected TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_user_sku (user_id, sku_id),
    CONSTRAINT ck_cart_qty CHECK (quantity > 0),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_cart_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id)
) ENGINE = InnoDB COMMENT = '购物车条目';

CREATE TABLE user_address (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    receiver_name VARCHAR(64) NOT NULL,
    receiver_phone VARCHAR(32) NOT NULL,
    province VARCHAR(64) NOT NULL,
    city VARCHAR(64) NOT NULL,
    district VARCHAR(64) NOT NULL,
    detail_address VARCHAR(255) NOT NULL,
    is_default TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_address_user (user_id),
    CONSTRAINT fk_address_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB COMMENT = '收货地址';

-- 6. 优惠券：模板 -> 用户实例。优惠券是否能使用必须由结算服务判定。
CREATE TABLE coupon_template (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    store_id BIGINT UNSIGNED NULL,
    title VARCHAR(128) NOT NULL,
    min_spend DECIMAL(12, 2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(12, 2) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    claimed_count INT NOT NULL DEFAULT 0,
    starts_at DATETIME(3) NOT NULL,
    ends_at DATETIME(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id),
    KEY idx_coupon_store (store_id, status),
    CONSTRAINT fk_coupon_store FOREIGN KEY (store_id) REFERENCES store (id)
) ENGINE = InnoDB COMMENT = '优惠券模板（NULL store_id 表示平台券）';

CREATE TABLE user_coupon (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    template_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNUSED',
    claimed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    used_at DATETIME(3) NULL,
    trade_order_id BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    KEY idx_user_coupon (user_id, status),
    CONSTRAINT fk_user_coupon_template FOREIGN KEY (template_id) REFERENCES coupon_template (id),
    CONSTRAINT fk_user_coupon_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB COMMENT = '用户领取的券；订单关联由业务层维护';

-- 7. 一个结算单 trade_order，按店铺拆成多个 store_order
CREATE TABLE trade_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    client_request_id VARCHAR(64) NOT NULL,
    goods_amount DECIMAL(12, 2) NOT NULL,
    discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    freight_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    payable_amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
    paid_at DATETIME(3) NULL,
    closed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_order_no (order_no),
    UNIQUE KEY uk_trade_request (user_id, client_request_id),
    KEY idx_trade_buyer_status (user_id, status, created_at),
    CONSTRAINT fk_trade_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB COMMENT = '跨店铺结算主单';

CREATE TABLE store_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sub_order_no VARCHAR(40) NOT NULL,
    trade_order_id BIGINT UNSIGNED NOT NULL,
    store_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    goods_amount DECIMAL(12, 2) NOT NULL,
    discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    freight_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    payable_amount DECIMAL(12, 2) NOT NULL,
    address_snapshot JSON NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
    shipped_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sub_order_no (sub_order_no),
    UNIQUE KEY uk_trade_store (trade_order_id, store_id),
    KEY idx_store_order (store_id, status, created_at),
    KEY idx_buyer_order (user_id, created_at),
    CONSTRAINT fk_sub_trade FOREIGN KEY (trade_order_id) REFERENCES trade_order (id),
    CONSTRAINT fk_sub_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT fk_sub_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB COMMENT = '按店铺拆分的商家子订单（一个店铺一个子单）';

CREATE TABLE order_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    store_order_id BIGINT UNSIGNED NOT NULL,
    spu_id BIGINT UNSIGNED NOT NULL,
    sku_id BIGINT UNSIGNED NOT NULL,
    product_title VARCHAR(255) NOT NULL,
    sku_snapshot JSON NOT NULL,
    image_url_snapshot VARCHAR(512) NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    quantity INT NOT NULL,
    discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    payable_amount DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_item_sub (store_order_id),
    KEY idx_order_item_sku (sku_id),
    CONSTRAINT ck_order_item_qty CHECK (quantity > 0),
    CONSTRAINT fk_item_sub FOREIGN KEY (store_order_id) REFERENCES store_order (id),
    CONSTRAINT fk_item_spu FOREIGN KEY (spu_id) REFERENCES product_spu (id),
    CONSTRAINT fk_item_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id)
) ENGINE = InnoDB COMMENT = '订单明细及下单时价格/规格快照';

-- 8. 支付尝试与第三方回调幂等记录（勿存储银行卡敏感信息）
CREATE TABLE payment_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    pay_no VARCHAR(40) NOT NULL,
    trade_order_id BIGINT UNSIGNED NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_trade_no VARCHAR(100) NULL,
    amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paid_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (pay_no),
    UNIQUE KEY uk_provider_trade_no (provider, provider_trade_no),
    KEY idx_payment_order (trade_order_id, status),
    CONSTRAINT fk_payment_trade FOREIGN KEY (trade_order_id) REFERENCES trade_order (id)
) ENGINE = InnoDB COMMENT = '支付请求记录；同一订单可多次尝试';

CREATE TABLE payment_notify_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provider VARCHAR(30) NOT NULL,
    provider_event_id VARCHAR(120) NOT NULL,
    pay_no VARCHAR(40) NULL,
    event_type VARCHAR(60) NOT NULL,
    received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_event (provider, provider_event_id)
) ENGINE = InnoDB COMMENT = '支付异步通知去重';

CREATE TABLE shipment (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    store_order_id BIGINT UNSIGNED NOT NULL,
    carrier_code VARCHAR(50) NOT NULL,
    tracking_no VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SHIPPED',
    shipped_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipment_sub (store_order_id),
    CONSTRAINT fk_shipment_sub FOREIGN KEY (store_order_id) REFERENCES store_order (id)
) ENGINE = InnoDB COMMENT = 'MVP 一笔商家子单对应一个物流单';

CREATE TABLE refund_request (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    refund_no VARCHAR(40) NOT NULL,
    store_order_id BIGINT UNSIGNED NOT NULL,
    order_item_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    quantity INT NOT NULL,
    refund_amount DECIMAL(12, 2) NOT NULL,
    reason VARCHAR(500) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    resolved_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_no (refund_no),
    KEY idx_refund_sub (store_order_id, status),
    CONSTRAINT ck_refund_qty CHECK (quantity > 0),
    CONSTRAINT fk_refund_sub FOREIGN KEY (store_order_id) REFERENCES store_order (id),
    CONSTRAINT fk_refund_item FOREIGN KEY (order_item_id) REFERENCES order_item (id),
    CONSTRAINT fk_refund_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB COMMENT = '售后退款申请，数量和金额上限由业务层验证';

CREATE TABLE product_review (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_item_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    spu_id BIGINT UNSIGNED NOT NULL,
    rating TINYINT NOT NULL,
    content TEXT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_order_item (order_item_id),
    KEY idx_review_product (spu_id, created_at),
    CONSTRAINT ck_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_review_item FOREIGN KEY (order_item_id) REFERENCES order_item (id),
    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_review_spu FOREIGN KEY (spu_id) REFERENCES product_spu (id)
) ENGINE = InnoDB COMMENT = '商品评价';

-- 9. 库存操作流水，支持核查下单锁定、支付扣减、超时释放
CREATE TABLE inventory_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sku_id BIGINT UNSIGNED NOT NULL,
    business_no VARCHAR(64) NOT NULL,
    operation VARCHAR(30) NOT NULL,
    quantity INT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_business (
        sku_id,
        business_no,
        operation
    ),
    CONSTRAINT fk_log_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id)
) ENGINE = InnoDB COMMENT = '库存操作流水与幂等约束';

-- 10. AI Agent：会话、消息、工具审计、待确认动作、知识库元信息
CREATE TABLE ai_conversation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    scene VARCHAR(20) NOT NULL,
    store_id BIGINT UNSIGNED NULL,
    title VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ai_conversation_user (user_id, scene, updated_at),
    CONSTRAINT fk_ai_conv_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_ai_conv_store FOREIGN KEY (store_id) REFERENCES store (id)
) ENGINE = InnoDB COMMENT = 'AI 会话，scene=BUYER/SELLER';

CREATE TABLE ai_message (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT UNSIGNED NOT NULL,
    message_role VARCHAR(20) NOT NULL,
    content LONGTEXT NOT NULL,
    rich_payload JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ai_message_conv (conversation_id, id),
    CONSTRAINT fk_ai_message_conv FOREIGN KEY (conversation_id) REFERENCES ai_conversation (id)
) ENGINE = InnoDB COMMENT = 'AI 对话历史与商品卡片等结构化消息';

CREATE TABLE ai_action_approval (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    approval_no VARCHAR(64) NOT NULL,
    conversation_id BIGINT UNSIGNED NOT NULL,
    actor_user_id BIGINT UNSIGNED NOT NULL,
    store_id BIGINT UNSIGNED NULL,
    action_code VARCHAR(80) NOT NULL,
    request_payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at DATETIME(3) NOT NULL,
    decided_at DATETIME(3) NULL,
    executed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_approval_no (approval_no),
    KEY idx_ai_approval_actor (actor_user_id, status),
    CONSTRAINT fk_ai_approval_conv FOREIGN KEY (conversation_id) REFERENCES ai_conversation (id),
    CONSTRAINT fk_ai_approval_actor FOREIGN KEY (actor_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_ai_approval_store FOREIGN KEY (store_id) REFERENCES store (id)
) ENGINE = InnoDB COMMENT = 'Agent 写操作的明确审批记录';

CREATE TABLE ai_tool_audit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT UNSIGNED NOT NULL,
    actor_user_id BIGINT UNSIGNED NOT NULL,
    store_id BIGINT UNSIGNED NULL,
    approval_id BIGINT UNSIGNED NULL,
    tool_name VARCHAR(100) NOT NULL,
    request_summary VARCHAR(1000) NULL,
    result_summary VARCHAR(1000) NULL,
    success TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ai_audit_conv (conversation_id, created_at),
    CONSTRAINT fk_ai_audit_conv FOREIGN KEY (conversation_id) REFERENCES ai_conversation (id),
    CONSTRAINT fk_ai_audit_actor FOREIGN KEY (actor_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_ai_audit_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT fk_ai_audit_approval FOREIGN KEY (approval_id) REFERENCES ai_action_approval (id)
) ENGINE = InnoDB COMMENT = 'Agent 调用审计，避免记录口令和敏感参数';

CREATE TABLE ai_knowledge_document (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    store_id BIGINT UNSIGNED NULL,
    document_type VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    source_uri VARCHAR(500) NULL,
    vector_document_id VARCHAR(128) NULL,
    visibility VARCHAR(30) NOT NULL DEFAULT 'PUBLIC',
    version INT NOT NULL DEFAULT 1,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ai_knowledge_store (store_id, visibility),
    CONSTRAINT fk_ai_doc_store FOREIGN KEY (store_id) REFERENCES store (id)
) ENGINE = InnoDB COMMENT = 'RAG 文档元信息；向量数据存储在 Qdrant';

-- 11. 可靠事件：订单完成后异步更新搜索索引、知识库等
CREATE TABLE outbox_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    processed_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_outbox_pending (status, next_retry_at, id)
) ENGINE = InnoDB COMMENT = '事务 Outbox，保证异步事件可重试';

-- 安全规则：所有商家后台查询必须经过 store_member 权限校验并按 store_id 限定。
-- 结算规则：不相信前端提交价格；按 SKU 实时价格、促销规则和库存重新计算。
-- 数据规则：订单商品快照不可通过修改在售商品而改变；日志与订单不物理级联删除。