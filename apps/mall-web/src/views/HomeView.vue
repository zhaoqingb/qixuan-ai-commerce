<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { fetchCategoryTree } from '@/api/category'
import { fetchProducts } from '@/api/product'
import ProductCardItem from '@/components/ProductCard.vue'
import type { CategoryNode, ProductCard as ProductCardType } from '@/types/mall'

const route = useRoute()
const router = useRouter()

const categories = ref<CategoryNode[]>([])
const products = ref<ProductCardType[]>([])
const loading = ref(false)
const errorMessage = ref('')

const banners = [
  { id: 1, tag: '限时', title: '开季焕新', desc: '全场满 99 元包邮，跨店一次结算', theme: 'amber' },
  { id: 2, tag: '爆款', title: '数码专场', desc: '耳机 · 键盘 · 充电宝，直降至高 300 元', theme: 'violet' },
  { id: 3, tag: '新品', title: '家居好物', desc: '咖啡豆 · 洁面乳 · 挂烫机，低至 5 折', theme: 'teal' },
]
const bannerIndex = ref(0)
let bannerTimer: number | undefined

const activeCategoryId = computed<number | null>(() => {
  const raw = route.query.categoryId
  const value = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isFinite(value) && value > 0 ? value : null
})

const activeKeyword = computed(() =>
  typeof route.query.keyword === 'string' ? route.query.keyword : '',
)

const activeCategoryName = computed(() => {
  if (activeCategoryId.value === null) return ''
  return categories.value.find((item) => item.id === activeCategoryId.value)?.name ?? ''
})

/** 有任一筛选条件时，隐藏首页楼层，只展示结果列表 */
const isFiltering = computed(() => activeCategoryId.value !== null || activeKeyword.value !== '')

/** 按分类名把商品分到各楼层，每层最多 5 件 */
const floors = computed(() => {
  const groups = new Map<string, ProductCardType[]>()
  for (const product of products.value) {
    const key = product.categoryName || '其他'
    const bucket = groups.get(key)
    if (bucket) {
      bucket.push(product)
    } else {
      groups.set(key, [product])
    }
  }

  const result: Array<{ name: string; items: ProductCardType[] }> = []
  for (const entry of groups) {
    result.push({ name: entry[0], items: entry[1].slice(0, 5) })
  }
  return result
})

async function loadCategories() {
  try {
    const tree = await fetchCategoryTree()
    categories.value = tree.filter((item) => item.parentId === null)
  } catch {
    categories.value = []
  }
}

async function loadProducts() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await fetchProducts({
      categoryId: activeCategoryId.value ?? undefined,
      keyword: activeKeyword.value || undefined,
      size: 60,
    })
    products.value = result.records
  } catch (error) {
    products.value = []
    errorMessage.value = error instanceof Error ? error.message : '商品加载失败'
  } finally {
    loading.value = false
  }
}

/**
 * 点分类属于「重新浏览」，必须把上一次的搜索词一起丢掉。
 * 否则会踩这个坑：搜一个不存在的词 → 列表空 → 点「全部商品」→ keyword 还在 URL 里 → 依旧空白。
 */
function selectCategory(id: number | null) {
  const query: Record<string, string> = {}
  if (id !== null) query.categoryId = String(id)
  applyQuery(query)
}

function clearFilters() {
  applyQuery({})
}

/** 只有筛选条件真的变了才跳转，避免重复导航告警和多余请求 */
function applyQuery(query: Record<string, string>) {
  const current = route.query
  const keys = Object.keys(query)
  const same =
    keys.length === Object.keys(current).length &&
    keys.every((key) => current[key] === query[key])
  if (same) return
  router.push({ path: '/', query })
}

function goBanner(index: number) {
  bannerIndex.value = index
}

function startBanner() {
  bannerTimer = window.setInterval(() => {
    bannerIndex.value = (bannerIndex.value + 1) % banners.length
  }, 4500)
}

onMounted(() => {
  loadCategories()
  startBanner()
})

onUnmounted(() => {
  if (bannerTimer !== undefined) window.clearInterval(bannerTimer)
})

watch([activeCategoryId, activeKeyword], loadProducts, { immediate: true })
</script>

