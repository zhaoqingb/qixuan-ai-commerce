
import http from './http'

import type { CategoryTreeNode } from '../types/category'

// 获取商品分类树
export async function getCategoryTree():
  Promise<CategoryTreeNode[]> {

  const response = await http.get<CategoryTreeNode[]>(
    '/categories/tree'
  )

  return response.data
}
