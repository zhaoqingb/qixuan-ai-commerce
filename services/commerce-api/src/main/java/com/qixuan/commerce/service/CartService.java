package com.qixuan.commerce.service;

import com.qixuan.commerce.dto.AddCartRequest;
import com.qixuan.commerce.dto.CartItemUpdateRequest;
import com.qixuan.commerce.dto.CartItemView;
import com.qixuan.commerce.entity.CartItem;
import com.qixuan.commerce.entity.ProductSku;
import com.qixuan.commerce.entity.SkuInventory;
import com.qixuan.commerce.mapper.CartItemMapper;
import com.qixuan.commerce.mapper.ProductSkuMapper;
import com.qixuan.commerce.mapper.SkuInventoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CartService {

    private final CartItemMapper cartItemMapper;
    private final ProductSkuMapper productSkuMapper;
    private final SkuInventoryMapper skuInventoryMapper;

    public CartService(CartItemMapper cartItemMapper, ProductSkuMapper productSkuMapper,
            SkuInventoryMapper skuInventoryMapper) {
        this.cartItemMapper = cartItemMapper;
        this.productSkuMapper = productSkuMapper;
        this.skuInventoryMapper = skuInventoryMapper;
    }

    public List<CartItemView> list(Long userId) {
        return cartItemMapper.findByUserId(userId);
    }

    @Transactional
    public void add(AddCartRequest request) {
        ProductSku sku = productSkuMapper.selectById(request.skuId());
        if (sku == null || !"ACTIVE".equals(sku.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "商品不存在或已下架");
        }

        SkuInventory inv = skuInventoryMapper.selectById(request.skuId());
        int available = inv == null ? 0 : inv.getAvailableQty();
        if (available < request.quantity()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "库存不足");
        }

        CartItem existing = cartItemMapper.selectOne(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, request.userId())
                        .eq(CartItem::getSkuId, request.skuId()));

        if (existing == null) {
            CartItem item = new CartItem();
            item.setUserId(request.userId());
            item.setSkuId(request.skuId());
            item.setQuantity(request.quantity());
            item.setSelected(1);
            cartItemMapper.insert(item);

        } else {
            int newQty = existing.getQuantity() + request.quantity();
            if (newQty > available) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "库存不足");
            }
            existing.setQuantity(newQty);
            cartItemMapper.updateById(existing);
        }
    }

    @Transactional
    public void update(Long id, CartItemUpdateRequest request) {
        if (request.quantity() == null && request.selected() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "quantity 与 selected 至少提供一个");
        }

        CartItem item = cartItemMapper.selectOne(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getId, id)
                        .eq(CartItem::getUserId, request.userId()));
        if (item == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "购物车条目不存在");
        }

        if (request.quantity() != null) {
            SkuInventory inv = skuInventoryMapper.selectById(item.getSkuId());
            int available = inv == null ? 0 : inv.getAvailableQty();
            if (request.quantity() > available) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "库存不足");
            }
            item.setQuantity(request.quantity());
        }

        if (request.selected() != null) {
            item.setSelected(request.selected());
        }

        cartItemMapper.updateById(item);
    }

    public void delete(Long id, Long userId) {
        int rows = cartItemMapper.delete(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getId, id)
                        .eq(CartItem::getUserId, userId));
        if (rows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "购物车条目不存在");
        }
    }

}
