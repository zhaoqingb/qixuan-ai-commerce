<script setup lang="ts">
import { ElMessageBox } from 'element-plus'
import axios from 'axios'
import { updateProductStatus } from '../../api/product'
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

import { getProducts, type ProductListItem } from '../../api/product'

// 商品列表数据
const products = ref<ProductListItem[]>([])

// 加载状态
const loading = ref(false)

const storeId = ref<number>(1)

async function loadProducts(id: number = storeId.value) {
  loading.value = true
  try {
    storeId.value = id
    products.value = await getProducts(storeId.value)
    console.log('商品列表：', products.value)
  } catch (error) {
    products.value = []
    console.error('加载商品失败：', error)
    ElMessage.error('商品列表加载失败')
  } finally {
    loading.value = false
  }
}

const changingId = ref<number | null>(null)

async function handleStatusChange(row: ProductListItem) {
  if (changingId.value !== null) return

  const targetStatus: 0 | 1 = row.status === 1 ? 0 : 1

  const action = targetStatus === 1 ? '上架' : '下架'

  try {
    await ElMessageBox.confirm(`确定要${action}商品「${row.name}」吗？`, '商品状态确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    // 用户取消操作
    return
  }

  changingId.value = row.id

  try {
    // 复用页面中已经配置正确的 storeId
    await updateProductStatus(row.id, storeId.value, targetStatus)

    ElMessage.success(`商品${action}成功`)

    // 更新页面数据
    await loadProducts()
  } catch (error) {
    console.error('修改商品状态失败：', error)

    const message = axios.isAxiosError(error)
      ? (error.response?.data as { message?: string } | undefined)?.message
      : undefined

    ElMessage.error(message || `商品${action}失败`)
  } finally {
    changingId.value = null
  }
}

// 页面加载后自动查询
onMounted(() => {
  void loadProducts()
})
</script>

<template>
  <div class="page-header">
    <div>
      <h2>商品管理</h2>
      <p>管理店铺中的商品及库存信息</p>
    </div>

    <div>
      <el-button @click="loadProducts"> 刷新商品 </el-button>

      <el-button type="primary" @click="$router.push('/products/new')"> + 新增商品 </el-button>
    </div>
  </div>

  <!-- 商品列表 -->
  <el-table
    shadow="never"
    v-loading="loading"
    :data="products"
    border
    style="width: 100%"
    empty-text="暂无商品"
  >
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

    <el-table-column label="操作" width="160" fixed="right">
      <template #default="{ row }">
        <el-button
          :type="row.status === 1 ? 'warning' : 'success'"
          :loading="changingId === row.id"
          @click="handleStatusChange(row)"
        >
          {{ row.status === 1 ? '下架' : '上架' }}
        </el-button>
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
