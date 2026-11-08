package com.qixuan.commerce.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("category")
public class Category extends BaseEntity {

    private Long parentId;
    private String name;
    private Integer sortOrder;
    private Integer enabled;

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

}
