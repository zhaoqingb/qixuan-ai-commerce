<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { fetchOrders } from '@/api/order'
import { CURRENT_USER_ID } from '@/config'
import type { OrderItemRow } from '@/types/mall'
import { formatMoney, formatSpec } from '@/utils/spec'

const router = useRouter()

const loading = ref(true)
const rows = ref<OrderItemRow[]>([])
const activeStatus = ref('ALL')

/** 后端存的是英文状态码，页面显示中文；新增状态只改这张表 */
const STATUS_TEXT: Record<string, string> = {
  PENDING_PAYMENT: '待付款',
  PAID: '待发货',
  SHIPPED: '待收货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

type TagTone = 'primary' | 'success' | 'info' | 'warning' | 'danger'

const STATUS_TONE: Record<string, TagTone> = {
  PENDING_PAYMENT: 'warning',
  PAID: 'primary',
  SHIPPED: 'primary',
  COMPLETED: 'success',
  CANCELLED: 'info',
}

const tabs = [
  { key: 'ALL', label: '全部' },
  { key: 'PENDING_PAYMENT', label: '待付款' },
  { key: 'PAID', label: '待发货' },
  { key: 'SHIPPED', label: '待收货' },
  { key: 'COMPLETED', label: '已完成' },
]

interface OrderStoreGroup {
  storeId: number
  storeName: string
  items: OrderItemRow[]
  subtotal: number
}

interface OrderCard {
  tradeOrderId: number
  orderNo: string
  status: string
  createdAt: string
  goodsAmount: number
  freightAmount: number
  payableAmount: number
  itemCount: number
  stores: OrderStoreGroup[]
}

/**
 * 后端返回的是商品行，这里按 tradeOrderId → storeId 两级分组。
 * Map 保持插入顺序，所以订单和店铺的先后顺序跟 SQL 的 ORDER BY 一致。
 */
const orders = computed<OrderCard[]>(() => {
  const map = new Map<number, OrderCard>()

  for (const row of rows.value) {
    let order = map.get(row.tradeOrderId)
    if (!order) {
      order = {
        tradeOrderId: row.tradeOrderId,
        orderNo: row.orderNo,
        status: row.status,
        createdAt: row.createdAt,
        goodsAmount: Number(row.goodsAmount),
        freightAmount: Number(row.freightAmount),
        payableAmount: Number(row.payableAmount),
        itemCount: 0,
        stores: [],
      }
      map.set(row.tradeOrderId, order)
    }

    let store = order.stores.find((group) => group.storeId === row.storeId)
    if (!store) {
      store = { storeId: row.storeId, storeName: row.storeName, items: [], subtotal: 0 }
      order.stores.push(store)
    }

    store.items.push(row)
    store.subtotal += Number(row.itemPayableAmount)
    order.itemCount += row.quantity
  }

  return [...map.values()]
})

const filteredOrders = computed(() =>
  activeStatus.value === 'ALL'
    ? orders.value
    : orders.value.filter((order) => order.status === activeStatus.value),
)

const totalItems = computed(() => orders.value.reduce((sum, order) => sum + order.itemCount, 0))

function countOf(key: string): number {
  return key === 'ALL'
    ? orders.value.length
    : orders.value.filter((order) => order.status === key).length
}

function statusText(status: string): string {
  return STATUS_TEXT[status] ?? status
}

function statusTone(status: string): TagTone {
  return STATUS_TONE[status] ?? 'info'
}

function onImageError(event: Event) {
  const img = event.target as HTMLImageElement
  img.src = '/images/placeholder.svg'
}

function pay() {
  ElMessage.info('演示环境未接入支付，订单保持「待付款」状态')
}

onMounted(async () => {
  try {
    rows.value = await fetchOrders(CURRENT_USER_ID)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '订单加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="orders qx-container qx-page">
    <header class="head">
      <div>
        <h1 class="head__title">我的订单</h1>
        <p class="head__sub">
          共 {{ orders.length }} 笔订单 · {{ totalItems }} 件商品
        </p>
      </div>
      <RouterLink to="/" class="head__link">
        继续购物
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path
            d="M9 6l6 6-6 6"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </RouterLink>
    </header>

    <nav class="tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        class="tab"
        :class="{ 'is-active': activeStatus === tab.key }"
        @click="activeStatus = tab.key"
      >
        {{ tab.label }}
        <span v-if="countOf(tab.key) > 0" class="tab__count">{{ countOf(tab.key) }}</span>
      </button>
    </nav>

    <el-skeleton v-if="loading" :rows="8" animated />

    <el-empty
      v-else-if="filteredOrders.length === 0"
      :description="activeStatus === 'ALL' ? '还没有订单，去挑几件喜欢的' : '该状态下没有订单'"
    >
      <el-button type="primary" @click="router.push('/')">去逛逛</el-button>
    </el-empty>

    <div v-else class="list">
      <article v-for="order in filteredOrders" :key="order.tradeOrderId" class="order qx-card">
        <header class="order__head">
          <div class="order__meta">
            <RouterLink :to="`/order/${order.orderNo}`" class="order__no">
              {{ order.orderNo }}
            </RouterLink>
            <span class="order__time">{{ order.createdAt }}</span>
          </div>
          <el-tag :type="statusTone(order.status)" effect="light" round size="small">
            {{ statusText(order.status) }}
          </el-tag>
        </header>

        <div v-for="store in order.stores" :key="store.storeId" class="store">
          <div class="store__head">
            <span class="store__icon">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M4 9l1.6-4h12.8L20 9M4 9h16v10.5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V9z"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.7"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <path d="M9.5 13.5h5" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
              </svg>
            </span>
            <span class="store__name">{{ store.storeName }}</span>
            <span class="store__sub">商品小计 ¥{{ formatMoney(store.subtotal) }}</span>
          </div>

          <div v-for="item in store.items" :key="item.orderItemId" class="item">
            <RouterLink :to="`/product/${item.spuId}`" class="item__media">
              <img
                :src="item.imageUrl || '/images/placeholder.svg'"
                :alt="item.productTitle"
                @error="onImageError"
              />
            </RouterLink>

            <div class="item__info">
              <RouterLink :to="`/product/${item.spuId}`" class="item__title qx-ellipsis-2">
                {{ item.productTitle }}
              </RouterLink>
              <p class="item__spec">{{ formatSpec(item.specJson) }}</p>
            </div>

            <div class="item__price">
              <span>¥{{ formatMoney(item.unitPrice) }}</span>
              <small>× {{ item.quantity }}</small>
            </div>

            <div class="item__sum qx-money">¥{{ formatMoney(item.itemPayableAmount) }}</div>
          </div>
        </div>

        <footer class="order__foot">
          <div class="order__sum">
            <span>共 {{ order.itemCount }} 件</span>
            <span class="order__sep">·</span>
            <span>商品 ¥{{ formatMoney(order.goodsAmount) }}</span>
            <span class="order__sep">·</span>
            <span>
              运费
              {{ order.freightAmount > 0 ? `¥${formatMoney(order.freightAmount)}` : '免运费' }}
            </span>
            <span class="order__payable">
              实付
              <span class="qx-money order__amount">
                <span class="qx-money__symbol">¥</span>{{ formatMoney(order.payableAmount) }}
              </span>
            </span>
          </div>

          <div class="order__actions">
            <el-button size="large" @click="router.push(`/order/${order.orderNo}`)">
              查看详情
            </el-button>
            <el-button
              v-if="order.status === 'PENDING_PAYMENT'"
              type="primary"
              size="large"
              @click="pay()"
            >
              去付款
            </el-button>
          </div>
        </footer>
      </article>
    </div>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.head__title {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.head__sub {
  margin-top: 4px;
  font-size: 13px;
  color: var(--qx-ink-3);
}

.head__link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 13px;
  color: var(--qx-ink-2);
  transition: color 0.18s ease;
}

.head__link svg {
  width: 16px;
  height: 16px;
}

.head__link:hover {
  color: var(--qx-brand);
}

/* ---------- 状态筛选 ---------- */
.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 18px;
  overflow-x: auto;
  scrollbar-width: none;
}

.tabs::-webkit-scrollbar {
  display: none;
}

.tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid var(--qx-line);
  border-radius: 20px;
  background: #fff;
  color: var(--qx-ink-2);
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.18s ease;
}

.tab:hover {
  border-color: var(--qx-brand);
  color: var(--qx-brand);
}

.tab.is-active {
  border-color: transparent;
  background: linear-gradient(135deg, #ff8a4c, #ff5a1f);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 6px 16px rgba(255, 90, 31, 0.22);
}

.tab__count {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: rgba(255, 107, 53, 0.12);
  color: var(--qx-brand);
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}

.tab.is-active .tab__count {
  background: rgba(255, 255, 255, 0.24);
  color: #fff;
}

/* ---------- 订单卡片 ---------- */
.list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.order {
  overflow: hidden;
}

.order__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 22px;
  background: #fafbfd;
  border-bottom: 1px solid var(--qx-line);
}

.order__meta {
  display: flex;
  align-items: baseline;
  gap: 14px;
  min-width: 0;
}

.order__no {
  font-family: 'DIN Alternate', 'Helvetica Neue', Arial, sans-serif;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.4px;
  color: var(--qx-ink);
  transition: color 0.18s ease;
}

.order__no:hover {
  color: var(--qx-brand);
}

.order__time {
  font-size: 12px;
  color: var(--qx-ink-3);
}

/* ---------- 店铺分组 ---------- */
.store {
  border-bottom: 1px dashed var(--qx-line);
}

.store:last-of-type {
  border-bottom: none;
}

.store__head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 22px 4px;
  font-size: 13px;
}

.store__icon {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  color: var(--qx-brand);
}

.store__icon svg {
  width: 16px;
  height: 16px;
}

.store__name {
  font-weight: 600;
  color: var(--qx-ink);
}

.store__sub {
  color: var(--qx-ink-3);
  font-size: 12px;
}

/* ---------- 商品行 ---------- */
.item {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) 100px 110px;
  align-items: center;
  gap: 14px;
  padding: 10px 22px 14px;
}

