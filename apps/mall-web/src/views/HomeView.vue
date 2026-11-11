<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
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
    products.value = await fetchProducts({
      categoryId: activeCategoryId.value ?? undefined,
      keyword: activeKeyword.value || undefined,
    })
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

onMounted(loadCategories)
watch([activeCategoryId, activeKeyword], loadProducts, { immediate: true })
</script>

<template>
  <div class="home">
    <section class="hero qx-container">
      <div class="hero__inner">
        <div class="hero__copy">
          <span class="hero__tag">多商户 · 正品直供</span>
          <h1>认真挑选的每一件好物</h1>
          <p>数码、家居、美妆、服饰一站备齐，跨店下单统一结算，满 99 元包邮。</p>
          <button type="button" class="hero__cta" @click="selectCategory(null)">立即选购</button>
        </div>
        <div class="hero__art" aria-hidden="true">
          <span class="orb orb--lg"></span>
          <span class="orb orb--md"></span>
          <span class="orb orb--sm"></span>
          <span class="ring"></span>
        </div>
      </div>
    </section>

    <div class="qx-container">
      <section class="filters qx-card">
        <div class="filters__scroll">
          <button
            type="button"
            class="chip"
            :class="{ 'is-active': activeCategoryId === null }"
            @click="selectCategory(null)"
          >
            全部商品
          </button>
          <button
            v-for="item in categories"
            :key="item.id"
            type="button"
            class="chip"
            :class="{ 'is-active': activeCategoryId === item.id }"
            @click="selectCategory(item.id)"
          >
            {{ item.name }}
          </button>
        </div>

        <div class="filters__meta">
          <template v-if="activeKeyword">
            <span class="filters__keyword">“{{ activeKeyword }}”</span>
            的搜索结果
            <button type="button" class="link" @click="clearFilters">清除</button>
          </template>
          <template v-else>
            <span>{{ activeCategoryName || '全部商品' }}</span>
            <span class="filters__count">共 {{ products.length }} 件</span>
          </template>
        </div>
      </section>

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
  </div>
</template>

<style scoped>
.home {
  padding-bottom: 64px;
}

/* ---------- Hero ---------- */
.hero {
  padding-top: 24px;
}

.hero__inner {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 208px;
  padding: 34px 44px;
  border-radius: 20px;
  overflow: hidden;
  background: linear-gradient(120deg, #fff4ee 0%, #ffe9df 46%, #ffe2d4 100%);
  box-shadow: 0 10px 30px rgba(255, 107, 53, 0.1);
}

.hero__copy {
  position: relative;
  z-index: 1;
  max-width: 560px;
}

.hero__tag {
  display: inline-block;
  margin-bottom: 12px;
  padding: 3px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.75);
  color: var(--qx-brand-dark);
  font-size: 12px;
  letter-spacing: 0.6px;
}

.hero__copy h1 {
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: #33251d;
}

.hero__copy p {
  margin-top: 10px;
  font-size: 14px;
  color: #8a6a5a;
  line-height: 1.7;
}

.hero__cta {
  margin-top: 18px;
  padding: 9px 26px;
  border: none;
  border-radius: 22px;
  background: linear-gradient(135deg, #ff8a4c, #ff5a1f);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 10px 22px rgba(255, 90, 31, 0.28);
  transition: transform 0.18s ease;
}

.hero__cta:hover {
  transform: translateY(-1px);
}

.hero__art {
  position: absolute;
  right: 30px;
  top: 50%;
  width: 340px;
  height: 208px;
  transform: translateY(-50%);
  pointer-events: none;
}

.orb {
  position: absolute;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(255, 138, 76, 0.9), rgba(255, 90, 31, 0.75));
}

.orb--lg {
  width: 168px;
  height: 168px;
  right: 96px;
  top: 12px;
  filter: blur(0.5px);
  opacity: 0.92;
}

.orb--md {
  width: 84px;
  height: 84px;
  right: 20px;
  top: 96px;
  opacity: 0.75;
}

.orb--sm {
  width: 44px;
  height: 44px;
  right: 236px;
  top: 150px;
  opacity: 0.6;
}

.ring {
  position: absolute;
  width: 220px;
  height: 220px;
  right: 60px;
  top: -6px;
  border: 2px dashed rgba(255, 255, 255, 0.75);
  border-radius: 50%;
}

/* ---------- 筛选条 ---------- */
.filters {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-top: 20px;
  padding: 12px 18px;
  position: sticky;
  top: 68px;
  z-index: 20;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
}

.filters__scroll {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 2px;
  scrollbar-width: none;
}

.filters__scroll::-webkit-scrollbar {
  display: none;
}

.chip {
  flex-shrink: 0;
  padding: 6px 16px;
  border: 1px solid var(--qx-line);
  border-radius: 18px;
  background: #fff;
  color: var(--qx-ink-2);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.18s ease;
}

.chip:hover {
  border-color: var(--qx-brand);
  color: var(--qx-brand-dark);
}

.chip.is-active {
  border-color: transparent;
  background: linear-gradient(135deg, #ff8a4c, #ff5a1f);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 6px 14px rgba(255, 90, 31, 0.24);
}

.filters__meta {
  margin-left: auto;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--qx-ink-2);
  white-space: nowrap;
}

.filters__keyword {
  color: var(--qx-brand-dark);
  font-weight: 600;
}

.filters__count {
  margin-left: 8px;
  color: var(--qx-ink-3);
}

.link {
  border: none;
  background: none;
  color: var(--qx-brand);
  font-size: 13px;
  cursor: pointer;
  padding: 0 0 0 6px;
}

/* ---------- 商品网格 ---------- */
.grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
  margin-top: 20px;
}

.skeleton {
  background: var(--qx-card);
  border-radius: var(--qx-radius);
  overflow: hidden;
  box-shadow: var(--qx-shadow);
}

@media (max-width: 1080px) {
  .grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .hero__art {
    opacity: 0.45;
  }
}

@media (max-width: 780px) {
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hero__inner {
    padding: 26px;
  }

  .hero__copy h1 {
    font-size: 22px;
  }

  .hero__art {
    display: none;
  }

  .filters {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .filters__meta {
    margin-left: 0;
  }
}
</style>
