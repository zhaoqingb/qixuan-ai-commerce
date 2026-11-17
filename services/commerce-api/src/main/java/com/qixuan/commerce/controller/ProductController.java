package com.qixuan.commerce.controller;

import com.qixuan.commerce.dto.ProductListItem;
import com.qixuan.commerce.dto.ProductCreateRequest;
import com.qixuan.commerce.dto.ProductCreateResponse;
import com.qixuan.commerce.dto.ProductStatusRequest;
import com.qixuan.commerce.dto.ProductDetailResponse;

import com.qixuan.commerce.service.ProductService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/merchant/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductListItem> list(
            @RequestAttribute Long userId,
            @RequestParam Long storeId) {
        return productService.listProducts(userId, storeId);
    }

    // 新增商品
    @PostMapping
    public ResponseEntity<ProductCreateResponse> create(
            @RequestAttribute Long userId,
            @Valid @RequestBody ProductCreateRequest request) {
        ProductCreateResponse response = productService.createProduct(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 修改商品上架/下架状态
    @PatchMapping("/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStatus(
            @RequestAttribute Long userId,
            @PathVariable("id") Long id,
            @Valid @RequestBody ProductStatusRequest request) {
        productService.updateProductStatus(userId, id, request);
    }

    @GetMapping("/{id}")
    public ProductDetailResponse detail(
            @RequestAttribute Long userId,
            @RequestParam Long storeId,
            @PathVariable("id") Long id) {
        return productService.getProductDetail(userId, storeId, id);
    }

}