<template>
  <div class="home">
    <!-- ============ 筛选态：只展示结果 ============ -->
    <template v-if="isFiltering">
      <div class="qx-container result">
        <div class="result__head">
          <h1>
            <template v-if="activeKeyword">“{{ activeKeyword }}” 的搜索结果</template>
            <template v-else>{{ activeCategoryName || '全部商品' }}</template>
          </h1>
          <span class="result__count">共 {{ products.length }} 件</span>
          <button type="button" class="result__back" @click="clearFilters">返回首页</button>
        </div>

        <div v-if="loading" class="grid">
          <div v-for="n in 8" :key="n" class="skeleton">
            <el-skeleton animated>
              <template #template>
                <el-skeleton-item variant="image" style="width: 100%; height: 240px" />
                <div style="padding: 14px">
                  <el-skeleton-item variant="h3" style="width: 86%" />
                  <el-skeleton-item variant="text" style="width: 56%; margin-top: 10px" />
                  <el-skeleton-item variant="text" style="width: 38%; margin-top: 14px" />
                </div>
              </template>
            </el-skeleton>
          </div>
        </div>

        <el-empty v-else-if="errorMessage" :description="errorMessage">
          <el-button type="primary" @click="loadProducts">重新加载</el-button>
        </el-empty>

        <el-empty
          v-else-if="products.length === 0"
          description="没有找到符合条件的商品，换个关键词试试"
        >
          <el-button type="primary" @click="clearFilters">看看全部商品</el-button>
        </el-empty>

        <div v-else class="grid">
          <ProductCardItem v-for="item in products" :key="item.id" :product="item" />
        </div>
      </div>
    </template>

    <!-- ============ 首页态：侧栏 + 轮播 + 金刚区 + 楼层 ============ -->
    <template v-else>
      <section class="stage qx-container">
        <aside class="catnav">
          <h2 class="catnav__title">全部分类</h2>
          <ul class="catnav__list">
            <li v-for="item in categories" :key="item.id">
              <button type="button" class="catnav__item" @click="selectCategory(item.id)">
                <span class="catnav__name">{{ item.name }}</span>
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path
                    d="M9 6l6 6-6 6"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </button>
            </li>
            <li v-if="categories.length === 0" class="catnav__empty">分类加载中…</li>
          </ul>
          <button type="button" class="catnav__all" @click="selectCategory(null)">查看全部商品</button>
        </aside>

        <div class="slides">
          <article
            v-for="(banner, index) in banners"
            :key="banner.id"
            class="slide"
            :class="[`slide--${banner.theme}`, { 'is-active': index === bannerIndex }]"
          >
            <span class="slide__tag">{{ banner.tag }}</span>
            <h2 class="slide__title">{{ banner.title }}</h2>
            <p class="slide__desc">{{ banner.desc }}</p>
            <button type="button" class="slide__cta" @click="selectCategory(null)">立即选购</button>
            <span class="slide__orb slide__orb--a"></span>
            <span class="slide__orb slide__orb--b"></span>
          </article>

          <div class="dots">
            <button
              v-for="(banner, index) in banners"
              :key="banner.id"
              type="button"
              class="dots__item"
              :class="{ 'is-active': index === bannerIndex }"
              :aria-label="`第 ${index + 1} 张`"
              @click="goBanner(index)"
            ></button>
          </div>
        </div>
      </section>

      <section class="quick qx-container">
        <button type="button" class="quick__item" @click="selectCategory(null)">
          <span class="quick__icon quick__icon--all">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <rect x="4" y="4" width="7" height="7" rx="2" fill="currentColor" />
              <rect x="13" y="4" width="7" height="7" rx="2" fill="currentColor" opacity="0.6" />
              <rect x="4" y="13" width="7" height="7" rx="2" fill="currentColor" opacity="0.6" />
              <rect x="13" y="13" width="7" height="7" rx="2" fill="currentColor" opacity="0.35" />
            </svg>
          </span>
          <span class="quick__label">全部商品</span>
        </button>
        <button
          v-for="item in categories"
          :key="item.id"
          type="button"
          class="quick__item"
          @click="selectCategory(item.id)"
        >
          <span class="quick__icon">{{ item.name.slice(0, 1) }}</span>
          <span class="quick__label">{{ item.name }}</span>
        </button>
      </section>

      <div v-if="loading" class="qx-container grid">
        <div v-for="n in 5" :key="n" class="skeleton">
          <el-skeleton animated>
            <template #template>
              <el-skeleton-item variant="image" style="width: 100%; height: 200px" />
              <div style="padding: 12px">
                <el-skeleton-item variant="h3" style="width: 84%" />
                <el-skeleton-item variant="text" style="width: 40%; margin-top: 10px" />
              </div>
            </template>
          </el-skeleton>
        </div>
      </div>

      <el-empty v-else-if="errorMessage" :description="errorMessage" class="qx-container">
        <el-button type="primary" @click="loadProducts">重新加载</el-button>
      </el-empty>

      <template v-else>
        <section v-for="floor in floors" :key="floor.name" class="floor qx-container">
          <header class="floor__head">
            <h2>{{ floor.name }}</h2>
            <button type="button" class="floor__more" @click="selectCategory(null)">
              查看全部
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M9 6l6 6-6 6"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </button>
          </header>
          <div class="floor__grid">
            <ProductCardItem v-for="item in floor.items" :key="item.id" :product="item" />
          </div>
        </section>
      </template>
    </template>
  </div>
