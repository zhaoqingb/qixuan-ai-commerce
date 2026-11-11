import http from './http'
import type { OrderDetailRow, OrderItemRow } from '@/types/mall'

/**
 * 我的订单。
 * 返回的是「商品行」而不是「订单行」：一个订单里有几件商品就有几条记录，
 * 前端在 views/OrderListView.vue 里按 tradeOrderId 分组。
 */
export async function fetchOrders(userId: number): Promise<OrderItemRow[]> {
  const { data } = await http.get<OrderItemRow[]>('/orders', { params: { userId } })
  return data
}

/**
 * 订单详情。用订单号而不是 id 查，因为订单号是唯一的业务主键，
 * 分享链接时也不会暴露自增 id。查不到（或不属于该用户）返回空数组。
 */
export async function fetchOrderDetail(orderNo: string, userId: number): Promise<OrderDetailRow[]> {
  const { data } = await http.get<OrderDetailRow[]>(`/orders/${orderNo}`, { params: { userId } })
  return data
}
