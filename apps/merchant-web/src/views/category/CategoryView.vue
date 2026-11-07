<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

import { getCategoryTree } from '../../api/category'
import type { CategoryTreeNode } from '../../types/category'

// 分类数据
const categories = ref<CategoryTreeNode[]>([])

// 加载状态
const loading = ref(false)

// 查询分类树
async function loadCategories() {
  loading.value = true
  try {
    categories.value = await getCategoryTree()
  } catch (error) {
    console.error('获取分类失败：', error)
    ElMessage.error('分类加载失败，请检查后端服务')
  } finally {
    loading.value = false
  }
}

// 页面加载完成后自动查询
onMounted(() => {
  void loadCategories()
})
</script>

<template>
  <div class="category-page">
    <!-- 页面标题 -->
    <div>
      <h2>商品分类管理</h2>
      <p>查看平台商品分类与层级结构</p>
    </div>
    <el-button type="primary" :loading="loading" @click="loadCategories">刷新分类</el-button>
  </div>
  <!-- 分类表格 -->
  <el-card shadow="never">
    <el-table
      v-loading="loading"
      :data="categories"
      row-key="id"
      border
      default-expand-all
      style="width: 100%"
    >
      <el-table-column prop="name" label="分类名称" min-width="240"></el-table-column>
      <el-table-column prop="id" label="分类ID" width="130"></el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="110"></el-table-column>
      <el-table-column label="下级分类数" width="140"
        ><template #default="{ row }">
          {{ row.children?.length ?? 0 }}
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.category-page {
  padding: 24px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 8px;
  font-size: 22px;
}

.page-header p {
  margin: 0;
  color: #909399;
}
</style>
