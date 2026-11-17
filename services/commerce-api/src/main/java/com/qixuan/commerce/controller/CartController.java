package com.qixuan.commerce.controller;

import com.qixuan.commerce.dto.AddCartRequest;
import com.qixuan.commerce.dto.CartItemUpdateRequest;
import com.qixuan.commerce.dto.CartItemView;
import com.qixuan.commerce.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart/items")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public List<CartItemView> list(@RequestAttribute("userId") Long userId) {
        return cartService.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void add(@RequestAttribute("userId") Long userId, @Valid @RequestBody AddCartRequest request) {
        cartService.add(userId, request);
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@RequestAttribute("userId") Long userId, @PathVariable Long id,
            @Valid @RequestBody CartItemUpdateRequest request) {
        cartService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        cartService.delete(userId, id);
    }

}
