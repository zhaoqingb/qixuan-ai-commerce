import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { fetchCart } from '@/api/cart'
import { CURRENT_USER_ID } from '@/config'
import type { CartItem } from '@/types/mall'

/**
 * 购物车状态放在 store 里，是为了让顶栏角标和购物车页面共享同一份数据：
 * 页面里增删改之后调用 refresh()，顶栏数字立刻跟着变。
 */
export const useCartStore = defineStore('cart', () => {
  const items = ref<CartItem[]>([])
  const loading = ref(false)
  const loaded = ref(false)

  /** 顶栏角标：商品总件数，不是条目数 */
  const totalQuantity = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0))

  async function refresh(): Promise<void> {
    loading.value = true
    try {
      items.value = await fetchCart(CURRENT_USER_ID)
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  return { items, loading, loaded, totalQuantity, refresh }
})
