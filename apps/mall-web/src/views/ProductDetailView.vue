<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { addToCart } from '@/api/cart'
import { fetchProductDetail } from '@/api/product'
import { CURRENT_USER_ID } from '@/config'
import { useCartStore } from '@/stores/cart'
import type { ProductDetail, SkuView } from '@/types/mall'
import { formatMoney, formatSpec } from '@/utils/spec'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()

const detail = ref<ProductDetail | null>(null)
const loading = ref(true)
const errorMessage = ref('')
const activeSkuId = ref<number | null>(null)
const quantity = ref(1)
const adding = ref(false)
const imageFailed = ref(false)

const activeSku = computed<SkuView | null>(
  () => detail.value?.skus.find((item) => item.skuId === activeSkuId.value) ?? null,
)

/** 可售库存：只在下单扣减，加购不动它 */
const availableQty = computed(() => activeSku.value?.availableQty ?? 0)

const soldOut = computed(() => availableQty.value <= 0)

/** 当前规格已经躺在购物车里的件数（加购是累加，不占用库存） */
const inCartQty = computed(() => {
  const skuId = activeSku.value?.skuId
  if (!skuId) return 0
  return cartStore.items
    .filter((item) => item.skuId === skuId)
    .reduce((sum, item) => sum + item.quantity, 0)
})

/** 本次还能加多少 = 可售库存 - 购物车已占，和后端 add() 的累加校验保持一致 */
const maxQuantity = computed(() => Math.max(0, availableQty.value - inCartQty.value))

/** 有库存但这一个规格已经被自己加满 */
const limitReached = computed(() => !soldOut.value && maxQuantity.value <= 0)

const addDisabled = computed(() => soldOut.value || limitReached.value)

const addText = computed(() => {
  if (soldOut.value) return '暂时无货'
  if (limitReached.value) return '已达可购上限'
  return '加入购物车'
})

const stockHint = computed(() => {
  if (soldOut.value) return '该规格暂时无货'
  if (inCartQty.value === 0) return `可售 ${availableQty.value} 件，单次最多加 ${maxQuantity.value} 件`
  return `可售 ${availableQty.value} 件，购物车已有 ${inCartQty.value} 件，还可加 ${maxQuantity.value} 件`
})

const cover = computed(() => {
  if (imageFailed.value || !detail.value?.mainImageUrl) return '/images/placeholder.svg'
  return detail.value.mainImageUrl
})

const discount = computed(() => {
  const list = activeSku.value?.listPrice
  const sale = activeSku.value?.salePrice
  if (!list || !sale || list <= sale) return ''
  return `${((sale / list) * 10).toFixed(1)} 折`
})

/** silent = 只刷新库存/价格，不重置用户选中的规格和数量 */
async function load(silent = false) {
  if (!silent) {
    loading.value = true
    errorMessage.value = ''
  }
  try {
    const data = await fetchProductDetail(Number(route.params.id))
    const skuKept = data.skus.some((item) => item.skuId === activeSkuId.value)
    detail.value = data
    if (silent && skuKept) return
    activeSkuId.value = data.skus[0]?.skuId ?? null
    quantity.value = 1
    imageFailed.value = false
  } catch (error) {
    if (silent) return
    detail.value = null
    errorMessage.value = error instanceof Error ? error.message : '商品加载失败'
  } finally {
    if (!silent) loading.value = false
  }
}

function selectSku(sku: SkuView) {
  if (sku.availableQty <= 0) return
  activeSkuId.value = sku.skuId
  quantity.value = 1
}

async function handleAdd() {
  const sku = activeSku.value
  if (!sku || addDisabled.value) return
  adding.value = true
  try {
    await addToCart({ userId: CURRENT_USER_ID, skuId: sku.skuId, quantity: quantity.value })
    await cartStore.refresh()
    ElMessage.success('已加入购物车')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加入购物车失败')
  } finally {
    adding.value = false
  }
}

