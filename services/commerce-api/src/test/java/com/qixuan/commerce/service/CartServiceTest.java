package com.qixuan.commerce.service;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.qixuan.commerce.dto.AddCartRequest;
import com.qixuan.commerce.entity.CartItem;
import com.qixuan.commerce.entity.ProductSku;
import com.qixuan.commerce.entity.SkuInventory;
import com.qixuan.commerce.mapper.CartItemMapper;
import com.qixuan.commerce.mapper.ProductSkuMapper;
import com.qixuan.commerce.mapper.SkuInventoryMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CartService.add 的边界测试。
 *
 * 这是今天的真实 bug 场景：SPU 状态是 ON_SALE、SKU 状态是 ACTIVE，
 * 同一个"上架"语义在两张表用了不同的值。这个测试锁住校验规则。
 */
class CartServiceTest {

    private final CartItemMapper cartItemMapper = mock(CartItemMapper.class);
    private final ProductSkuMapper productSkuMapper = mock(ProductSkuMapper.class);
    private final SkuInventoryMapper skuInventoryMapper = mock(SkuInventoryMapper.class);
    private final CartService service = new CartService(
            cartItemMapper, productSkuMapper, skuInventoryMapper);

    private ProductSku sku(String status) {
        ProductSku s = new ProductSku();
        s.setId(1L);
        s.setStatus(status);
        s.setSalePrice(BigDecimal.TEN);
        return s;
    }

    private SkuInventory inventory(int available) {
        SkuInventory inv = new SkuInventory();
        inv.setSkuId(1L);
        inv.setAvailableQty(available);
        return inv;
    }

    private AddCartRequest request(int qty) {
        return new AddCartRequest(1L, qty);
    }

    @Test
    void add_SKU状态非ACTIVE时拒绝() {
        when(productSkuMapper.selectById(1L)).thenReturn(sku("ON_SALE"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.add(100L, request(1)));
    }

    @Test
    void add_库存为零时拒绝() {
        when(productSkuMapper.selectById(1L)).thenReturn(sku("ACTIVE"));
        when(skuInventoryMapper.selectById(1L)).thenReturn(inventory(0));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.add(100L, request(1)));
    }

    @Test
    void add_超过可售库存时拒绝() {
        when(productSkuMapper.selectById(1L)).thenReturn(sku("ACTIVE"));
        when(skuInventoryMapper.selectById(1L)).thenReturn(inventory(3));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.add(100L, request(5)));
    }
}
