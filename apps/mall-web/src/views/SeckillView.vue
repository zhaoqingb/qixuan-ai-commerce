<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import {
  buySeckill,
  cancelSeckill,
  convertSeckillOrder,
  fetchMySeckillOrders,
  fetchSeckillItems,
  fetchSeckillOrder,
} from '@/api/seckill'
import type { SeckillItem, SeckillOrderRecord } from '@/api/seckill'
import { useAuthStore } from '@/stores/auth'

type BuyState = 'idle' | 'buying' | 'queuing' | 'success' | 'fail'

const router = useRouter()
const authStore = useAuthStore()

const items = ref<SeckillItem[]>([])
const loading = ref(true)
const error = ref('')

/** 每个商品的抢购状态，key 是 itemId */
const buyState = reactive<Record<number, BuyState>>({})
/** 抢到的订单号，用于展示和查询 */
const orderNos = reactive<Record<number, string>>({})

/** 我的秒杀记录。秒杀订单不在 trade_order 里，所以订单页看不到，只能在这里回看 */
const records = ref<SeckillOrderRecord[]>([])

async function refreshRecords() {
  if (!authStore.isLoggedIn) return
  try {
    records.value = await fetchMySeckillOrders()
  } catch {
    // 记录拉不到不影响抢购主流程
  }
}

function recordStatusText(status: string): string {
  if (status === 'SUCCESS') return '已抢到'
  if (status === 'CANCELLED') return '已取消'
  if (status === 'PENDING') return '处理中'
  return status
}

/** 倒计时剩余秒数，取最近结束的一场活动 */
const remainSeconds = ref(0)
let tickTimer: number | undefined

const nearestEndAt = computed(() => {
  if (items.value.length === 0) return null
  const ends = items.value.map((i) => new Date(i.endAt).getTime()).filter((t) => !Number.isNaN(t))
  return ends.length ? Math.min(...ends) : null
})

