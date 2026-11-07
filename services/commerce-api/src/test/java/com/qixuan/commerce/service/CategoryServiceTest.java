
package com.qixuan.commerce.service;

import com.qixuan.commerce.dto.CategoryCreateRequest;
import com.qixuan.commerce.entity.Category;
import com.qixuan.commerce.mapper.CategoryMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryService categoryService;

    // 测试1：修改不存在的分类
    @Test
    void updateMissingCategoryShouldReturn404() {

        when(categoryMapper.selectById(999L))
                .thenReturn(null);

        CategoryCreateRequest request = new CategoryCreateRequest();

        request.setName("测试分类");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> categoryService.updateCategory(
                        999L, request));

        assertEquals(
                404,
                exception.getStatusCode().value());
    }

    // 测试2：不允许把自己设为父分类
    @Test
    void cannotSetSelfAsParent() {

        Category category = new Category();
        category.setId(7L);

        when(categoryMapper.selectById(7L))
                .thenReturn(category);

        CategoryCreateRequest request = new CategoryCreateRequest();

        request.setName("手机通讯");
        request.setParentId(7L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> categoryService.updateCategory(
                        7L, request));

        assertEquals(
                400,
                exception.getStatusCode().value());
    }
}
