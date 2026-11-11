import http from './http'
import type { ProductCard, ProductDetail } from '@/types/mall'

export interface ProductQuery {
  categoryId?: number
  keyword?: string
}

/** 在售商品列表，后端已按 ON_SALE + 店铺 ACTIVE 过滤 */
export async function fetchProducts(query: ProductQuery = {}): Promise<ProductCard[]> {
  const { data } = await http.get<ProductCard[]>('/products', { params: query })
  return data
}

/** 商品详情（含全部在售 SKU 与库存） */
export async function fetchProductDetail(id: number): Promise<ProductDetail> {
  const { data } = await http.get<ProductDetail>(`/products/${id}`)
  return data
}
