import http from './http'
import type { CartItem } from '@/types/mall'

export async function fetchCart(userId: number): Promise<CartItem[]> {
  const { data } = await http.get<CartItem[]>('/cart/items', { params: { userId } })
  return data
}

export async function addToCart(payload: {
  userId: number
  skuId: number
  quantity: number
}): Promise<void> {
  await http.post('/cart/items', payload)
}

/** 只传需要修改的字段：quantity 改数量，selected 改勾选 */
export async function updateCartItem(
  id: number,
  payload: { userId: number; quantity?: number; selected?: number },
): Promise<void> {
  await http.patch(`/cart/items/${id}`, payload)
}

export async function removeCartItem(id: number, userId: number): Promise<void> {
  await http.delete(`/cart/items/${id}`, { params: { userId } })
}
