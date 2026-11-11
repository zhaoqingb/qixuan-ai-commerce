package com.qixuan.commerce.controller;

import com.qixuan.commerce.dto.ProductCardView;
import com.qixuan.commerce.dto.ProductDetailResponse;
import com.qixuan.commerce.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 买家端（C 端）商品浏览接口，只暴露已上架商品。
 */
@RestController
@RequestMapping("/api/products")
public class MallProductController {

    private final ProductService productService;

    public MallProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductCardView> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        return productService.listOnSale(categoryId, keyword);
    }

    @GetMapping("/{id}")
    public ProductDetailResponse detail(@PathVariable Long id) {
        return productService.getProductDetailForBuyer(id);
    }
}
