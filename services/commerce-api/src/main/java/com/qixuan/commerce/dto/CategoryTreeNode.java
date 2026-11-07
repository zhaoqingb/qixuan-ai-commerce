package com.qixuan.commerce.dto;

import java.util.List;

public record CategoryTreeNode(
        Long id,
        Long parentId,
        String name,
        Integer sortOrder,
        List<CategoryTreeNode> children) {
}
