package com.qixuan.commerce.service;

import com.qixuan.commerce.dto.ProductListItem;
import com.qixuan.commerce.mapper.ProductSpuMapper;
import com.qixuan.commerce.mapper.StoreMapper;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProductService {

    private final ProductSpuMapper productSpuMapper;
    private final StoreMapper storeMapper;

    public ProductService(
            ProductSpuMapper productSpuMapper,
            StoreMapper storeMapper) {

        this.productSpuMapper = productSpuMapper;
        this.storeMapper = storeMapper;
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

}
