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
    public List<CartItemView> list(@RequestParam Long userId) {
        return cartService.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void add(@Valid @RequestBody AddCartRequest request) {
        cartService.add(request);
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable Long id,
            @Valid @RequestBody CartItemUpdateRequest request) {
        cartService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @RequestParam Long userId) {
        cartService.delete(id, userId);
    }

}
