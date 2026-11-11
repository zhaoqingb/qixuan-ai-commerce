<script setup lang="ts">
import { computed, ref } from 'vue'

import type { ProductCard } from '@/types/mall'
import { formatMoney } from '@/utils/spec'

const props = defineProps<{ product: ProductCard }>()

const imageFailed = ref(false)

const cover = computed(() => {
  if (imageFailed.value || !props.product.mainImageUrl) return '/images/placeholder.svg'
  return props.product.mainImageUrl
})

const soldText = computed(() => {
  const sold = props.product.soldCount ?? 0
  if (sold >= 1000) return `已售 ${(sold / 1000).toFixed(1)}k`
  return `已售 ${sold}`
})
</script>

<template>
  <RouterLink :to="`/product/${product.id}`" class="card">
    <div class="card__media">
      <img :src="cover" :alt="product.title" loading="lazy" @error="imageFailed = true" />
      <span class="card__sold">{{ soldText }}</span>
    </div>

    <div class="card__body">
      <h3 class="card__title qx-ellipsis-2">{{ product.title }}</h3>
      <p v-if="product.subtitle" class="card__subtitle">{{ product.subtitle }}</p>

      <div class="card__price">
        <span class="qx-money">
          <span class="qx-money__symbol">¥</span>{{ formatMoney(product.minPrice) }}
        </span>
        <span class="card__price-from">起</span>
        <span class="card__stock">库存 {{ product.availableStock }}</span>
      </div>

      <div class="card__foot">
        <span class="card__store">{{ product.storeName }}</span>
        <span class="card__cat">{{ product.categoryName }}</span>
      </div>
    </div>
  </RouterLink>
</template>

<style scoped>
.card {
  display: flex;
  flex-direction: column;
  background: var(--qx-card);
  border-radius: var(--qx-radius);
  overflow: hidden;
  box-shadow: var(--qx-shadow);
  transition: transform 0.22s ease, box-shadow 0.22s ease;
}

.card:hover {
  transform: translateY(-4px);
  box-shadow: var(--qx-shadow-lg);
}

.card__media {
  position: relative;
  aspect-ratio: 1 / 1;
  background: linear-gradient(160deg, #f8f9fb, #eef1f6);
  overflow: hidden;
}

.card__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.4s ease;
}

.card:hover .card__media img {
  transform: scale(1.05);
}

.card__sold {
  position: absolute;
  left: 12px;
  bottom: 12px;
  padding: 2px 9px;
  border-radius: 10px;
  background: rgba(31, 36, 48, 0.55);
  color: #fff;
  font-size: 11px;
  backdrop-filter: blur(4px);
}

.card__body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 14px 15px;
}

.card__title {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
  min-height: 43px;
  color: var(--qx-ink);
}

.card__subtitle {
  font-size: 12px;
  color: var(--qx-ink-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card__price {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 2px;
}

.card__price .qx-money {
  font-size: 21px;
}

.card__price-from {
  font-size: 12px;
  color: var(--qx-ink-3);
}

.card__stock {
  margin-left: auto;
  font-size: 11px;
  color: var(--qx-ink-3);
}

.card__foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  padding-top: 10px;
  border-top: 1px dashed var(--qx-line);
}

.card__store {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: var(--qx-ink-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card__cat {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: 8px;
  background: var(--qx-brand-soft);
  color: var(--qx-brand-dark);
  font-size: 11px;
}
</style>
