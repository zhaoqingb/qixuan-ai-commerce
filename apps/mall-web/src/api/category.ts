import http from './http'
import type { CategoryNode } from '@/types/mall'

/** 分类树，商城首页只取一级分类做筛选条 */
export async function fetchCategoryTree(): Promise<CategoryNode[]> {
  const { data } = await http.get<CategoryNode[]>('/categories/tree')
  return data
}
