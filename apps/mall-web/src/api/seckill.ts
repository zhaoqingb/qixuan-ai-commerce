import http from './http'

/** 秒杀商品卡片，与后端 SeckillItemView 一一对应 */
export interface SeckillItem {
  itemId: number
  activityId: number
  spuId: number
  skuId: number
  title: string
  subtitle: string | null
  mainImageUrl: string | null
  originalPrice: number
  seckillPrice: number
  stock: number
  soldCount: number
  startAt: string
  endAt: string
}

/** 抢购结果：QUEUING 表示名额已锁定、订单还在异步落库 */
export interface SeckillResult {
  orderNo: string | null
  status: string
  message: string
}

export async function fetchSeckillItems(): Promise<SeckillItem[]> {
  const { data } = await http.get<SeckillItem[]>('/seckill/items')
  return data
}

export async function buySeckill(itemId: number): Promise<SeckillResult> {
  const { data } = await http.post<SeckillResult>(`/seckill/items/${itemId}/buy`)
  return data
}

export async function fetchSeckillOrder(orderNo: string): Promise<SeckillResult> {
  const { data } = await http.get<SeckillResult>(`/seckill/orders/${orderNo}`)
  return data
}

/** 取消秒杀订单，名额会立即还回库存池（Redis + DB 同时释放） */
export async function cancelSeckill(orderNo: string): Promise<SeckillResult> {
  const { data } = await http.post<SeckillResult>(`/seckill/orders/${orderNo}/cancel`)
  return data
}

/** 我的秒杀记录。秒杀订单还没接进 trade_order，所以「我的订单」页看不到，只能从这里查 */
export interface SeckillOrderRecord {
  orderNo: string
  itemId: number
  status: string
  amount: number
  createdAt: string
  tradeOrderId: number | null
}

export async function fetchMySeckillOrders(): Promise<SeckillOrderRecord[]> {
  const { data } = await http.get<SeckillOrderRecord[]>('/seckill/orders')
  return data
}

/**
 * 把秒杀订单转成正式订单，返回的 orderNo 是 trade_order 的单号，用来跳支付页。
 * 幂等：重复调用返回同一张订单。
 */
export async function convertSeckillOrder(seckillOrderNo: string): Promise<SeckillResult> {
  const { data } = await http.post<SeckillResult>(`/seckill/orders/${seckillOrderNo}/convert`)
  return data
}
