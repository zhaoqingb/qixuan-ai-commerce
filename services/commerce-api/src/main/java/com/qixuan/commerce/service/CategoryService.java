package com.qixuan.commerce.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qixuan.commerce.dto.CategoryCreateRequest;
import com.qixuan.commerce.dto.CategoryTreeNode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qixuan.commerce.entity.Category;
import com.qixuan.commerce.entity.ProductSpu;
import com.qixuan.commerce.mapper.CategoryMapper;
import com.qixuan.commerce.mapper.ProductSpuMapper;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final ProductSpuMapper productSpuMapper;

    public CategoryService(CategoryMapper categoryMapper, ProductSpuMapper productSpuMapper) {
        this.categoryMapper = categoryMapper;
        this.productSpuMapper = productSpuMapper;
    }

    public List<Category> listRootCategories() {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(Category::getParentId).eq(Category::getEnabled, 1).orderByAsc(Category::getSortOrder);
        return categoryMapper.selectList(wrapper);
    }

    public List<Category> listCategories() {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Category::getEnabled, 1).orderByAsc(Category::getSortOrder);
        return categoryMapper.selectList(wrapper);
    }

    public Category createCategory(CategoryCreateRequest request) {

        String name = request.getName().trim();
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Category::getName, name);
        if (request.getParentId() == null) {
            wrapper.isNull(Category::getParentId);
        } else {
            Category parent = categoryMapper.selectById(request.getParentId());
            if (parent == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "父分类不存在");
            }
            wrapper.eq(Category::getParentId, request.getParentId());
        }
        Long count = categoryMapper.selectCount(wrapper);
        if (count > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "同级分类已存在");
        }

        // 构建数据库实体
        Category category = new Category();

        category.setName(name);
        category.setParentId(request.getParentId());
        category.setSortOrder(
                request.getSortOrder() == null ? 0 : request.getSortOrder());
        category.setEnabled(1);

        // 插入 MySQL
        categoryMapper.insert(category);

        return category;
    }

    public Category updateCategory(
            Long id, CategoryCreateRequest request) {

        // 1. 检查分类是否存在
        Category category = categoryMapper.selectById(id);

        if (category == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "分类不存在");
        }

        // 2. 检查父子关系，避免循环
        Long parentId = request.getParentId();
        Set<Long> visited = new HashSet<>();

        while (parentId != null) {

            if (parentId.equals(id) ||
                    !visited.add(parentId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "不能将自身或子孙分类设置为父分类");
            }

            Category parent = categoryMapper.selectById(parentId);

            if (parent == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "父分类不存在");
            }

            parentId = parent.getParentId();
        }

        String newName = request.getName().trim();
        LambdaQueryWrapper<Category> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.ne(Category::getId, id).eq(Category::getName, newName);
        if (request.getParentId() == null) {
            checkWrapper.isNull(Category::getParentId);
        } else {
            checkWrapper.eq(Category::getParentId, request.getParentId());
        }
        Long count = categoryMapper.selectCount(checkWrapper);
        if (count > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "同级分类已存在");
        }
        // 3. 使用 LambdaUpdateWrapper 显式更新字段
        LambdaUpdateWrapper<Category> wrapper = new LambdaUpdateWrapper<>();

        wrapper.eq(Category::getId, id)
                .set(Category::getName, request.getName().trim())
                .set(Category::getParentId, request.getParentId())
                .set(
                        Category::getSortOrder,
                        request.getSortOrder() == null
                                ? 0
                                : request.getSortOrder());

        // 4. 更新数据库
        int rows = categoryMapper.update(null, wrapper);

        if (rows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "分类不存在");
        }

        // 5. 返回更新后的数据
        return categoryMapper.selectById(id);
    }

    public void deleteCategory(Long id) {

        Category category = categoryMapper.selectById(id);

        if (category == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "分类不存在");
        }

        Long childCount = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getParentId, id));

        if (childCount > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "该分类存在子分类，不能删除");
        }

        // TODO 第 7 关：在这里补「该分类下有商品」的检查
        // 要点① 商品在 product_spu 表，用 category_id 关联
        // 要点② CategoryService 目前只注入了 CategoryMapper，需要额外注入 ProductSpuMapper
        // 要点③ 有商品时不能删（状态冲突 → 409）
        Long productCount = productSpuMapper.selectCount(
                new LambdaQueryWrapper<ProductSpu>()
                        .eq(ProductSpu::getCategoryId, id));

        if (productCount > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "该分类下有商品，不能删除");
        }

        categoryMapper.deleteById(id);
    }

    public List<CategoryTreeNode> listCategoryTree() {

        List<Category> categories = listCategories();

        Map<Long, CategoryTreeNode> nodeMap = new HashMap<>();

        for (Category category : categories) {

            CategoryTreeNode node = new CategoryTreeNode(
                    category.getId(),
                    category.getParentId(),
                    category.getName(),
                    category.getSortOrder(),
                    new ArrayList<>());

            nodeMap.put(category.getId(), node);
        }

        List<CategoryTreeNode> roots = new ArrayList<>();

        for (Category category : categories) {

            CategoryTreeNode node = nodeMap.get(category.getId());

            CategoryTreeNode parent = nodeMap.get(category.getParentId());

            if (parent == null) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }
        return roots;
    }

}
