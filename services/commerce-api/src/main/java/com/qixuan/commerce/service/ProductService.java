package com.qixuan.commerce.service;

import com.qixuan.commerce.common.BizStatus;

import com.qixuan.commerce.dto.ProductCardView;
import com.qixuan.commerce.dto.ProductListItem;
import com.qixuan.commerce.dto.PageResult;
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
        private final ProductCacheService productCacheService;

        public ProductService(
                        ProductSpuMapper productSpuMapper,
                        StoreMapper storeMapper,
                        SkuInventoryMapper skuInventoryMapper,
                        ProductSkuMapper productSkuMapper,
                        CategoryMapper categoryMapper,
                        JsonMapper jsonMapper,
                        ProductCacheService productCacheService) {

                this.productSpuMapper = productSpuMapper;
                this.storeMapper = storeMapper;
                this.productSkuMapper = productSkuMapper;
                this.categoryMapper = categoryMapper;
                this.jsonMapper = jsonMapper;
                this.skuInventoryMapper = skuInventoryMapper;
                this.productCacheService = productCacheService;
        }

        private Store requireOwnedStore(Long userId, Long storeId) {
                Store store = storeMapper.selectById(storeId);
                if (store == null || !userId.equals(store.getOwnerUserId())) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "店铺不存在");
                }
                return store;
        }

        public List<ProductListItem> listProducts(Long userId, Long storeId) {
                // 1.校验店铺ID
                if (storeId == null || storeId <= 0) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "店铺ID必须为正整数");
                }
                // 2.查询店铺是否存在
                requireOwnedStore(userId, storeId);
                // 3.查询该店铺的商品
                return productSpuMapper.findListByStoreId(storeId);
        }

        @Transactional
        public ProductCreateResponse createProduct(
                        Long userId,
                        ProductCreateRequest request) {
                // 1.检查店铺
                Store store = requireOwnedStore(userId, request.storeId());

                if (!BizStatus.STORE_ACTIVE.equals(store.getStatus())) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "店铺已停用");
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

                // 草稿不进列表，但保守起见还是失效一次：万一改了上下架规则不会漏
                productCacheService.evictAll();

                return new ProductCreateResponse(spuId, skuIds);
        }

        @Transactional
        public void updateProductStatus(
                        Long userId,
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
                Store store = requireOwnedStore(userId, request.storeId());
                if (!BizStatus.STORE_ACTIVE.equals(store.getStatus())) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "店铺已停用");
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

                // 上下架直接改变列表可见性，必须立刻让缓存失效
                productCacheService.evictAll();
        }

        /** 单页默认条数 */
        private static final int DEFAULT_PAGE_SIZE = 20;

        /** 单页最大条数 */
        private static final int MAX_PAGE_SIZE = 100;

        public List<ProductCardView> listOnSale(Long categoryId, String keyword) {
                String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

                // 搜索是长尾请求：关键词组合无限，缓存命中率低还会撑爆 key 空间，直接查库
                if (kw != null) {
                        return productSpuMapper.findOnSale(categoryId, kw);
                }

                return productCacheService.getOrLoad(categoryId,
                                () -> productSpuMapper.findOnSale(categoryId, null));
        }

        /**
         * 分页查在售商品。
         *
         * 这里刻意不走 ProductCacheService：
         *   缓存的是"整份列表"，分页后每页内容不同，要给每个 (分类, 关键词, 页码) 都建 key，
         *   key 空间膨胀，而上下架时又要清理一整片 key，得不偿失。
         *   分页查询靠 (status, category_id) 索引 + LIMIT 就够快。
         */
        public PageResult<ProductCardView> pageOnSale(Long categoryId, String keyword,
                        int pageNo, int pageSize) {

                int safePageNo = pageNo < 1 ? 1 : pageNo;
                int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
                if (safePageSize > MAX_PAGE_SIZE) {
                        safePageSize = MAX_PAGE_SIZE;
                }

                String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

                long total = productSpuMapper.countOnSale(categoryId, kw);
                List<ProductCardView> records = productSpuMapper.findOnSalePage(
                                categoryId, kw, (safePageNo - 1) * safePageSize, safePageSize);

                long pages = (total + safePageSize - 1) / safePageSize;
                return new PageResult<>(records, total, safePageNo, safePageSize, pages);
        }

        public List<ProductSpu> listProducts(Long categoryId, String keyword) {
                LambdaQueryWrapper<ProductSpu> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(ProductSpu::getStatus, BizStatus.SPU_ON_SALE)
                                .eq(categoryId != null, ProductSpu::getCategoryId, categoryId)
                                .like(keyword != null && !keyword.isBlank(), ProductSpu::getTitle, keyword)
                                .orderByDesc(ProductSpu::getId);

                return productSpuMapper.selectList(wrapper);
        }

        public ProductDetailResponse getProductDetail(Long storeId, Long userId, Long id) {
                requireOwnedStore(userId, storeId);
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