</template>

<style scoped>
.home {
  padding: 22px 0 72px;
}

/* ================= 首屏：分类侧栏 + 轮播 ================= */
.stage {
  display: grid;
  grid-template-columns: 208px minmax(0, 1fr);
  gap: 16px;
}

.catnav {
  display: flex;
  flex-direction: column;
  padding: 14px 10px 12px;
  border-radius: 16px;
  background: #fff;
  box-shadow: var(--qx-shadow);
}

.catnav__title {
  margin: 0 6px 8px;
  font-size: 12px;
  font-weight: 500;
  color: var(--qx-ink-3);
  letter-spacing: 0.6px;
}

.catnav__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
  flex: 1;
}

.catnav__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 9px 10px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--qx-ink-2);
  font-size: 13.5px;
  text-align: left;
  cursor: pointer;
  transition: background 0.16s ease, color 0.16s ease;
}

.catnav__item svg {
  width: 15px;
  height: 15px;
  opacity: 0;
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.catnav__item:hover {
  background: var(--qx-brand-soft);
  color: var(--qx-brand-dark);
  font-weight: 600;
}

.catnav__item:hover svg {
  opacity: 1;
  transform: translateX(2px);
}

.catnav__empty {
  padding: 10px;
  font-size: 12.5px;
  color: var(--qx-ink-3);
}

.catnav__all {
  margin-top: 10px;
  padding: 9px;
  border: none;
  border-radius: 9px;
  background: var(--qx-grad);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: filter 0.16s ease;
}

.catnav__all:hover {
  filter: brightness(1.05);
}

/* ---------- 轮播 ---------- */
.slides {
  position: relative;
  height: 340px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: var(--qx-shadow);
}

.slide {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 56px;
  opacity: 0;
  transform: scale(1.02);
  transition: opacity 0.6s ease, transform 0.6s ease;
  pointer-events: none;
  overflow: hidden;
}

.slide.is-active {
  opacity: 1;
  transform: scale(1);
  pointer-events: auto;
}

.slide--amber {
  background: linear-gradient(125deg, #ffb066 0%, #ff7a35 52%, #f2551a 100%);
  color: #fff;
}

.slide--violet {
  background: linear-gradient(125deg, #8a8ff0 0%, #6558e0 55%, #4b3fc4 100%);
  color: #fff;
}

.slide--teal {
  background: linear-gradient(125deg, #56d3b4 0%, #23a98c 55%, #0e8168 100%);
  color: #fff;
}

.slide__tag {
  align-self: flex-start;
  margin-bottom: 14px;
  padding: 3px 12px;
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.22);
  font-size: 12px;
  letter-spacing: 1px;
}

.slide__title {
  margin: 0;
  font-size: 40px;
  font-weight: 700;
  letter-spacing: 2px;
}

.slide__desc {
  margin: 12px 0 0;
  font-size: 15px;
  color: rgba(255, 255, 255, 0.88);
}

.slide__cta {
  align-self: flex-start;
  margin-top: 26px;
  padding: 10px 28px;
  border: none;
  border-radius: 22px;
  background: #fff;
  color: #e8541f;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.14);
  transition: transform 0.16s ease;
}

.slide--violet .slide__cta {
  color: #5548cf;
}

.slide--teal .slide__cta {
  color: #0e8168;
}

.slide__cta:hover {
  transform: translateY(-2px);
}

.slide__orb {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.16);
  pointer-events: none;
}

.slide__orb--a {
  width: 260px;
  height: 260px;
  right: -50px;
  top: -70px;
}

.slide__orb--b {
  width: 160px;
  height: 160px;
  right: 130px;
  bottom: -70px;
  background: rgba(255, 255, 255, 0.1);
}

.dots {
  position: absolute;
  left: 56px;
  bottom: 22px;
  display: flex;
  gap: 7px;
}

.dots__item {
  width: 20px;
  height: 4px;
  padding: 0;
  border: none;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.42);
  cursor: pointer;
  transition: width 0.24s ease, background 0.24s ease;
}

.dots__item.is-active {
  width: 34px;
  background: #fff;
}

/* ================= 金刚区 ================= */
.quick {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 8px;
  margin-top: 16px;
  padding: 16px 12px;
  border-radius: 16px;
  background: #fff;
  box-shadow: var(--qx-shadow);
}

.quick__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9px;
  padding: 8px 4px;
  border: none;
  border-radius: 12px;
  background: transparent;
  cursor: pointer;
  transition: background 0.16s ease;
}

