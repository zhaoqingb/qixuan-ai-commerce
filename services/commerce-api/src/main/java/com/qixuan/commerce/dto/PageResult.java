package com.qixuan.commerce.dto;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页结果。
 *
 * 不直接返回 MyBatis-Plus 的 Page：它会把 optimizeCountSql、searchCount、countId、
 * maxLimit 这些内部字段一起序列化给前端。前端只需要"数据 + 总数 + 页码"。
 */
public record PageResult<T>(
        List<T> records,
        long total,
        long page,
        long size,
        long pages) {

    public static <T> PageResult<T> of(Page<T> source) {
        return new PageResult<>(
                source.getRecords(),
                source.getTotal(),
                source.getCurrent(),
                source.getSize(),
                source.getPages());
    }
}
