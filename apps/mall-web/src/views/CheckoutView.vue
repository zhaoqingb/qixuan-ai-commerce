<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { fetchAddresses, fetchPreview, submitOrder } from '@/api/checkout'
import { CURRENT_USER_ID } from '@/config'
import { useCartStore } from '@/stores/cart'
import type { Address, CheckoutPreview } from '@/types/mall'
import { formatMoney, formatSpec } from '@/utils/spec'

const router = useRouter()
const cartStore = useCartStore()

const preview = ref<CheckoutPreview | null>(null)
const addresses = ref<Address[]>([])
const addressId = ref<number | null>(null)
const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref('')

/**
 * 幂等键：同一串字符重复提交，后端只会生成一张订单。
 * 生成一次就固定住，用户连点提交也不会出两张单。
 */
const clientRequestId = ref(createRequestId())

function createRequestId(): string {
  return `web-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

const totalQuantity = computed(() => preview.value?.totalQuantity ?? 0)

const hasShortage = computed(
  () => preview.value?.storeGroups.some((group) => group.items.some((item) => !item.stockEnough)) ?? false,
)

function onImageError(event: Event) {
  const img = event.target as HTMLImageElement
  img.src = '/images/placeholder.svg'
}

function fullAddress(address: Address): string {
  return `${address.province}${address.city}${address.district}${address.detailAddress}`
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [previewData, addressList] = await Promise.all([
      fetchPreview(CURRENT_USER_ID),
      fetchAddresses(CURRENT_USER_ID),
    ])
    preview.value = previewData
    addresses.value = addressList
    addressId.value =
      addressList.find((item) => item.isDefault === 1)?.id ?? addressList[0]?.id ?? null
  } catch (error) {
    preview.value = null
    errorMessage.value = error instanceof Error ? error.message : '结算信息加载失败'
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!preview.value) return
  if (!addressId.value) {
    ElMessage.warning('请选择收货地址')
    return
  }
  if (hasShortage.value) {
    ElMessage.warning('有商品库存不足，请返回购物车调整数量')
    return
  }

  submitting.value = true
  try {
    const result = await submitOrder({
      userId: CURRENT_USER_ID,
      addressId: addressId.value,
      clientRequestId: clientRequestId.value,
    })
    await cartStore.refresh()
    router.replace({
      path: '/order/success',
      query: {
        orderNo: result.orderNo,
        amount: String(result.payableAmount),
        count: String(result.itemCount),
        stores: String(result.storeOrderCount),
      },
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交订单失败')
    // 失败后换一把新钥匙，避免下一次重试被上一次失败请求"粘住"
    clientRequestId.value = createRequestId()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="checkout qx-container qx-page">
    <header class="checkout__head">
      <h1>确认订单</h1>
      <span v-if="preview" class="checkout__meta">
        {{ preview.storeGroups.length }} 个店铺 · 共 {{ totalQuantity }} 件商品
      </span>
    </header>

    <el-skeleton v-if="loading" :rows="10" animated />

    <el-empty v-else-if="errorMessage" :description="errorMessage">
      <el-button type="primary" @click="router.push('/cart')">返回购物车</el-button>
    </el-empty>

    <div v-else-if="preview" class="layout">
      <div class="main">
        <section class="block qx-card">
          <h2 class="block__title">收货地址</h2>

          <div v-if="addresses.length === 0" class="empty-address">
            还没有收货地址，请先在数据库或后续的地址管理页面添加。
          </div>

          <div v-else class="address-list">
            <button
              v-for="address in addresses"
              :key="address.id"
              type="button"
              class="address"
              :class="{ 'is-active': addressId === address.id }"
              @click="addressId = address.id"
            >
              <div class="address__top">
                <span class="address__name">{{ address.receiverName }}</span>
                <span class="address__phone">{{ address.receiverPhone }}</span>
                <span v-if="address.isDefault === 1" class="address__default">默认</span>
              </div>
              <p class="address__detail">{{ fullAddress(address) }}</p>
            </button>
          </div>
        </section>

        <section v-for="group in preview.storeGroups" :key="group.storeId" class="block qx-card">
          <header class="block__store">
            <span class="block__store-name">{{ group.storeName }}</span>
            <span class="block__store-meta">
              {{ group.items.length }} 种商品 · 运费 ¥{{ formatMoney(group.freightAmount) }}
            </span>
          </header>

          <div v-for="item in group.items" :key="item.cartItemId" class="goods">
            <div class="goods__media">
              <img
                :src="item.imageUrl || '/images/placeholder.svg'"
                :alt="item.productTitle"
                @error="onImageError"
              />
            </div>
            <div class="goods__info">
              <p class="goods__title qx-ellipsis-2">{{ item.productTitle }}</p>
              <p class="goods__spec">{{ formatSpec(item.specJson) }}</p>
              <p v-if="!item.stockEnough" class="goods__warn">库存不足（仅剩 {{ item.availableQty }} 件）</p>
            </div>
            <div class="goods__price">
              <span class="goods__unit">¥{{ formatMoney(item.salePrice) }} × {{ item.quantity }}</span>
              <span class="qx-money">¥{{ formatMoney(item.subtotal) }}</span>
            </div>
          </div>

          <footer class="block__foot">
            店铺合计
            <span class="qx-money">¥{{ formatMoney(group.payableAmount) }}</span>
          </footer>
        </section>
      </div>

      <aside class="aside">
        <div class="summary qx-card">
          <h2 class="block__title">金额明细</h2>

          <div class="summary__row">
            <span>商品金额</span>
            <span>¥{{ formatMoney(preview.goodsAmount) }}</span>
          </div>
          <div class="summary__row">
            <span>运费</span>
            <span>{{ preview.freightAmount > 0 ? `¥${formatMoney(preview.freightAmount)}` : '包邮' }}</span>
          </div>
          <div class="summary__row summary__row--total">
            <span>应付金额</span>
            <span class="qx-money summary__amount">
              <span class="qx-money__symbol">¥</span>{{ formatMoney(preview.payableAmount) }}
            </span>
          </div>

          <p class="summary__hint">每个店铺单独发货，运费按店铺分别计算，满 99 元包邮。</p>

          <el-button
            type="primary"
            size="large"
            class="summary__btn"
            :loading="submitting"
            :disabled="!addressId || hasShortage"
            @click="submit"
          >
            {{ submitting ? '提交中…' : '提交订单' }}
          </el-button>

          <button type="button" class="summary__back" @click="router.push('/cart')">返回购物车</button>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.checkout__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}

.checkout__head h1 {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.checkout__meta {
  font-size: 13px;
  color: var(--qx-ink-3);
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 20px;
  align-items: start;
}

.main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.block {
  padding: 20px 22px;
}

.block__title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 14px;
}

/* ---------- 地址 ---------- */
.address-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}

.address {
  position: relative;
  text-align: left;
  padding: 14px 16px;
  border: 1.5px solid var(--qx-line);
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  transition: all 0.18s ease;
}

.address:hover {
  border-color: var(--qx-brand);
}

.address.is-active {
  border-color: var(--qx-brand);
  background: var(--qx-brand-soft);
  box-shadow: 0 6px 16px rgba(255, 107, 53, 0.12);
}

.address__top {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.address__name {
  font-size: 14px;
  font-weight: 600;
}

.address__phone {
  font-size: 13px;
  color: var(--qx-ink-2);
}

.address__default {
  margin-left: auto;
  padding: 1px 7px;
  border-radius: 6px;
  background: var(--qx-brand);
  color: #fff;
  font-size: 11px;
}

.address__detail {
  font-size: 13px;
  line-height: 1.6;
  color: var(--qx-ink-2);
}

.empty-address {
  padding: 18px;
  border-radius: 12px;
  background: #fafbfd;
  color: var(--qx-ink-3);
  font-size: 13px;
  text-align: center;
}

/* ---------- 店铺 ---------- */
.block__store {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 14px;
  margin-bottom: 6px;
  border-bottom: 1px dashed var(--qx-line);
}

.block__store-name {
  font-size: 14px;
  font-weight: 600;
}

.block__store-name::before {
  content: '';
  display: inline-block;
  width: 4px;
  height: 14px;
  margin-right: 8px;
  border-radius: 2px;
  background: var(--qx-brand);
  vertical-align: -2px;
}

.block__store-meta {
  margin-left: auto;
  font-size: 12px;
  color: var(--qx-ink-3);
}

.goods {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 0;
  border-bottom: 1px solid #f2f4f8;
}

.goods:last-of-type {
  border-bottom: none;
}

.goods__media {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  border-radius: 10px;
  overflow: hidden;
  background: linear-gradient(160deg, #f8f9fb, #eef1f6);
}

.goods__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.goods__info {
  flex: 1;
  min-width: 0;
}

.goods__title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
}

.goods__spec {
  margin-top: 4px;
  font-size: 12px;
  color: var(--qx-ink-3);
}

.goods__warn {
  margin-top: 4px;
  font-size: 12px;
  color: #f56c6c;
}

.goods__price {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}

.goods__unit {
  font-size: 12px;
  color: var(--qx-ink-3);
}

.goods__price .qx-money {
  font-size: 16px;
}

.block__foot {
  padding-top: 14px;
  margin-top: 4px;
  border-top: 1px dashed var(--qx-line);
  text-align: right;
  font-size: 13px;
  color: var(--qx-ink-2);
}

.block__foot .qx-money {
  margin-left: 6px;
  font-size: 18px;
}

/* ---------- 汇总 ---------- */
.aside {
  position: sticky;
  top: 88px;
}

.summary {
  padding: 20px 22px 18px;
}

.summary__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 0;
  font-size: 14px;
  color: var(--qx-ink-2);
}

.summary__row--total {
  margin-top: 8px;
  padding-top: 14px;
  border-top: 1px dashed var(--qx-line);
  color: var(--qx-ink);
  font-weight: 600;
}

.summary__amount {
  font-size: 28px;
  line-height: 1;
}

.summary__hint {
  margin: 10px 0 16px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--qx-ink-3);
}

.summary__btn {
  width: 100%;
  height: 46px;
  border: none;
  border-radius: 23px;
  background: linear-gradient(135deg, #ff8a4c, #ff5a1f);
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 10px 22px rgba(255, 90, 31, 0.26);
}

.summary__back {
  display: block;
  width: 100%;
  margin-top: 10px;
  border: none;
  background: none;
  color: var(--qx-ink-3);
  font-size: 13px;
  cursor: pointer;
}

.summary__back:hover {
  color: var(--qx-brand);
}

@media (max-width: 980px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .aside {
    position: static;
  }
}
</style>