.quick__item:hover {
  background: #f8f9fc;
}

.quick__icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: var(--qx-brand-soft);
  color: var(--qx-brand-dark);
  font-size: 18px;
  font-weight: 600;
}

.quick__icon--all {
  background: var(--qx-grad);
  color: #fff;
}

.quick__icon--all svg {
  width: 20px;
  height: 20px;
}

.quick__label {
  font-size: 12.5px;
  color: var(--qx-ink-2);
  white-space: nowrap;
}

/* ================= 楼层 ================= */
.floor {
  margin-top: 30px;
}

.floor__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

.floor__head h2 {
  position: relative;
  margin: 0;
  padding-left: 14px;
  font-size: 21px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.floor__head h2::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 5px;
  height: 21px;
  border-radius: 3px;
  background: var(--qx-bar-grad);
}

.floor__more {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 4px 8px;
  border: none;
  background: none;
  color: var(--qx-ink-3);
  font-size: 13px;
  cursor: pointer;
  transition: color 0.16s ease;
}

.floor__more svg {
  width: 14px;
  height: 14px;
}

.floor__more:hover {
  color: var(--qx-brand-dark);
}

.floor__grid,
.grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

.skeleton {
  overflow: hidden;
  border-radius: var(--qx-radius);
  background: #fff;
  box-shadow: var(--qx-shadow);
}

/* ================= 结果页 ================= */
.result {
  padding-top: 26px;
}

.result__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 18px;
}

.result__head h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
}

.result__count {
  font-size: 13px;
  color: var(--qx-ink-3);
}

.result__back {
  margin-left: auto;
  padding: 7px 16px;
  border: 1.5px solid var(--qx-line);
  border-radius: 18px;
  background: #fff;
  color: var(--qx-ink-2);
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.16s ease, color 0.16s ease;
}

.result__back:hover {
  border-color: var(--qx-brand);
  color: var(--qx-brand-dark);
}

/* ================= 响应式 ================= */
@media (max-width: 1120px) {
  .floor__grid,
  .grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .quick {
    grid-template-columns: repeat(5, 1fr);
  }
}

@media (max-width: 900px) {
  .stage {
    grid-template-columns: 1fr;
  }

  .catnav {
    display: none;
  }

  .slides {
    height: 240px;
  }

  .slide {
    padding: 0 28px;
  }

  .slide__title {
    font-size: 28px;
  }

  .dots {
    left: 28px;
    bottom: 16px;
  }
}

@media (max-width: 720px) {
  .floor__grid,
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .quick {
    grid-template-columns: repeat(4, 1fr);
  }
}
</style>
