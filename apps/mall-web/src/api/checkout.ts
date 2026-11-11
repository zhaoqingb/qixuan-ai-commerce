import http from './http'
import type { Address, CheckoutPreview, SubmitOrderResult } from '@/types/mall'

/** 结算预览：按店铺分组算好金额与运费，只读不写 */
export async function fetchPreview(userId: number): Promise<CheckoutPreview> {
  const { data } = await http.get<CheckoutPreview>('/checkout/preview', { params: { userId } })
  return data
}

export async function fetchAddresses(userId: number): Promise<Address[]> {
  const { data } = await http.get<Address[]>('/checkout/addresses', { params: { userId } })
  return data
}

/**
 * 提交订单。
 * clientRequestId 是幂等键：同一个值重复提交，后端只会生成一张订单。
 */
export async function submitOrder(payload: {
  userId: number
  addressId: number
  clientRequestId: string
}): Promise<SubmitOrderResult> {
  const { data } = await http.post<SubmitOrderResult>('/checkout/submit', payload)
  return data
}
