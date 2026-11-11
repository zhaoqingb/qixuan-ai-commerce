<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { addToCart } from '@/api/cart'
import { fetchOrderDetail } from '@/api/order'
import { CURRENT_USER_ID } from '@/config'
import { useCartStore } from '@/stores/cart'
import type { OrderDetailRow } from '@/types/mall'
import { formatMoney, formatSpec } from '@/utils/spec'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()

const loading = ref(true)
const rows = ref<OrderDetailRow[]>([])
const buying = ref(false)

const orderNo = computed(() => (typeof route.params.orderNo === 'string' ? route.params.orderNo : ''))

interface AddressInfo {
  receiverName: string
  receiverPhone: string
  province: string
  city: string
  district: string
  detailAddress: string
}

interface SubOrderGroup {
  subOrderNo: string
  storeId: number
  storeName: string
  freightAmount: number
  payableAmount: number
  items: OrderDetailRow[]
  goodsAmount: number
  itemCount: number
}

interface OrderDetail {
  tradeOrderId: number
  orderNo: string
  status: string
  createdAt: string
  goodsAmount: number
  freightAmount: number
  discountAmount: number
  payableAmount: number
  addressSnapshot: string
  stores: SubOrderGroup[]
}

/** 按 subOrderNo 分组，一个子单 = 一个店铺 = 一个包裹 */
const order = computed<OrderDetail | null>(() => {
  if (rows.value.length === 0) return null
  const first = rows.value[0]
  const stores = new Map<string, SubOrderGroup>()

  for (const row of rows.value) {
    let group = stores.get(row.subOrderNo)
    if (!group) {
      group = {
        subOrderNo: row.subOrderNo,
        storeId: row.storeId,
        storeName: row.storeName,
        freightAmount: Number(row.subFreightAmount),
        payableAmount: Number(row.subPayableAmount),
        items: [],
        goodsAmount: 0,
        itemCount: 0,
      }
      stores.set(row.subOrderNo, group)
    }
    group.items.push(row)
    group.goodsAmount += Number(row.itemPayableAmount)
    group.itemCount += row.quantity
  }

  return {
    tradeOrderId: first.tradeOrderId,
    orderNo: first.orderNo,
    status: first.status,
    createdAt: first.createdAt,
    goodsAmount: Number(first.goodsAmount),
    freightAmount: Number(first.freightAmount),
    discountAmount: Number(first.discountAmount),
    payableAmount: Number(first.payableAmount),
    addressSnapshot: first.addressSnapshot,
    stores: [...stores.values()],
  }
})

/**
 * 地址是以 JSON 快照存在 store_order 里的（下单那一刻的副本，之后用户改地址不影响历史订单）。
 * 解析失败不能让页面白屏，所以整体 try 住。
 */
const address = computed<AddressInfo | null>(() => {
  const raw = order.value?.addressSnapshot
  if (!raw) return null
  try {
    return JSON.parse(raw) as AddressInfo
  } catch {
    return null
  }
})

const STATUS_TEXT: Record<string, string> = {
  PENDING_PAYMENT: '等待付款',
  PAID: '商家备货中',
  SHIPPED: '已发货',
  COMPLETED: '交易完成',
  CANCELLED: '订单已取消',
}

const STATUS_HINT: Record<string, string> = {
  PENDING_PAYMENT: '请在 24 小时内完成支付，超时订单将自动取消',
  PAID: '商家正在打包，请耐心等待发货',
  SHIPPED: '包裹已交给快递，请注意查收',
  COMPLETED: '感谢购买，期待再次光临',
  CANCELLED: '订单已关闭，库存已释放',
}

const STEPS = ['提交订单', '付款成功', '商家发货', '确认收货']

/** 各状态下已完成的最后一步；-1 表示不展示进度条（已取消） */
const doneStep = computed(() => {
  switch (order.value?.status) {
    case 'PENDING_PAYMENT':
      return 0
    case 'PAID':
      return 1
    case 'SHIPPED':
      return 2
    case 'COMPLETED':
      return 3
    default:
      return -1
  }
})