/**
 * 详情页里的库存是「打开页面那一刻」的快照。
 * 别人下单、自己换设备下单都会让它过期，切回标签页时静默重拉一次，
 * 免得用户挑了十分钟才在结算页被告知没货。
 */
function handleVisibility() {
  if (document.visibilityState === 'visible') load(true)
}

onMounted(() => document.addEventListener('visibilitychange', handleVisibility))
onUnmounted(() => document.removeEventListener('visibilitychange', handleVisibility))

// 购物车里的数量变了（加购/改量/删除），本次可加的上限跟着收缩，数量框不能停在超限值
watch(maxQuantity, (value) => {
  if (value > 0 && quantity.value > value) quantity.value = value
})

watch(
  () => route.params.id,
  () => load(),
  { immediate: true },
)
</script>

<template>
  <div class="detail qx-container qx-page">
    <div class="crumb">
      <RouterLink to="/" class="crumb__back">
        <span aria-hidden="true">←</span> 返回商品列表
      </RouterLink>
      <template v-if="detail">
        <span class="crumb__sep">/</span>
        <span class="crumb__store">{{ detail.storeName }}</span>
        <span class="crumb__sep">/</span>
        <span class="crumb__current">{{ detail.title }}</span>
      </template>
    </div>

    <el-skeleton v-if="loading" :rows="8" animated />

    <el-empty v-else-if="errorMessage" :description="errorMessage">
      <el-button type="primary" @click="router.push('/')">回到首页</el-button>
    </el-empty>

    <template v-else-if="detail">
      <section class="panel qx-card">
        <div class="panel__media">
          <img :src="cover" :alt="detail.title" @error="imageFailed = true" />
        </div>

        <div class="panel__info">
          <h1 class="title">{{ detail.title }}</h1>
          <p v-if="detail.subtitle" class="subtitle">{{ detail.subtitle }}</p>

          <div class="price">
            <span class="qx-money price__sale">
              <span class="qx-money__symbol">¥</span>{{ formatMoney(activeSku?.salePrice) }}
            </span>
            <span v-if="activeSku?.listPrice" class="price__list">
              ¥{{ formatMoney(activeSku.listPrice) }}
            </span>
            <span v-if="discount" class="price__discount">{{ discount }}</span>
          </div>

          <div class="meta">
            <span class="meta__store">{{ detail.storeName }}</span>
            <span class="meta__dot">·</span>
            <span>库存 {{ availableQty }} 件</span>
            <span class="meta__dot">·</span>
            <span>SKU {{ activeSku?.skuCode }}</span>
          </div>

          <div class="field">
            <label class="field__label">规格</label>
            <div class="skus">
              <button
                v-for="sku in detail.skus"
                :key="sku.skuId"
                type="button"
                class="sku"
                :class="{ 'is-active': sku.skuId === activeSkuId, 'is-disabled': sku.availableQty <= 0 }"
                @click="selectSku(sku)"
              >
                {{ formatSpec(sku.specJson) }}
                <span v-if="sku.availableQty <= 0" class="sku__out">无货</span>
              </button>
            </div>
          </div>

          <div class="field">
            <label class="field__label">数量</label>
            <el-input-number
              v-model="quantity"
              :min="1"
              :max="Math.max(1, maxQuantity)"
              :disabled="addDisabled"
              controls-position="right"
            />
            <span class="field__hint">{{ stockHint }}</span>
          </div>

          <ul class="promise">
            <li>正品保障</li>
            <li>7 天无理由退换</li>
            <li>48 小时内发货</li>
          </ul>

          <div class="actions">
            <el-button
              type="primary"
              size="large"
              class="btn-cart"
              :loading="adding"
              :disabled="addDisabled"
              @click="handleAdd"
            >
              {{ addText }}
            </el-button>
            <el-button size="large" @click="router.push('/cart')">去购物车</el-button>
          </div>
        </div>
      </section>

      <section class="desc qx-card">
        <h2 class="qx-section-title">商品详情</h2>
        <p class="desc__text">{{ detail.description || '商家暂未填写商品详情。' }}</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  font-size: 13px;
  color: var(--qx-ink-3);
  overflow: hidden;
}

