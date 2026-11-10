package com.qixuan.commerce.dto;

import java.util.List;

public record ProductCreateResponse(
        Long spuId,
        List<Long> skuIds) {

}
