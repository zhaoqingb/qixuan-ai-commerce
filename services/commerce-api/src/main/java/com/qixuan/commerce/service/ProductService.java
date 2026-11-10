package com.qixuan.commerce.service;

import com.qixuan.commerce.dto.ProductListItem;
import com.qixuan.commerce.dto.ProductCreateRequest;
import com.qixuan.commerce.dto.ProductCreateResponse;
import com.qixuan.commerce.dto.ProductStatusRequest;
import com.qixuan.commerce.entity.Category;
import com.qixuan.commerce.entity.ProductSpu;
import com.qixuan.commerce.entity.ProductSku;
import com.qixuan.commerce.entity.SkuInventory;
import com.qixuan.commerce.entity.Store;

import com.qixuan.commerce.mapper.CategoryMapper;
import com.qixuan.commerce.mapper.ProductSpuMapper;
import com.qixuan.commerce.mapper.ProductSkuMapper;
import com.qixuan.commerce.mapper.StoreMapper;
import com.qixuan.commerce.mapper.SkuInventoryMapper;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

@Service
public class ProductService {

    private final ProductSpuMapper productSpuMapper;
    private final ProductSkuMapper productSkuMapper;
    private final StoreMapper storeMapper;
    private final SkuInventoryMapper skuInventoryMapper;
    private final CategoryMapper categoryMapper;
    private final JsonMapper jsonMapper;

    public ProductService(
            ProductSpuMapper productSpuMapper,
            StoreMapper storeMapper,
            SkuInventoryMapper skuInventoryMapper,
            ProductSkuMapper productSkuMapper,
            CategoryMapper categoryMapper,
            JsonMapper jsonMapper) {

        this.productSpuMapper = productSpuMapper;
        this.storeMapper = storeMapper;
        this.productSkuMapper = productSkuMapper;
        this.categoryMapper = categoryMapper;
        this.jsonMapper = jsonMapper;
        this.skuInventoryMapper = skuInventoryMapper;
    }

    public List<ProductListItem> listProducts(Long storeId) {
        // 1.校验店铺ID
        if (storeId == null || storeId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "店铺ID必须为正整数");
        }
        // 2.查询店铺是否存在
        if (storeMapper.selectById(storeId) == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "店铺不存在");
        }
        // 3.查询该店铺的商品
        return productSpuMapper.findListByStoreId(storeId);
    }

    @Transactional
    public ProductCreateResponse createProduct(
            ProductCreateRequest request) {
        // 1.检查店铺
        Store store = storeMapper.selectById(
                request.storeId());

        if (store == null || !Integer.valueOf(1).equals(store.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "店铺不存在或已停用");
        }
        // 2.检查商品分类
        Category category = categoryMapper.selectById(
                request.categoryId());
        if (category == null || !Integer.valueOf(1).equals(category.getEnabled())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "商品分类不存在或已禁用");
        }

        // 3.创建SPU
        ProductSpu spu = new ProductSpu();

        spu.setStoreId(request.storeId());
        spu.setCategoryId(request.categoryId());
        spu.setName(request.name().trim());
        spu.setDescription(request.description());

        spu.setStatus(0);
        productSpuMapper.insert(spu);

        // MySQL自增ID回填至实体
        Long spuId = spu.getId();
        List<Long> skuIds = new ArrayList<>();

        // 4.创建多个SKU和库存
        for (ProductCreateRequest.SkuItem item : request.skus()) {
            ProductSku sku = new ProductSku();

            sku.setSpuId(spuId);
            sku.setSkuCode(item.skuCode().trim());

            // 将规格Map转换为JSON字符串
            sku.setSpecJson(
                    jsonMapper.writeValueAsString(item.specs()));

            sku.setPrice(item.price());
            sku.setStatus(1);

            productSkuMapper.insert(sku);

            Long skuId = sku.getId();
            skuIds.add(skuId);

            // 5.初始化SKU库存
            SkuInventory inventory = new SkuInventory();

            inventory.setSkuId(skuId);
            inventory.setAvailableQty(item.stock());
            inventory.setLockedQty(0);
            inventory.setVersion(0);

            skuInventoryMapper.insert(inventory);
        }

        // 全部成功后返回新增ID
        return new ProductCreateResponse(spuId, skuIds);
    }

    @Transactional
    public void updateProductStatus(
            Long productId,
            ProductStatusRequest request) {
        // 1.检查商品ID
        if (productId == null || productId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "商品ID必须为正整数");
        }

        // 2.查询商品是否存在、是否属于指定店铺
        ProductSpu spu = productSpuMapper.selectById(productId);
        if (spu == null || !request.storeId().equals(spu.getStoreId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "商品不存在");

        }

        // 3.检查店铺状态
        Store store = storeMapper.selectById(request.storeId());
        if (store == null || !Integer.valueOf(1).equals(store.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "店铺不存在或已停用");
        }

        // 4.上架时额外检查分类与SKU
        if (request.status() == 1) {
            Category category = categoryMapper.selectById(spu.getCategoryId());

            if (category == null || !Integer.valueOf(1).equals(category.getEnabled())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "商品分类不存在或已禁用");
            }
            Long skuCount = productSkuMapper.selectCount(
                    new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getSpuId, productId).eq(ProductSku::getStatus,
                            1));
            if (skuCount == 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "商品没有可用SKU,无法上架");
            }
        }

        // 5.只更新商品状态与更新时间
        LambdaUpdateWrapper<ProductSpu> wrapper = new LambdaUpdateWrapper<ProductSpu>().eq(ProductSpu::getId, productId)
                .eq(ProductSpu::getStoreId, request.storeId()).set(ProductSpu::getStatus, request.status())
                .set(ProductSpu::getUpdatedAt, LocalDateTime.now());

        int rows = productSpuMapper.update(null, wrapper);

        if (rows != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "商品状态更新失败，请重试");
        }
    }

}