.crumb__back {
  flex-shrink: 0;
  color: var(--qx-ink-2);
  transition: color 0.18s ease;
}

.crumb__back:hover {
  color: var(--qx-brand);
}

.crumb__sep {
  color: #cdd4e0;
}

.crumb__store,
.crumb__current {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.crumb__current {
  min-width: 0;
}

.panel {
  display: grid;
  grid-template-columns: 480px minmax(0, 1fr);
  gap: 40px;
  padding: 32px;
}

.panel__media {
  aspect-ratio: 1 / 1;
  border-radius: 16px;
  overflow: hidden;
  background: linear-gradient(160deg, #f8f9fb, #eef1f6);
}

.panel__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.panel__info {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.title {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: 0.3px;
}

.subtitle {
  margin-top: -6px;
  font-size: 14px;
  color: var(--qx-ink-3);
}

.price {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 14px 18px;
  border-radius: 12px;
  background: linear-gradient(100deg, #fff6f1, #fff1e9);
}

.price__sale {
  font-size: 34px;
  line-height: 1;
}

.price__list {
  font-size: 14px;
  color: var(--qx-ink-3);
  text-decoration: line-through;
}

.price__discount {
  padding: 2px 9px;
  border-radius: 8px;
  background: var(--qx-brand);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 13px;
  color: var(--qx-ink-2);
}

.meta__store {
  padding: 2px 10px;
  border-radius: 8px;
  background: #f2f5fa;
  color: var(--qx-ink);
  font-weight: 600;
}

.meta__dot {
  color: #cdd4e0;
}

.field {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.field__label {
  width: 42px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--qx-ink-3);
}

.field__hint {
  font-size: 12px;
  color: var(--qx-ink-3);
}

.skus {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.sku {
  padding: 8px 16px;
  border: 1.5px solid var(--qx-line);
  border-radius: 10px;
  background: #fff;
  color: var(--qx-ink-2);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.18s ease;
}

.sku:hover:not(.is-disabled) {
  border-color: var(--qx-brand);
  color: var(--qx-brand-dark);
}

.sku.is-active {
  border-color: var(--qx-brand);
  background: var(--qx-brand-soft);
  color: var(--qx-brand-dark);
  font-weight: 600;
}

.sku.is-disabled {
  color: #c3cbd9;
  background: #fafbfd;
  cursor: not-allowed;
}

.sku__out {
  margin-left: 6px;
  font-size: 11px;
  color: #c3cbd9;
}

.promise {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin: 4px 0 0;
  padding: 14px 16px;
  list-style: none;
  border-radius: 12px;
  background: #fafbfd;
}

.promise li {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--qx-ink-2);
}

.promise li::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--qx-success);
}

.actions {
  display: flex;
  gap: 12px;
  margin-top: auto;
  padding-top: 8px;
}

.btn-cart {
  min-width: 168px;
  height: 46px;
  border: none;
  border-radius: 23px;
  background: linear-gradient(135deg, #ff8a4c, #ff5a1f);
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 10px 22px rgba(255, 90, 31, 0.26);
}

.desc {
  margin-top: 20px;
  padding: 28px 32px 32px;
}

.desc__text {
  margin-top: 14px;
  font-size: 14px;
  line-height: 2;
  color: var(--qx-ink-2);
  white-space: pre-wrap;
}

@media (max-width: 960px) {
  .panel {
    grid-template-columns: minmax(0, 1fr);
    gap: 24px;
    padding: 20px;
  }

  .title {
    font-size: 20px;
  }

  .price__sale {
    font-size: 28px;
  }
}
</style>
