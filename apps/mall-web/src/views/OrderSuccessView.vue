<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { formatMoney } from '@/utils/spec'

const route = useRoute()
const router = useRouter()

const orderNo = computed(() => (typeof route.query.orderNo === 'string' ? route.query.orderNo : ''))
const amount = computed(() => Number(route.query.amount ?? 0))
const itemCount = computed(() => Number(route.query.count ?? 0))
const storeCount = computed(() => Number(route.query.stores ?? 0))
</script>

<template>
  <div class="success qx-container qx-page">
    <div class="card qx-card">
      <div class="badge">
        <svg viewBox="0 0 52 52" aria-hidden="true">
          <circle cx="26" cy="26" r="24" fill="none" stroke="currentColor" stroke-width="3" />
          <path
            d="M15 27 l8 8 l15 -16"
            fill="none"
            stroke="currentColor"
            stroke-width="4"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </div>

      <h1>订单提交成功</h1>
      <p class="subtitle">商家正在备货，请保持手机畅通。当前为演示环境，未接入真实支付。</p>

      <dl v-if="orderNo" class="info">
        <div class="info__row">
          <dt>订单号</dt>
          <dd class="info__no">{{ orderNo }}</dd>
        </div>
        <div class="info__row">
          <dt>应付金额</dt>
          <dd class="info__amount">¥{{ formatMoney(amount) }}</dd>
        </div>
        <div class="info__row">
          <dt>商品件数</dt>
          <dd>{{ itemCount }} 件</dd>
        </div>
        <div class="info__row">
          <dt>拆单情况</dt>
          <dd>{{ storeCount }} 个店铺</dd>
        </div>
      </dl>

      <div class="actions">
        <el-button type="primary" size="large" class="btn" @click="router.push('/orders')">
          查看订单
        </el-button>
        <el-button size="large" @click="router.push('/')">继续购物</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.success {
  display: flex;
  justify-content: center;
  padding-top: 48px;
}

.card {
  width: 100%;
  max-width: 560px;
  padding: 40px 44px 36px;
  text-align: center;
  animation: rise 0.4s ease both;
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(12px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.badge {
  width: 76px;
  height: 76px;
  margin: 0 auto 18px;
  color: var(--qx-success);
  animation: pop 0.45s cubic-bezier(0.34, 1.56, 0.64, 1) both;
}

@keyframes pop {
  from {
    transform: scale(0.6);
    opacity: 0;
  }

  to {
    transform: scale(1);
    opacity: 1;
  }
}

.card h1 {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.subtitle {
  margin-top: 8px;
  font-size: 13px;
  color: var(--qx-ink-3);
}

.info {
  margin: 24px 0 28px;
  padding: 16px 20px;
  border-radius: 12px;
  background: #fafbfd;
  text-align: left;
}

.info__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 0;
  border-bottom: 1px dashed #eef1f6;
}

.info__row:last-child {
  border-bottom: none;
}

.info dt {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--qx-ink-3);
}

.info dd {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  text-align: right;
  word-break: break-all;
}

.info__no {
  font-family: 'DIN Alternate', 'Helvetica Neue', Arial, sans-serif;
  letter-spacing: 0.4px;
}

.info__amount {
  color: var(--qx-brand);
  font-size: 18px;
  font-weight: 700;
}

.actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}

.btn {
  min-width: 132px;
  height: 44px;
  border: none;
  border-radius: 22px;
  background: linear-gradient(135deg, #ff8a4c, #ff5a1f);
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 10px 22px rgba(255, 90, 31, 0.24);
}

@media (max-width: 560px) {
  .card {
    padding: 30px 22px 26px;
  }
}
</style>