const countdownText = computed(() => {
  const total = remainSeconds.value
  if (total <= 0) return '已结束'
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(h)}:${pad(m)}:${pad(s)}`
})

/** 已抢百分比：soldCount / (soldCount + stock) */
function soldPercent(item: SeckillItem): number {
  const total = item.soldCount + item.stock
  if (total <= 0) return 100
  return Math.min(100, Math.round((item.soldCount / total) * 100))
}

function isEnded(item: SeckillItem): boolean {
  return new Date(item.endAt).getTime() <= Date.now()
}

function isNotStarted(item: SeckillItem): boolean {
  return new Date(item.startAt).getTime() > Date.now()
}

function buttonLabel(item: SeckillItem): string {
  const state = buyState[item.itemId] ?? 'idle'
  if (state === 'buying') return '抢购中…'
  if (state === 'queuing') return '排队中…'
  if (state === 'success') return '已抢到'
  if (isEnded(item)) return '已结束'
  if (isNotStarted(item)) return '未开始'
  if (item.stock <= 0) return '已售罄'
  return '马上抢'
}

function buttonDisabled(item: SeckillItem): boolean {
  const state = buyState[item.itemId] ?? 'idle'
  if (state === 'buying' || state === 'queuing' || state === 'success') return true
  return isEnded(item) || isNotStarted(item) || item.stock <= 0
}

/** 抢到之后轮询订单状态：QUEUING → SUCCESS，最多等 10 秒 */
async function pollOrder(itemId: number, orderNo: string) {
  for (let i = 0; i < 10; i += 1) {
    await new Promise((resolve) => setTimeout(resolve, 800))
    try {
      const result = await fetchSeckillOrder(orderNo)
      if (result.status !== 'QUEUING') {
        buyState[itemId] = 'success'
        const target = items.value.find((it) => it.itemId === itemId)
        if (target) target.stock = Math.max(0, target.stock)
        refreshRecords()
        return
      }
    } catch {
      // 查询失败不打断，继续下一轮
    }
  }
  // 10 秒还没查到：不谎报成功，让用户去订单页确认
  buyState[itemId] = 'fail'
  ElMessage.warning('订单处理较慢，请到「我的订单」确认')
}

async function handleBuy(item: SeckillItem) {
  if (!authStore.isLoggedIn) {
    ElMessage.info('请先登录再参与秒杀')
    router.push({ path: '/login', query: { redirect: '/seckill' } })
    return
  }
  if (buttonDisabled(item)) return

  buyState[item.itemId] = 'buying'
  try {
    const result = await buySeckill(item.itemId)
    if (!result.orderNo) {
      buyState[item.itemId] = 'fail'
      ElMessage.error(result.message || '抢购失败')
      return
    }
    orderNos[item.itemId] = result.orderNo
    buyState[item.itemId] = 'queuing'
    // 乐观扣减，避免多个用户同时看到旧库存
    item.stock = Math.max(0, item.stock - 1)
    item.soldCount += 1
    ElMessage.success('抢到了！订单创建中')
    pollOrder(item.itemId, result.orderNo)
  } catch (ex) {
    buyState[item.itemId] = 'fail'
    ElMessage.error(ex instanceof Error ? ex.message : '抢购失败')
  }
}

/** 取消刚抢到的订单：名额会立刻还回库存池，可以再抢一次 */
async function handleCancel(item: SeckillItem) {
  const orderNo = orderNos[item.itemId]
  if (!orderNo) return
  try {
    await cancelSeckill(orderNo)
    buyState[item.itemId] = 'idle'
    delete orderNos[item.itemId]
    // 和后端保持一致：DB 是先改的，Redis 随后释放，前端这里同步回退展示值
    item.stock += 1
    item.soldCount = Math.max(0, item.soldCount - 1)
    ElMessage.success('已取消，名额已释放')
    refreshRecords()
  } catch (ex) {
    ElMessage.error(ex instanceof Error ? ex.message : '取消失败')
  }
}

/** 从记录列表取消（可能不是当前页展示的商品） */
async function cancelByOrderNo(orderNo: string) {
  try {
    await cancelSeckill(orderNo)
    ElMessage.success('已取消，名额已释放')
    await Promise.all([load(), refreshRecords()])
  } catch (ex) {
    ElMessage.error(ex instanceof Error ? ex.message : '取消失败')
  }
}

/** 去支付：先把秒杀订单转成正式订单，再跳到订单详情页 */
const convertingOrderNo = ref('')

async function payRecord(orderNo: string) {
  convertingOrderNo.value = orderNo
  try {
    const result = await convertSeckillOrder(orderNo)
    if (result.orderNo) {
      await refreshRecords()
      router.push(`/order/${result.orderNo}`)
      return
    }
    ElMessage.error(result.message || '下单失败')
  } catch (ex) {
    ElMessage.error(ex instanceof Error ? ex.message : '下单失败')
  } finally {
    convertingOrderNo.value = ''
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await fetchSeckillItems()
    items.value.forEach((item) => {
      buyState[item.itemId] = 'idle'
    })
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function tick() {
  const end = nearestEndAt.value
  if (!end) {
    remainSeconds.value = 0
    return
  }
  remainSeconds.value = Math.max(0, Math.floor((end - Date.now()) / 1000))
}

onMounted(async () => {
  await load()
  await refreshRecords()
  tick()
  tickTimer = window.setInterval(tick, 1000)
})

onBeforeUnmount(() => {
  if (tickTimer) window.clearInterval(tickTimer)
})

function goOrders() {
  router.push('/orders')
}
</script>

<template>
  <div class="seckill">
    <!-- 顶部横幅：活动信息 + 倒计时 -->
    <section class="hero">
      <div class="hero__inner qx-container">
        <div class="hero__text">
          <span class="hero__badge">限时秒杀</span>
          <h1 class="hero__title">整点开抢 · 手慢无</h1>
          <p class="hero__desc">每人限购 1 件，先到先得。抢到后订单会在几秒内生成。</p>
        </div>
        <div class="hero__clock">
          <span class="hero__clock-label">距结束</span>
          <strong class="hero__clock-value">{{ countdownText }}</strong>
        </div>
      </div>
    </section>

    <section class="qx-container body">
      <div class="toolbar">
        <h2 class="toolbar__title">正在抢购</h2>
        <div class="toolbar__right">
          <span class="toolbar__count">{{ items.length }} 件商品</span>
          <button type="button" class="toolbar__link" @click="goOrders">我的订单</button>
        </div>
      </div>

      <div v-if="loading" class="grid">
        <div v-for="n in 6" :key="n" class="skeleton"></div>
      </div>

      <div v-else-if="error" class="empty">
        <p>{{ error }}</p>
        <button type="button" class="empty__btn" @click="load">重新加载</button>
      </div>

      <div v-else-if="items.length === 0" class="empty">
        <p>当前没有进行中的秒杀活动</p>
      </div>

      <div v-else class="grid">
        <article v-for="item in items" :key="item.itemId" class="card">
          <div class="card__media" @click="router.push(`/product/${item.spuId}`)">
            <img v-if="item.mainImageUrl" :src="item.mainImageUrl" :alt="item.title" />
            <span v-else class="card__placeholder">暂无图片</span>
            <span class="card__tag">秒杀</span>
          </div>

          <div class="card__body">
            <h3 class="card__title" :title="item.title">{{ item.title }}</h3>
            <p v-if="item.subtitle" class="card__subtitle">{{ item.subtitle }}</p>

            <div class="price">
              <span class="price__symbol">¥</span>
              <span class="price__now">{{ item.seckillPrice }}</span>
              <span class="price__origin">¥{{ item.originalPrice }}</span>
            </div>

            <div class="progress">
              <div class="progress__bar">
                <span class="progress__fill" :style="{ width: `${soldPercent(item)}%` }"></span>
              </div>
              <span class="progress__text">已抢 {{ soldPercent(item) }}%</span>
            </div>

            <div class="card__foot">
              <span class="card__stock">剩 {{ item.stock }} 件</span>
              <button
                type="button"
                class="buy"
                :class="{ 'is-disabled': buttonDisabled(item), 'is-done': buyState[item.itemId] === 'success' }"
                :disabled="buttonDisabled(item)"
                @click="handleBuy(item)"
              >
                {{ buttonLabel(item) }}
              </button>
            </div>

            <p v-if="orderNos[item.itemId]" class="card__order">
              <span>订单号 {{ orderNos[item.itemId] }}</span>
              <button
                v-if="buyState[item.itemId] === 'success'"
                type="button"
                class="cancel"
                @click="handleCancel(item)"
              >
                取消订单
              </button>
            </p>
          </div>
        </article>
      </div>
    </section>

    <!-- 秒杀记录：秒杀订单不进 trade_order，订单页看不到，只能在这里回看 -->
    <section v-if="authStore.isLoggedIn" class="records qx-container">
      <h2 class="records__title">我的秒杀记录</h2>

      <p v-if="records.length === 0" class="records__empty">还没有抢过东西</p>

      <ul v-else class="records__list">
        <li v-for="record in records" :key="record.orderNo" class="record">
          <div class="record__main">
            <span class="record__no">{{ record.orderNo }}</span>
            <span class="record__meta">¥{{ record.amount }} · {{ record.createdAt?.replace('T', ' ') }}</span>
          </div>
          <span class="record__status" :class="`is-${record.status.toLowerCase()}`">
            {{ recordStatusText(record.status) }}
          </span>
          <template v-if="record.status === 'SUCCESS'">
            <button
              type="button"
              class="record__pay"
              :disabled="convertingOrderNo === record.orderNo"
              @click="payRecord(record.orderNo)"
            >
              {{ convertingOrderNo === record.orderNo ? '处理中…' : '去支付' }}
            </button>
            <button
              type="button"
              class="record__cancel"
              @click="cancelByOrderNo(record.orderNo)"
            >
              取消
            </button>
          </template>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.seckill {
  padding-bottom: 64px;
}

/* ---------- 横幅 ---------- */
.hero {
  background: var(--qx-grad);
  color: #fff;
  padding: 40px 0 48px;
  position: relative;
  overflow: hidden;
}

.hero::after {
  content: '';
  position: absolute;
  right: -80px;
  top: -80px;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
}

.hero__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  position: relative;
  z-index: 1;
}

.hero__badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.22);
  font-size: 12px;
  letter-spacing: 1px;
}

.hero__title {
  margin: 12px 0 8px;
  font-size: 30px;
  font-weight: 700;
}

.hero__desc {
  margin: 0;
  font-size: 14px;
  opacity: 0.9;
}

.hero__clock {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 16px 26px;
  border-radius: var(--qx-radius);
  background: rgba(0, 0, 0, 0.16);
  backdrop-filter: blur(6px);
}

.hero__clock-label {
  font-size: 12px;
  opacity: 0.85;
}

.hero__clock-value {
  font-size: 28px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 2px;
}

/* ---------- 列表 ---------- */
.body {
  margin-top: -24px;
  position: relative;
  z-index: 2;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 4px 14px;
}

.toolbar__title {
  margin: 0;
  font-size: 20px;
}

.toolbar__right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.toolbar__count {
  color: var(--qx-ink-3);
  font-size: 13px;
}

.toolbar__link {
  border: none;
  background: none;
  color: var(--qx-brand);
  font-size: 13px;
  cursor: pointer;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 18px;
}

.skeleton {
  height: 360px;
  border-radius: var(--qx-radius);
  background: linear-gradient(90deg, #eef1f6, #f7f9fc, #eef1f6);
  background-size: 200% 100%;
  animation: shine 1.4s infinite;
}

@keyframes shine {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}

/* ---------- 卡片 ---------- */
.card {
  background: var(--qx-card);
  border-radius: var(--qx-radius);
  box-shadow: var(--qx-shadow);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  transition: transform 0.2s, box-shadow 0.2s;
}

.card:hover {
  transform: translateY(-3px);
  box-shadow: var(--qx-shadow-lg);
}

.card__media {
  position: relative;
  aspect-ratio: 1 / 1;
  background: #f2f4f8;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card__placeholder {
  color: var(--qx-ink-3);
  font-size: 13px;
}

.card__tag {
  position: absolute;
  left: 10px;
  top: 10px;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--qx-grad);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.card__body {
  padding: 14px 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.card__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card__subtitle {
  margin: 0;
  font-size: 12px;
  color: var(--qx-ink-3);
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.price {
  display: flex;
  align-items: baseline;
  gap: 2px;
  color: #e8340c;
}

.price__symbol {
  font-size: 14px;
  font-weight: 600;
}

.price__now {
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
}

.price__origin {
  margin-left: 8px;
  font-size: 12px;
  color: var(--qx-ink-3);
  text-decoration: line-through;
}

.progress {
  display: flex;
  align-items: center;
  gap: 8px;
}

.progress__bar {
  flex: 1;
  height: 8px;
  border-radius: 999px;
  background: var(--qx-brand-soft);
  overflow: hidden;
}

.progress__fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--qx-grad);
  transition: width 0.3s;
}

.progress__text {
  font-size: 11px;
  color: var(--qx-ink-3);
  white-space: nowrap;
}

.card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
}

.card__stock {
  font-size: 12px;
  color: var(--qx-ink-2);
}

.buy {
  height: 36px;
  padding: 0 20px;
  border: none;
  border-radius: 18px;
  background: var(--qx-grad);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: var(--qx-grad-shadow);
  transition: opacity 0.2s, transform 0.1s;
}

.buy:active:not(:disabled) {
  transform: scale(0.97);
}

.buy.is-disabled {
  background: #d7dce5;
  box-shadow: none;
  cursor: not-allowed;
}

.buy.is-done {
  background: var(--qx-success);
  box-shadow: none;
}

.card__order {
  margin: 0;
  font-size: 11px;
  color: var(--qx-ink-3);
  word-break: break-all;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.cancel {
  flex-shrink: 0;
  border: none;
  background: none;
  padding: 0;
  color: var(--qx-ink-3);
  font-size: 11px;
  text-decoration: underline;
  cursor: pointer;
}

.cancel:hover {
  color: #e8340c;
}

/* ---------- 秒杀记录 ---------- */
.records {
  margin-top: 36px;
}

.records__title {
  margin: 0 0 14px;
  font-size: 18px;
}

.records__empty {
  padding: 28px 0;
  color: var(--qx-ink-3);
  font-size: 13px;
}

.records__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.record {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: var(--qx-radius);
  background: var(--qx-card);
  box-shadow: var(--qx-shadow);
}

.record__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.record__no {
  font-size: 13px;
  font-weight: 600;
  word-break: break-all;
}

.record__meta {
  font-size: 12px;
  color: var(--qx-ink-3);
}

.record__status {
  flex-shrink: 0;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 999px;
}

.record__status.is-success {
  background: #e8f7ef;
  color: var(--qx-success);
}

.record__status.is-cancelled {
  background: #f2f4f8;
  color: var(--qx-ink-3);
}

.record__status.is-pending {
  background: var(--qx-brand-soft);
  color: var(--qx-brand-dark);
}

.record__pay {
  flex-shrink: 0;
  height: 30px;
  padding: 0 16px;
  border: none;
  border-radius: 15px;
  background: var(--qx-grad);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: var(--qx-grad-shadow);
}

.record__pay:disabled {
  background: #d7dce5;
  box-shadow: none;
  cursor: wait;
}

.record__cancel {
  flex-shrink: 0;
  border: none;
  background: none;
  color: var(--qx-ink-3);
  font-size: 12px;
  text-decoration: underline;
  cursor: pointer;
}

.record__cancel:hover {
  color: #e8340c;
}

/* ---------- 空态 ---------- */
.empty {
  padding: 60px 0;
  text-align: center;
  color: var(--qx-ink-2);
}

.empty__btn {
  margin-top: 12px;
  padding: 10px 24px;
  border: none;
  border-radius: var(--qx-btn-radius);
  background: var(--qx-grad);
  color: #fff;
  cursor: pointer;
}

@media (max-width: 720px) {
  .hero__inner {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero__title {
    font-size: 24px;
  }

  .grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .price__now {
    font-size: 22px;
  }
}
</style>
