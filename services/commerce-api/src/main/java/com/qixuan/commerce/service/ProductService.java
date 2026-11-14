package com.qixuan.commerce.service;

import com.qixuan.commerce.common.BizStatus;

import com.qixuan.commerce.dto.ProductCardView;
import com.qixuan.commerce.dto.ProductListItem;
import com.qixuan.commerce.dto.ProductCreateRequest;
import com.qixuan.commerce.dto.ProductCreateResponse;
import com.qixuan.commerce.dto.ProductDetailResponse;
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
import java.util.Map;
import java.util.stream.Collectors;

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

                if (store == null || !BizStatus.STORE_ACTIVE.equals(store.getStatus())) {
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

                ProductSpu spu = new ProductSpu();
                spu.setStoreId(request.storeId());
                spu.setCategoryId(request.categoryId());
                spu.setTitle(request.name().trim());
                spu.setDescription(request.description());
                spu.setStatus(BizStatus.SPU_DRAFT);
                productSpuMapper.insert(spu);
                Long spuId = spu.getId();
                List<Long> skuIds = new ArrayList<>();
                for (ProductCreateRequest.SkuItem item : request.skus()) {
                        ProductSku sku = new ProductSku();
                        sku.setSpuId(spuId);
                        sku.setSkuCode(item.skuCode().trim());
                        sku.setSpecJson(jsonMapper.writeValueAsString(item.specs()));
                        sku.setSalePrice(item.price());
                        sku.setStatus(BizStatus.SKU_ACTIVE);
                        productSkuMapper.insert(sku);

                        Long skuId = sku.getId();
                        skuIds.add(skuId);

                        // 5.创建SKU库存
                        SkuInventory skuInventory = new SkuInventory();
                        skuInventory.setSkuId(skuId);
                        skuInventory.setAvailableQty(item.stock());
                        skuInventory.setLockedQty(0);
                        skuInventory.setVersion(0);
                        skuInventoryMapper.insert(skuInventory);
                }
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
                if (store == null || !BizStatus.STORE_ACTIVE.equals(store.getStatus())) {
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
                        List<ProductSku> skus = productSkuMapper.selectList(
                                        new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getSpuId, productId)
                                                        .eq(ProductSku::getStatus, BizStatus.SKU_ACTIVE));
                        if (skus.isEmpty()) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "商品没有可用SKU,无法上架");
                        }
                        // 提取这些 SKU 的 id
                        List<Long> skuIds = new ArrayList<>();
                        for (ProductSku sku : skus) {
                                skuIds.add(sku.getId());
                        }

                        // 汇总可售库存
                        long totalAvailable = 0;
                        if (!skuIds.isEmpty()) {
                                List<SkuInventory> inventories = skuInventoryMapper
                                                .selectList(new LambdaQueryWrapper<SkuInventory>()
                                                                .in(SkuInventory::getSkuId, skuIds));
                                for (SkuInventory inventory : inventories) {
                                        Integer availableQty = inventory.getAvailableQty();
                                        if (availableQty != null) {
                                                totalAvailable += availableQty;
                                        }
                                }
                        }
                        if (totalAvailable <= 0) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "商品没有可用库存,无法上架");
                        }
                }
                // 5.只更新商品状态与更新时间
                String targetStatus = request.status() == 1
                                ? BizStatus.SPU_ON_SALE
                                : BizStatus.SPU_OFF_SALE;

                LambdaUpdateWrapper<ProductSpu> wrapper = new LambdaUpdateWrapper<ProductSpu>()
                                .eq(ProductSpu::getId, productId)
                                .eq(ProductSpu::getStoreId, request.storeId())
                                .set(ProductSpu::getStatus, targetStatus)
                                .set(ProductSpu::getUpdatedAt, LocalDateTime.now());

                int rows = productSpuMapper.update(null, wrapper);

                if (rows != 1) {
                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "商品状态更新失败，请重试");
                }
        }

        public List<ProductCardView> listOnSale(Long categoryId, String keyword) {
                String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
                return productSpuMapper.findOnSale(categoryId, kw);
        }

        public List<ProductSpu> listProducts(Long categoryId, String keyword) {
                LambdaQueryWrapper<ProductSpu> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(ProductSpu::getStatus, BizStatus.SPU_ON_SALE)
                                .eq(categoryId != null, ProductSpu::getCategoryId, categoryId)
                                .like(keyword != null && !keyword.isBlank(), ProductSpu::getTitle, keyword)
                                .orderByDesc(ProductSpu::getId);

                return productSpuMapper.selectList(wrapper);
        }

        public ProductDetailResponse getProductDetail(Long storeId, Long id) {
                ProductSpu spu = productSpuMapper.selectById(id);
                if (spu == null || !storeId.equals(spu.getStoreId())) {
                        throw new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "商品不存在");
                }
                return buildDetail(spu);
        }

        public ProductDetailResponse getProductDetailForBuyer(Long id) {
                ProductSpu spu = productSpuMapper.selectById(id);
                if (spu == null || !BizStatus.SPU_ON_SALE.equals(spu.getStatus())) {
                        throw new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "商品不存在或已下架");
                }
                return buildDetail(spu);
        }

        private ProductDetailResponse buildDetail(ProductSpu spu) {
                List<ProductSku> skus = productSkuMapper.selectList(
                                new LambdaQueryWrapper<ProductSku>()
                                                .eq(ProductSku::getSpuId, spu.getId())
                                                .eq(ProductSku::getStatus, "ACTIVE")
                                                .orderByAsc(ProductSku::getId));

                List<Long> skuIds = skus.stream().map(ProductSku::getId).toList();

                Map<Long, SkuInventory> inventoryMap = skuIds.isEmpty()
                                ? Map.of()
                                : skuInventoryMapper.selectList(
                                                new LambdaQueryWrapper<SkuInventory>()
                                                                .in(SkuInventory::getSkuId, skuIds))
                                                .stream()
                                                .collect(Collectors.toMap(
                                                                SkuInventory::getSkuId, v -> v));

                List<ProductDetailResponse.SkuView> views = skus.stream()
                                .map(sku -> {
                                        SkuInventory inv = inventoryMap.get(sku.getId());
                                        return new ProductDetailResponse.SkuView(
                                                        sku.getId(),
                                                        sku.getSkuCode(),
                                                        sku.getSpecJson(),
                                                        sku.getSalePrice(),
                                                        sku.getListPrice(),
                                                        inv == null ? 0 : inv.getAvailableQty());
                                })
                                .toList();

                Store store = storeMapper.selectById(spu.getStoreId());

                return new ProductDetailResponse(
                                spu.getId(),
                                spu.getTitle(),
                                spu.getSubtitle(),
                                spu.getMainImageUrl(),
                                spu.getDescription(),
                                spu.getStoreId(),
                                store == null ? null : store.getStoreName(),
                                spu.getCategoryId(),
                                views);
        }

}
