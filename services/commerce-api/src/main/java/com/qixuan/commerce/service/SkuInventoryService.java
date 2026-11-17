package com.qixuan.commerce.service;

import com.qixuan.commerce.entity.ProductSku;
import com.qixuan.commerce.entity.ProductSpu;
import com.qixuan.commerce.entity.SkuInventory;
import com.qixuan.commerce.entity.Store;
import com.qixuan.commerce.mapper.InventoryLogMapper;
import com.qixuan.commerce.mapper.ProductSkuMapper;
import com.qixuan.commerce.mapper.ProductSpuMapper;
import com.qixuan.commerce.mapper.SkuInventoryMapper;
import com.qixuan.commerce.mapper.StoreMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 库存业务域。
 *
 * 之所以独立成 Service（而不是塞进 ProductService）：库存已经被
 * 下单、支付、关单、退款、补货五条链路使用，它不属于"商品管理"。
 */
@Service
public class SkuInventoryService {

    /** 单次补货上限：防误操作（多打两个 0）和 int 溢出 */
    private static final int MAX_RESTOCK_QTY = 100_000;

    private static final String OPERATION_RESTOCK = "RESTOCK";

    private final SkuInventoryMapper skuInventoryMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductSpuMapper productSpuMapper;
    private final StoreMapper storeMapper;
    private final InventoryLogMapper inventoryLogMapper;

    public SkuInventoryService(SkuInventoryMapper skuInventoryMapper, ProductSkuMapper productSkuMapper,
            ProductSpuMapper productSpuMapper, StoreMapper storeMapper,
            InventoryLogMapper inventoryLogMapper) {
        this.skuInventoryMapper = skuInventoryMapper;
        this.productSkuMapper = productSkuMapper;
        this.productSpuMapper = productSpuMapper;
        this.storeMapper = storeMapper;
        this.inventoryLogMapper = inventoryLogMapper;
    }

    /**
     * 商家补货。
     *
     * @param businessNo 业务单号（审批单号），写进库存流水，事后能追到"这 20 件是谁补的"
     * @return 补货后的可售库存
     */
    @Transactional
    public int restock(Long userId, Long skuId, int qty, String businessNo) {

        // ① 参数校验放在最前面：补货是唯一不受 `>= qty` 保护的库存变更
        if (qty <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "补货数量必须大于 0");
        }
        if (qty > MAX_RESTOCK_QTY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单次补货不能超过 " + MAX_RESTOCK_QTY + " 件");
        }

        // ② 归属校验：SKU → SPU → 店铺 → 店主是不是我
        Store store = requireOwnedStoreOfSku(userId, skuId);

        // ③ 原子加（不需要 >= 条件）
        int rows = skuInventoryMapper.restock(skuId, qty);
        if (rows != 1) {
            // 补货的 0 行只可能是"这行不存在"，和下单的 0 行（库存不足）含义不同
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "该 SKU 没有库存记录");
        }

        // ④ 同一个事务里写流水，否则会出现"库存加了、流水没记"
        inventoryLogMapper.insert(skuId, businessNo, OPERATION_RESTOCK, qty);

        SkuInventory inventory = skuInventoryMapper.selectById(skuId);
        return inventory == null ? 0 : inventory.getAvailableQty();
    }

    /** 校验 SKU 所属店铺归当前用户所有，返回店铺；不在自己名下统一 404（不泄露存在性） */
    public Store requireOwnedStoreOfSku(Long userId, Long skuId) {
        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }

        ProductSpu spu = productSpuMapper.selectById(sku.getSpuId());
        if (spu == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }

        Store store = storeMapper.selectById(spu.getStoreId());
        if (store == null || !userId.equals(store.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }

        return store;
    }
}