const statusText = computed(() => STATUS_TEXT[order.value?.status ?? ''] ?? order.value?.status ?? '')
const statusHint = computed(() => STATUS_HINT[order.value?.status ?? ''] ?? '')

function onImageError(event: Event) {
  const img = event.target as HTMLImageElement
  img.src = '/images/placeholder.svg'
}

async function copyOrderNo() {
  if (!order.value) return
  try {
    await navigator.clipboard.writeText(order.value.orderNo)
    ElMessage.success('订单号已复制')
  } catch {
    ElMessage.warning('浏览器不允许自动复制，请手动选中订单号')
  }
}

function pay() {
  ElMessage.info('演示环境未接入支付，订单保持「待付款」状态')
}

async function buyAgain() {
  if (!order.value) return
  buying.value = true
  try {
    for (const store of order.value.stores) {
      for (const item of store.items) {
        await addToCart({
          userId: CURRENT_USER_ID,
          skuId: item.skuId,
          quantity: item.quantity,
        })
      }
    }
    await cartStore.refresh()
    ElMessage.success('已按原数量加入购物车')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加入购物车失败')
  } finally {
    buying.value = false
  }
}

onMounted(async () => {
  try {
    rows.value = await fetchOrderDetail(orderNo.value, CURRENT_USER_ID)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '订单加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="detail qx-container qx-page">
    <nav class="crumb">
      <RouterLink to="/orders" class="crumb__link">我的订单</RouterLink>
      <span class="crumb__sep">/</span>
      <span class="crumb__current">订单详情</span>
    </nav>

    <el-skeleton v-if="loading" :rows="10" animated />

    <el-empty v-else-if="!order" description="订单不存在，或不属于当前账号">
      <el-button type="primary" @click="router.push('/orders')">返回订单列表</el-button>
    </el-empty>

    <template v-else>
      <!-- 状态横幅 -->
      <section class="banner qx-card">
        <div class="banner__head">
          <div>
            <h1 class="banner__title">{{ statusText }}</h1>
            <p class="banner__hint">{{ statusHint }}</p>
          </div>
          <div class="banner__no">
            <span class="banner__label">订单号</span>
            <button type="button" class="copy" :title="order.orderNo" @click="copyOrderNo">
              {{ order.orderNo }}
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <rect x="9" y="9" width="11" height="11" rx="2" fill="none" stroke="currentColor" stroke-width="1.7" />
                <path d="M15 6H6a2 2 0 0 0-2 2v9" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
              </svg>
            </button>
          </div>
        </div>

        <ol v-if="doneStep >= 0" class="steps">
          <li
            v-for="(step, index) in STEPS"
            :key="step"
            class="step"
            :class="{ 'is-done': index <= doneStep, 'is-current': index === doneStep }"
          >
            <span class="step__dot">
              <svg v-if="index <= doneStep" viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M5 12.5l4.5 4.5L19 7"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2.4"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </span>
            <span class="step__text">{{ step }}</span>
          </li>
        </ol>
      </section>

      <div class="layout">
        <div class="main">
          <!-- 收货信息 -->
          <section class="block qx-card">
            <header class="block__head">
              <h2 class="block__title">收货信息</h2>
              <span class="block__note">下单时间 {{ order.createdAt }}</span>
            </header>

            <div v-if="address" class="address">
              <div class="address__row">
                <span class="address__name">{{ address.receiverName }}</span>
                <span class="address__phone">{{ address.receiverPhone }}</span>
              </div>
              <p class="address__text">
                {{ address.province }}{{ address.city }}{{ address.district }}{{ address.detailAddress }}
              </p>
            </div>
            <p v-else class="qx-muted">该订单没有地址快照</p>
          </section>

          <!-- 商品清单：一个子单一个卡片 -->
          <section v-for="store in order.stores" :key="store.subOrderNo" class="block qx-card">
            <header class="block__head">
              <div class="block__left">
                <span class="block__icon">
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
                <h2 class="block__title">{{ store.storeName }}</h2>
                <span class="block__sub">子单号 {{ store.subOrderNo }}</span>
              </div>
              <span class="block__note">共 {{ store.itemCount }} 件</span>
            </header>

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

            <footer class="store__foot">
              <span>商品 ¥{{ formatMoney(store.goodsAmount) }}</span>
              <span class="store__sep">·</span>
              <span>
                运费 {{ store.freightAmount > 0 ? `¥${formatMoney(store.freightAmount)}` : '免运费' }}
              </span>
              <span class="store__payable">
                本店应付
                <span class="qx-money">¥{{ formatMoney(store.payableAmount) }}</span>
              </span>
            </footer>
          </section>
        </div>

        <!-- 侧栏：金额与操作 -->
        <aside class="side">
          <section class="block qx-card side__card">
            <h2 class="block__title">金额明细</h2>
            <dl class="fees">
              <div class="fees__row">
                <dt>商品金额</dt>
                <dd>¥{{ formatMoney(order.goodsAmount) }}</dd>
              </div>
              <div class="fees__row">
                <dt>运费</dt>
                <dd>{{ order.freightAmount > 0 ? `¥${formatMoney(order.freightAmount)}` : '免运费' }}</dd>
              </div>
              <div class="fees__row">
                <dt>优惠</dt>
                <dd class="fees__discount">-¥{{ formatMoney(order.discountAmount) }}</dd>
              </div>
              <div class="fees__row fees__row--total">
                <dt>实付款</dt>
                <dd class="qx-money side__amount">
                  <span class="qx-money__symbol">¥</span>{{ formatMoney(order.payableAmount) }}
                </dd>
              </div>
            </dl>

            <p class="side__note">
              本单拆为 {{ order.stores.length }} 个包裹，分别由各店铺发货
            </p>

            <div class="side__actions">
              <el-button
                v-if="order.status === 'PENDING_PAYMENT'"
                type="primary"
                size="large"
                class="side__btn"
                @click="pay()"
              >
                去付款
              </el-button>
              <el-button size="large" class="side__btn" :loading="buying" @click="buyAgain">
                再次购买
              </el-button>
              <el-button text class="side__back" @click="router.push('/orders')">
                返回订单列表
              </el-button>
            </div>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.crumb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 14px;
  font-size: 13px;
  color: var(--qx-ink-3);
}

.crumb__link:hover {
  color: var(--qx-brand);
}

.crumb__sep {
  color: var(--qx-line);
}

.crumb__current {
  color: var(--qx-ink-2);
}

/* ---------- 状态横幅 ---------- */
.banner {
  padding: 22px 26px;
  margin-bottom: 16px;
}

.banner__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.banner__title {
  font-size: 21px;
  font-weight: 700;
  letter-spacing: 0.3px;
}

.banner__hint {
  margin-top: 4px;
  font-size: 13px;
  color: var(--qx-ink-2);
}

.banner__no {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.banner__label {
  font-size: 12px;
  color: var(--qx-ink-3);
}

.copy {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border: 1px solid var(--qx-line);
  border-radius: 8px;
  background: #fbfcfe;
  color: var(--qx-ink-2);
  font-family: 'DIN Alternate', 'Helvetica Neue', Arial, sans-serif;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.18s ease;
}

.copy svg {
  width: 14px;
  height: 14px;
}

.copy:hover {
  border-color: var(--qx-brand);
  color: var(--qx-brand);
}

/* ---------- 进度条 ---------- */
.steps {
  display: flex;
  gap: 0;
  margin: 20px 0 0;
  padding: 0;
  list-style: none;
}

.step {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--qx-ink-3);
}

.step::before {
  content: '';
  position: absolute;
  top: 11px;
  right: 50%;
  width: 100%;
  height: 2px;
  background: #dde3ec;
}

.step:first-child::before {
  display: none;
}

.step.is-done::before {
  background: var(--qx-brand);
}

.step__dot {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 2px solid #dde3ec;
  background: #fff;
  color: #fff;
  transition: all 0.2s ease;
}

.step__dot svg {
  width: 14px;
  height: 14px;
}

.step.is-done .step__dot {
  border-color: var(--qx-brand);
  background: var(--qx-brand);
}

.step.is-current .step__dot {
  box-shadow: 0 0 0 4px rgba(255, 107, 53, 0.14);
}

.step.is-current .step__text {
  color: var(--qx-brand);
  font-weight: 600;
}

.step.is-done .step__text {
  color: var(--qx-ink-2);
}

/* ---------- 两栏布局 ---------- */
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;
}

.main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.block {
  padding: 18px 22px;
}

.block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.block__left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.block__icon {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  color: var(--qx-brand);
}

.block__icon svg {
  width: 16px;
  height: 16px;
}

.block__title {
  font-size: 15px;
  font-weight: 600;
}

.block__sub {
  font-size: 12px;
  color: var(--qx-ink-3);
}

.block__note {
  font-size: 12px;
  color: var(--qx-ink-3);
}

/* ---------- 地址 ---------- */
.address__row {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.address__name {
  font-size: 15px;
  font-weight: 600;
}

.address__phone {
  font-family: 'DIN Alternate', 'Helvetica Neue', Arial, sans-serif;
  font-size: 13px;
  color: var(--qx-ink-2);
  letter-spacing: 0.5px;
}

.address__text {
  margin-top: 6px;
  font-size: 13px;
  color: var(--qx-ink-2);
  line-height: 1.6;
}

/* ---------- 商品行 ---------- */
.item {
  display: grid;
  grid-template-columns: 60px minmax(0, 1fr) 96px 104px;
  align-items: center;
  gap: 14px;
  padding: 10px 0;
  border-top: 1px solid var(--qx-line);
}

.item:first-of-type {
  border-top: none;
}

.item__media {
  width: 60px;
  height: 60px;
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
  font-size: 13px;
  color: var(--qx-ink-2);
}

.item__price small {
  font-size: 12px;
  color: var(--qx-ink-3);
}

.item__sum {
  text-align: right;
  font-size: 15px;
}

.store__foot {
  display: flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px dashed var(--qx-line);
  font-size: 13px;
  color: var(--qx-ink-2);
}

.store__sep {
  color: var(--qx-line);
}

.store__payable {
  margin-left: 8px;
}

.store__payable .qx-money {
  font-size: 16px;
}

/* ---------- 侧栏 ---------- */
.side {
  position: sticky;
  top: 84px;
}

.side__card .block__title {
  margin-bottom: 14px;
}

.fees {
  margin: 0;
}

.fees__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 0;
  font-size: 13px;
}

.fees__row dt {
  color: var(--qx-ink-2);
}

.fees__row dd {
  margin: 0;
  color: var(--qx-ink);
}

.fees__discount {
  color: var(--qx-success);
}

.fees__row--total {
  margin-top: 8px;
  padding-top: 14px;
  border-top: 1px solid var(--qx-line);
}

.side__amount {
  font-size: 26px;
  line-height: 1;
}

.side__note {
  margin-top: 14px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--qx-brand-soft);
  color: var(--qx-brand-dark);
  font-size: 12px;
}

.side__actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 16px;
}

.side__btn {
  width: 100%;
  height: 42px;
  margin: 0;
  border-radius: 21px;
  font-size: 14px;
}

.side__btn.el-button--primary {
  border: none;
  background: linear-gradient(135deg, #ff8a4c, #ff5a1f);
  box-shadow: 0 8px 18px rgba(255, 90, 31, 0.22);
}

.side__back {
  align-self: center;
  font-size: 13px;
  color: var(--qx-ink-3);
}

@media (max-width: 980px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .side {
    position: static;
  }

  .banner__head {
    flex-direction: column;
    gap: 12px;
  }
}

@media (max-width: 720px) {
  .item {
    grid-template-columns: 52px minmax(0, 1fr);
    row-gap: 6px;
  }

  .item__price,
  .item__sum {
    grid-column: 2;
    align-items: flex-start;
    text-align: left;
  }

  .store__foot {
    flex-wrap: wrap;
    justify-content: flex-start;
  }
}
</style>
