package com.qixuan.commerce.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("store")
public class Store extends BaseEntity {

    private String name;
    private Integer status;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getStatus()  {
        return status;
    }
    
    public void setStatus(Integer status){
        this.status=status;
    }

}