.item__media {
  width: 64px;
  height: 64px;
  border-radius: 10px;
  overflow: hidden;
  background: linear-gradient(160deg, #f8f9fb, #eef1f6);
}

.item__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.item__info {
  min-width: 0;
}

.item__title {
  font-size: 14px;
  line-height: 1.45;
  transition: color 0.18s ease;
}

.item__title:hover {
  color: var(--qx-brand);
}

.item__spec {
  margin-top: 4px;
  font-size: 12px;
  color: var(--qx-ink-3);
}

.item__price {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  font-size: 13px;
  color: var(--qx-ink-2);
}

.item__price small {
  font-size: 12px;
  color: var(--qx-ink-3);
}

.item__sum {
  text-align: right;
  font-size: 16px;
}

/* ---------- 卡片底部 ---------- */
.order__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 22px;
  background: #fcfdfe;
  border-top: 1px solid var(--qx-line);
}

.order__sum {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
  color: var(--qx-ink-2);
}

.order__sep {
  color: var(--qx-line);
}

.order__payable {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  margin-left: 8px;
  color: var(--qx-ink);
}

.order__amount {
  font-size: 22px;
  line-height: 1;
}

.order__actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

@media (max-width: 820px) {
  .order__foot {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .order__actions {
    justify-content: flex-end;
  }

  .item {
    grid-template-columns: 56px minmax(0, 1fr);
    row-gap: 6px;
  }

  .item__price,
  .item__sum {
    grid-column: 2;
    align-items: flex-start;
    text-align: left;
  }

  .item__sum {
    text-align: left;
  }

  .order__head {
    flex-wrap: wrap;
    gap: 8px;
  }
}
</style>
