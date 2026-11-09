<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

import { getProducts, type ProductListItem } from '@/api/product'

// 商品列表数据
const products = ref<ProductListItem[]>([])

// 加载状态
const loading = ref(false)

const storeId = 2

async function loadProducts() {
  loading.value = true
  try {
    products.value = await getProducts(storeId)
    console.log('商品列表：', products.value)
  } catch (error) {
    console.error('加载商品失败：', error)
    ElMessage.error('商品列表加载失败')
  } finally {
    loading.value = false
  }
}

// 页面加载后自动查询
onMounted(() => {
  void loadProducts()
})
</script>

<template>
  <div class="product-page">
    <!-- 页面标题 -->
    <div>
      <h2>商品管理</h2>
      <p>管理店铺中的商品及库存信息</p>
    </div>

    <el-button type="primary" :loading="loading" @click="loadProducts">刷新商品</el-button>
  </div>

  <!-- 商品列表 -->
  < shadow="never">
  <el-table v-loading="loading" :data="products" border style="width: 100%" empty-text="暂无商品">
    <el-table-column prop="id" label="商品ID" width="100"></el-table-column>
    <el-table-column prop="name" label="商品名称" width="220"></el-table-column>
    <el-table-column prop="categoryName" label="商品分类" width="140"></el-table-column>
    <el-table-column prop="storeName" label="所属店铺" width="170"></el-table-column>
    <el-table-column label="最低价格" width="130">
      <template #default="{ row }">
        <span v-if="row.minPrice !== null"> ${{ Number(row.minPrice).toFixed(2) }} </span>
        <span v-else>暂无报价</span>
      </template>
    </el-table-column>

    <el-table-column prop="availableStock" label="可售库存" width="120"></el-table-column>

    <el-table-column label="商品状态" width="120">
      <template #default="{ row }">
        <el-tag :type="row.status === 1 ? 'success' : 'info'">
          {{ row.status === 1 ? '已上架' : '草稿' }}
        </el-tag>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.product-page {
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
  font-size: 14px;
}
</style>
