import http from './http'
import type { CancelOrderResult, OrderDetailRow, OrderItemRow, PayOrderResult } from '@/types/mall'

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

/**
 * 模拟支付。真实项目这里分两步：先「预下单」拿支付参数，再跳收银台等异步回调。
 * 作品集把两步合成一步，重点验证「状态机流转 + 库存 locked→sold」。
 * 接口幂等：重复调用返回同一份已支付结果，不会重复扣库存。
 */
export async function payOrder(orderNo: string, userId: number): Promise<PayOrderResult> {
  const { data } = await http.post<PayOrderResult>(`/orders/${orderNo}/pay`, null, {
    params: { userId },
  })
  return data
}

/**
 * 取消订单：只有待付款能取消，取消后库存从 locked 退回 available。
 * 和「超时未付款自动关单」共用后端同一段逻辑，这里只是用户主动触发的那条路径。
 */
export async function cancelOrder(orderNo: string, userId: number): Promise<CancelOrderResult> {
  const { data } = await http.post<CancelOrderResult>(`/orders/${orderNo}/cancel`, null, {
    params: { userId },
  })
  return data
}
