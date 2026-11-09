package com.qixuan.commerce.controller;

import com.qixuan.commerce.dto.ProductListItem;
import com.qixuan.commerce.service.ProductService;

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
            @RequestParam Long storeId) {
        return productService.listProducts(storeId);
    }
}
