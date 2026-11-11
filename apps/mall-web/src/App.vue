<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useCartStore } from '@/stores/cart'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()

const keyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')

// 路由 → 输入框：点分类、点「全部商品」、浏览器后退时，输入框要跟着变空
watch(
  () => route.query.keyword,
  (value) => {
    const next = typeof value === 'string' ? value : ''
    if (next !== keyword.value) keyword.value = next
  },
)

// 输入框 → 路由：删光文字就该退出搜索态。
// 否则列表还挂着上一次关键词的筛选结果，看起来像「删了也没生效」。
watch(keyword, (value) => {
  if (value.trim() !== '') return
  if (typeof route.query.keyword !== 'string') return
  const query = { ...route.query }
  delete query.keyword
  router.replace({ path: '/', query })
})

function search() {
  const kw = keyword.value.trim()
  router.push({ path: '/', query: kw ? { keyword: kw } : {} })
}

function clearKeyword() {
  keyword.value = ''
}

onMounted(() => {
  cartStore.refresh().catch(() => undefined)
})
</script>

<template>
  <header class="topbar">
    <div class="qx-container topbar__inner">
      <RouterLink to="/" class="brand">
        <span class="brand__mark">栖</span>
        <span class="brand__text">
          <strong>栖选商城</strong>
          <small>QIXUAN SELECT</small>
        </span>
      </RouterLink>

      <div class="search">
        <svg class="search__icon" viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="11" cy="11" r="7" fill="none" stroke="currentColor" stroke-width="2" />
          <path d="M16.5 16.5 L21 21" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
        </svg>
        <input
          v-model="keyword"
          type="text"
          placeholder="搜索商品，试试「耳机」「咖啡」「T 恤」"
          @keyup.enter="search"
        />
        <button
          v-if="keyword"
          type="button"
          class="search__clear"
          aria-label="清除搜索"
          @click="clearKeyword"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M7 7l10 10M17 7L7 17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
        </button>
        <button type="button" class="search__btn" @click="search">搜索</button>
      </div>

      <nav class="actions">
        <RouterLink to="/" class="action" :class="{ 'is-active': route.name === 'home' }">
          首页
        </RouterLink>
        <RouterLink to="/orders" class="action" :class="{ 'is-active': route.name === 'orders' }">
          我的订单
        </RouterLink>
        <RouterLink to="/cart" class="action action--cart" :class="{ 'is-active': route.name === 'cart' }">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M4 5h2.2l2.1 10.2h9.5L20 8H7"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <circle cx="9.5" cy="19" r="1.6" fill="currentColor" />
            <circle cx="17" cy="19" r="1.6" fill="currentColor" />
          </svg>
          购物车
          <span v-if="cartStore.totalQuantity > 0" class="badge">
            {{ cartStore.totalQuantity > 99 ? '99+' : cartStore.totalQuantity }}
          </span>
        </RouterLink>
      </nav>
    </div>
  </header>

  <main class="main">
    <RouterView />
  </main>

  <footer class="footer">
    <div class="qx-container footer__inner">
      <div class="footer__brand">
        <span class="brand__mark brand__mark--sm">栖</span>
        <div>
          <strong>栖选商城</strong>
          <p>多商户 B2C 电商演示项目 · Vue 3 + Spring Boot + MySQL</p>
        </div>
      </div>
      <div class="footer__links">
        <span>商品浏览</span>
        <span>购物车</span>
        <span>订单结算</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: saturate(180%) blur(12px);
  border-bottom: 1px solid var(--qx-line);
}

.topbar__inner {
  display: flex;
  align-items: center;
  gap: 28px;
  height: 68px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.brand__mark {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 11px;
  background: linear-gradient(135deg, #ff8a4c, #ff5a1f);
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 1px;
  box-shadow: 0 6px 16px rgba(255, 90, 31, 0.32);
}

.brand__mark--sm {
  width: 32px;
  height: 32px;
  font-size: 17px;
  border-radius: 9px;
}

.brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.brand__text strong {
  font-size: 17px;
  letter-spacing: 1px;
}

.brand__text small {
  font-size: 10px;
  color: var(--qx-ink-3);
  letter-spacing: 1.6px;
}

.search {
  position: relative;
  display: flex;
  align-items: center;
  flex: 1;
  max-width: 520px;
  height: 42px;
  padding-left: 14px;
  border: 1.5px solid var(--qx-line);
  border-radius: 21px;
  background: #fbfcfe;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.search:focus-within {
  border-color: var(--qx-brand);
  box-shadow: 0 0 0 4px rgba(255, 107, 53, 0.1);
  background: #fff;
}

.search__icon {
  width: 18px;
  height: 18px;
  color: var(--qx-ink-3);
  flex-shrink: 0;
}

.search input {
  flex: 1;
  height: 100%;
  padding: 0 10px;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: var(--qx-ink);
}

.search input::placeholder {
  color: var(--qx-ink-3);
}

.search__btn {
  height: 34px;
  margin-right: 4px;
  padding: 0 20px;
  border: none;
  border-radius: 17px;
  background: linear-gradient(135deg, #ff8a4c, #ff5a1f);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: filter 0.18s ease;
}.search__btn:hover {
  filter: brightness(1.05);
}

.search__clear {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  margin-right: 6px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: #e8ecf3;
  color: var(--qx-ink-3);
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease;
}

.search__clear:hover {
  background: #dbe1ea;
  color: var(--qx-ink);
}

.search__clear svg {
  width: 12px;
  height: 12px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  flex-shrink: 0;
}

.action {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 10px;
  color: var(--qx-ink-2);
  font-size: 14px;
  white-space: nowrap;
  transition: background 0.18s ease, color 0.18s ease;
}

.action svg {
  width: 20px;
  height: 20px;
}

.action:hover {
  background: #f4f6fa;
  color: var(--qx-ink);
}

.action.is-active {
  background: var(--qx-brand-soft);
  color: var(--qx-brand-dark);
  font-weight: 600;
}

/* 购物车按钮右侧留出角标位置，避免角标压住文字 */
.action--cart {
  padding-right: 28px;
}

.badge {
  position: absolute;
  top: 3px;
  right: 5px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--qx-brand);
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  font-weight: 600;
}

.main {
  min-height: calc(100vh - 68px - 132px);
}

.footer {
  margin-top: 40px;
  padding: 32px 0;
  background: #fff;
  border-top: 1px solid var(--qx-line);
}

.footer__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}

.footer__brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.footer__brand strong {
  font-size: 15px;
}

.footer__brand p {
  font-size: 12px;
  color: var(--qx-ink-3);
}

.footer__links {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: var(--qx-ink-3);
}

@media (max-width: 860px) {
  .topbar__inner {
    gap: 14px;
    height: auto;
    padding-top: 10px;
    padding-bottom: 10px;
    flex-wrap: wrap;
  }

  .brand__text small {
    display: none;
  }

  .search {
    order: 3;
    max-width: none;
    flex-basis: 100%;
  }

  .action {
    padding: 6px 10px;
    font-size: 13px;
  }
}
</style>
