<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { removeCartItem, updateCartItem } from '@/api/cart'
import { CURRENT_USER_ID } from '@/config'
import { useCartStore } from '@/stores/cart'
import type { CartItem } from '@/types/mall'
import { formatMoney, formatSpec } from '@/utils/spec'

const router = useRouter()
const cartStore = useCartStore()

const busyId = ref<number | null>(null)

const items = computed(() => cartStore.items)

const selectedItems = computed(() => items.value.filter((item) => item.selected === 1))

const allSelected = computed(
  () => items.value.length > 0 && items.value.every((item) => item.selected === 1),
)

const selectedQuantity = computed(() =>
  selectedItems.value.reduce((sum, item) => sum + item.quantity, 0),
)

const selectedAmount = computed(() =>
  selectedItems.value.reduce((sum, item) => sum + item.salePrice * item.quantity, 0),
)

function subtotal(item: CartItem) {
  return item.salePrice * item.quantity
}

function onImageError(event: Event) {
  const img = event.target as HTMLImageElement
  img.src = '/images/placeholder.svg'
}

async function toggleSelected(item: CartItem, checked: boolean) {
  const previous = item.selected
  item.selected = checked ? 1 : 0
  try {
    await updateCartItem(item.cartItemId, {
      userId: CURRENT_USER_ID,
      selected: checked ? 1 : 0,
    })
  } catch (error) {
    item.selected = previous
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

async function toggleAll(checked: boolean) {
  const target = checked ? 1 : 0
  const snapshot = items.value.map((item) => ({ item, selected: item.selected }))
  snapshot.forEach(({ item }) => {
    item.selected = target
  })
  try {
    await Promise.all(
      snapshot.map(({ item }) =>
        updateCartItem(item.cartItemId, { userId: CURRENT_USER_ID, selected: target }),
      ),
    )
  } catch (error) {
    snapshot.forEach(({ item, selected }) => {
      item.selected = selected
    })
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

async function changeQuantity(item: CartItem, value: number | undefined) {
  const next = Number(value)
  if (!Number.isFinite(next) || next < 1) return
  const previous = item.quantity
  if (next === previous) return

  item.quantity = next
  busyId.value = item.cartItemId
  try {
    await updateCartItem(item.cartItemId, { userId: CURRENT_USER_ID, quantity: next })
  } catch (error) {
    item.quantity = previous
    ElMessage.error(error instanceof Error ? error.message : '数量修改失败')
  } finally {
    busyId.value = null
  }
}

async function remove(item: CartItem) {
  busyId.value = item.cartItemId
  try {
    await removeCartItem(item.cartItemId, CURRENT_USER_ID)
    await cartStore.refresh()
    ElMessage.success('已移出购物车')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  } finally {
    busyId.value = null
  }
}

function goCheckout() {
  if (selectedItems.value.length === 0) {
    ElMessage.warning('请先勾选要结算的商品')
    return
  }
  router.push('/checkout')
}

onMounted(() => {
  cartStore.refresh().catch((error: unknown) => {
    ElMessage.error(error instanceof Error ? error.message : '购物车加载失败')
  })
})
</script>

<template>
  <div class="cart qx-container qx-page">
    <header class="cart__head">
      <h1 class="cart__title">我的购物车</h1>
      <span class="cart__count">{{ items.length }} 种商品 / {{ cartStore.totalQuantity }} 件</span>
    </header>

    <el-skeleton v-if="cartStore.loading && items.length === 0" :rows="6" animated />

    <el-empty v-else-if="items.length === 0" description="购物车还是空的，去挑几件喜欢的">
      <el-button type="primary" @click="router.push('/')">去逛逛</el-button>
    </el-empty>

    <template v-else>
      <section class="list qx-card">
        <div class="row row--head">
          <div class="col col--check">
            <el-checkbox
              :model-value="allSelected"
              @change="(value: unknown) => toggleAll(Boolean(value))"
            />
          </div>
          <div class="col col--goods">商品</div>
          <div class="col col--price">单价</div>
          <div class="col col--qty">数量</div>
          <div class="col col--sum">小计</div>
          <div class="col col--op">操作</div>
        </div>

        <div v-for="item in items" :key="item.cartItemId" class="row" :class="{ 'is-off': item.selected !== 1 }">
          <div class="col col--check">
            <el-checkbox
              :model-value="item.selected === 1"
              @change="(value: unknown) => toggleSelected(item, Boolean(value))"
            />
          </div>

          <div class="col col--goods">
            <RouterLink :to="`/product/${item.skuId}`" class="goods">
              <div class="goods__media">
                <img :src="item.imageUrl || '/images/placeholder.svg'" :alt="item.productTitle" @error="onImageError" />
              </div>
              <div class="goods__info">
                <p class="goods__title qx-ellipsis-2">{{ item.productTitle }}</p>
                <p class="goods__spec">{{ formatSpec(item.specJson) }}</p>
                <p v-if="item.availableQty < item.quantity" class="goods__warn">
                  库存仅剩 {{ item.availableQty }} 件
                </p>
              </div>
            </RouterLink>
          </div>

          <div class="col col--price">
            <span class="cell-label">单价</span>
            ¥{{ formatMoney(item.salePrice) }}
          </div>

          <div class="col col--qty">
            <span class="cell-label">数量</span>
            <el-input-number
              :model-value="item.quantity"
              :min="1"
              :max="Math.max(1, item.availableQty)"
              :disabled="busyId === item.cartItemId"
              size="small"
              controls-position="right"
              @change="(value: unknown) => changeQuantity(item, value as number | undefined)"
            />
          </div>

          <div class="col col--sum">
            <span class="cell-label">小计</span>
            <span class="qx-money">¥{{ formatMoney(subtotal(item)) }}</span>
          </div>

          <div class="col col--op">
            <button type="button" class="remove" :disabled="busyId === item.cartItemId" @click="remove(item)">
              移除
            </button>
          </div>
        </div>
      </section>

      <div class="bar qx-card">
        <div class="bar__left">
          <el-checkbox :model-value="allSelected" @change="(value: unknown) => toggleAll(Boolean(value))">
            全选
          </el-checkbox>
          <button
            v-if="selectedItems.length > 0"
            type="button"
            class="link"
            @click="toggleAll(false)"
          >
            取消选择
          </button>
        </div>

        <div class="bar__right">
          <div class="bar__stat">
            已选 <strong>{{ selectedQuantity }}</strong> 件
          </div>
          <div class="bar__total">
            合计
            <span class="qx-money bar__amount">
              <span class="qx-money__symbol">¥</span>{{ formatMoney(selectedAmount) }}
            </span>
          </div>
          <el-button
            type="primary"
            size="large"
            class="bar__btn"
            :disabled="selectedItems.length === 0"
            @click="goCheckout"
          >
            去结算
          </el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.cart__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}

.cart__title {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.cart__count {
  font-size: 13px;
  color: var(--qx-ink-3);
}

.list {
  overflow: hidden;
}

.row {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) 110px 132px 110px 72px;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--qx-line);
  transition: background 0.18s ease;
}

.row:last-child {
  border-bottom: none;
}

.row:not(.row--head):hover {
  background: #fbfcfe;
}

.row--head {
  padding-top: 12px;
  padding-bottom: 12px;
  background: #fafbfd;
  font-size: 12px;
  color: var(--qx-ink-3);
  letter-spacing: 0.5px;
}

.row.is-off .goods__title,
.row.is-off .col--price,
.row.is-off .col--sum {
  color: var(--qx-ink-3);
}

.col {
  min-width: 0;
  font-size: 14px;
}

.col--check {
  display: flex;
  justify-content: center;
}

.col--price {
  color: var(--qx-ink-2);
}

.col--sum .qx-money {
  font-size: 17px;
}

.col--op {
  text-align: right;
}

.cell-label {
  display: none;
}

.goods {
  display: flex;
  align-items: center;
  gap: 14px;
}

.goods__media {
  width: 76px;
  height: 76px;
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
  min-width: 0;
}

.goods__title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
  transition: color 0.18s ease;
}

.goods:hover .goods__title {
  color: var(--qx-brand);
}

.goods__spec {
  margin-top: 4px;
  font-size: 12px;
  color: var(--qx-ink-3);
}

.goods__warn {
  margin-top: 4px;
  font-size: 12px;
  color: #e6a23c;
}

.remove {
  border: none;
  background: none;
  color: var(--qx-ink-3);
  font-size: 13px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 6px;
  transition: all 0.18s ease;
}

.remove:hover:not(:disabled) {
  background: #fff1ee;
  color: #f56c6c;
}

.remove:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.link {
  border: none;
  background: none;
  color: var(--qx-brand);
  font-size: 13px;
  cursor: pointer;
}

/* ---------- 结算栏 ---------- */
.bar {
  position: sticky;
  bottom: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 20px;
  padding: 14px 20px;
  box-shadow: var(--qx-shadow-lg);
}

.bar__left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.bar__right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.bar__stat {
  font-size: 13px;
  color: var(--qx-ink-2);
}

.bar__stat strong {
  color: var(--qx-brand);
  font-size: 16px;
}

.bar__total {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 13px;
  color: var(--qx-ink-2);
}

.bar__amount {
  font-size: 26px;
  line-height: 1;
}

.bar__btn {
  width: 140px;
  height: 44px;
  border: none;
  border-radius: 22px;
  background: linear-gradient(135deg, #ff8a4c, #ff5a1f);
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 10px 20px rgba(255, 90, 31, 0.24);
}

@media (max-width: 900px) {
  .row {
    grid-template-columns: 36px minmax(0, 1fr);
    gap: 8px 12px;
    padding: 14px 16px;
  }

  .row--head {
    display: none;
  }

  .col--price,
  .col--qty,
  .col--sum,
  .col--op {
    grid-column: 2;
    display: flex;
    align-items: center;
    gap: 8px;
    text-align: left;
  }

  .col--op {
    justify-content: flex-end;
  }

  .cell-label {
    display: inline;
    width: 40px;
    flex-shrink: 0;
    font-size: 12px;
    color: var(--qx-ink-3);
  }

  .bar {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .bar__right {
    justify-content: space-between;
  }
}
</style>
