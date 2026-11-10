<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

import http from '../../api/http'
import { createProduct, type ProductCreateRequest } from '../../api/product'

interface CategoryNode {
  id: number
  name: string
  children?: CategoryNode[]
}

interface SkuFormItem {
  key: number
  skuCode: string
  color: string
  price: number
  stock: number
}

const router = useRouter()
const formRef = ref<FormInstance>()

const loading = ref(false)
const categoryLoading = ref(false)
const categories = ref<CategoryNode[]>([])

// 改成你实际测试成功的店铺 ID
const storeId = 1

let nextSkuKey = 0

function newSku(): SkuFormItem {
  return {
    key: ++nextSkuKey,
    skuCode: '',
    color: '',
    price: 0,
    stock: 0,
  }
}

const form = reactive({
  name: '',
  categoryId: null as number | null,
  description: '',
  skus: [newSku()],
})

const rules: FormRules = {
  name: [
    {
      required: true,
      message: '请输入商品名称',
      trigger: 'blur',
    },
    {
      max: 200,
      message: '商品名称不能超过200个字符',
      trigger: 'blur',
    },
  ],
  categoryId: [
    {
      required: true,
      type: 'number',
      message: '请选择商品分类',
      trigger: 'change',
    },
  ],
}

// 加载数据库中的真实分类
async function loadCategories() {
  categoryLoading.value = true

  try {
    const response = await http.get<CategoryNode[]>('/categories/tree')

    categories.value = response.data
  } catch (error) {
    console.error(error)
    ElMessage.error('商品分类加载失败')
  } finally {
    categoryLoading.value = false
  }
}

function addSku() {
  form.skus.push(newSku())
}

function removeSku(key: number) {
  if (form.skus.length === 1) {
    ElMessage.warning('至少保留一个SKU')
    return
  }

  form.skus = form.skus.filter((item) => item.key !== key)
}

async function submitProduct() {
  if (loading.value) return

  const valid = await formRef.value?.validate().catch(() => false)

  if (!valid) return

  if (!Number.isInteger(storeId) || storeId <= 0) {
    ElMessage.error('请配置正确的店铺ID')
    return
  }

  const skuCodes = new Set<string>()

  for (const [index, sku] of form.skus.entries()) {
    const row = index + 1
    const code = sku.skuCode.trim()

    if (!code || !sku.color.trim()) {
      ElMessage.warning(`请补全第${row}个SKU的编码和颜色`)
      return
    }

    if (skuCodes.has(code)) {
      ElMessage.warning('SKU编码不能重复')
      return
    }

    skuCodes.add(code)

    if (
      !Number.isFinite(sku.price) ||
      sku.price < 0 ||
      !Number.isInteger(sku.stock) ||
      sku.stock < 0
    ) {
      ElMessage.warning(`第${row}个SKU的价格或库存不合法`)
      return
    }
  }

  const request: ProductCreateRequest = {
    storeId,
    categoryId: form.categoryId!,
    name: form.name.trim(),
    description: form.description.trim(),
    skus: form.skus.map((sku) => ({
      skuCode: sku.skuCode.trim(),
      specs: {
        颜色: sku.color.trim(),
      },
      price: sku.price,
      stock: sku.stock,
    })),
  }

  loading.value = true

  try {
    const result = await createProduct(request)

    ElMessage.success(`商品创建成功！SPU ID：${result.spuId}`)

    await router.push('/products')
  } catch (error) {
    console.error('创建商品失败：', error)

    const serverMessage = axios.isAxiosError(error)
      ? (error.response?.data as { message?: string } | undefined)?.message
      : undefined

    ElMessage.error(serverMessage || '商品创建失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadCategories()
})
</script>

<template>
  <div class="create-page">
    <div class="page-header">
      <h2>新增商品</h2>
      <p>填写商品基本信息及SKU，保存后默认进入草稿状态</p>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-card shadow="never">
        <template #header>
          <strong>商品基本信息</strong>
        </template>

        <el-form-item label="商品名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="请输入商品名称"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="商品分类" prop="categoryId">
          <el-tree-select
            v-model="form.categoryId"
            :data="categories"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            check-strictly
            filterable
            :loading="categoryLoading"
            placeholder="请选择商品分类"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="商品描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            placeholder="请输入商品描述"
          />
        </el-form-item>
      </el-card>

      <el-card shadow="never" class="sku-card">
        <template #header>
          <div class="sku-header">
            <strong>SKU规格与库存</strong>

            <el-button type="primary" plain @click="addSku"> + 添加SKU </el-button>
          </div>
        </template>

        <div v-for="(sku, index) in form.skus" :key="sku.key" class="sku-row">
          <div class="sku-title">
            <strong>SKU {{ index + 1 }}</strong>

            <el-button type="danger" link @click="removeSku(sku.key)"> 删除 </el-button>
          </div>

          <el-form-item label="SKU编码">
            <el-input v-model="sku.skuCode" maxlength="64" placeholder="例如 QX-SPEAKER-RED" />
          </el-form-item>

          <el-form-item label="颜色">
            <el-input v-model="sku.color" placeholder="例如 曜石黑" />
          </el-form-item>

          <el-form-item label="商品价格">
            <el-input-number
              v-model="sku.price"
              :min="0"
              :max="9999999999.99"
              :precision="2"
              :step="1"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="初始库存">
            <el-input-number
              v-model="sku.stock"
              :min="0"
              :precision="0"
              :step="1"
              style="width: 100%"
            />
          </el-form-item>
        </div>
      </el-card>

      <div class="actions">
        <el-button @click="router.push('/products')"> 取消 </el-button>

        <el-button type="primary" :loading="loading" @click="submitProduct"> 保存为草稿 </el-button>
      </div>
    </el-form>
  </div>
</template>

<style scoped>
.create-page {
  padding: 24px;
  max-width: 1050px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 8px;
}

.page-header p {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.sku-card {
  margin-top: 20px;
}

.sku-header,
.sku-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sku-row {
  padding: 20px 12px;
  margin-bottom: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.sku-title {
  margin-bottom: 20px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
}
</style>
