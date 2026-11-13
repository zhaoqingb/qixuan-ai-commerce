/** 买家端（C 端）接口的数据结构，与后端 DTO 一一对应 */

export interface ProductCard {
  id: number
  title: string
  subtitle: string | null
  mainImageUrl: string | null
  minPrice: number
  storeName: string
  categoryName: string
  availableStock: number
  soldCount: number
}

export interface SkuView {
  skuId: number
  skuCode: string
  specJson: string | null
  salePrice: number
  listPrice: number | null
  availableQty: number
}

export interface ProductDetail {
  id: number
  title: string
  subtitle: string | null
  mainImageUrl: string | null
  description: string | null
  storeId: number
  storeName: string
  categoryId: number
  skus: SkuView[]
}

export interface CategoryNode {
  id: number
  parentId: number | null
  name: string
  sortOrder: number
  children: CategoryNode[]
}

export interface CartItem {
  cartItemId: number
  skuId: number
  quantity: number
  selected: number
  skuCode: string
  specJson: string | null
  salePrice: number
  productTitle: string
  imageUrl: string | null
  availableQty: number
}

export interface Address {
  id: number
  userId: number
  receiverName: string
  receiverPhone: string
  province: string
  city: string
  district: string
  detailAddress: string
  isDefault: number
}

export interface CheckoutItem {
  cartItemId: number
  skuId: number
  spuId: number
  productTitle: string
  imageUrl: string | null
  specJson: string | null
  salePrice: number
  quantity: number
  availableQty: number
  stockEnough: boolean
  subtotal: number
}

export interface StoreGroup {
  storeId: number
  storeName: string
  goodsAmount: number
  freightAmount: number
  payableAmount: number
  items: CheckoutItem[]
}

export interface CheckoutPreview {
  storeGroups: StoreGroup[]
  goodsAmount: number
  freightAmount: number
  payableAmount: number
  totalQuantity: number
}

/**
 * 订单列表的行数据：后端一条 SQL 把「订单 → 子单 → 明细」拍平，
 * 同一个订单会出现多行（每个商品一行），前端按 tradeOrderId 分组。
 */
export interface OrderItemRow {
  orderItemId: number
  tradeOrderId: number
  orderNo: string
  status: string
  createdAt: string
  goodsAmount: number
  freightAmount: number
  payableAmount: number
  storeId: number
  storeName: string
  spuId: number
  skuId: number
  productTitle: string
  imageUrl: string | null
  specJson: string | null
  unitPrice: number
  quantity: number
  itemPayableAmount: number
}

/**
 * 订单详情的行数据：同样是一条 SQL 拍平，
 * 一个子单（store_order）里的每件商品一行，前端按 subOrderNo 分组。
 */
export interface OrderDetailRow {
  orderItemId: number
  tradeOrderId: number
  orderNo: string
  status: string
  createdAt: string
  goodsAmount: number
  freightAmount: number
  discountAmount: number
  payableAmount: number
  addressSnapshot: string
  subOrderNo: string
  subFreightAmount: number
  subPayableAmount: number
  storeId: number
  storeName: string
  spuId: number
  skuId: number
  productTitle: string
  imageUrl: string | null
  specJson: string | null
  unitPrice: number
  quantity: number
  itemPayableAmount: number
}

export interface SubmitOrderResult {
  tradeOrderId: number
  orderNo: string
  payableAmount: number
  status: string
  storeOrderCount: number
  itemCount: number
  duplicated: boolean
}

/** 支付结果：接口幂等，重复调用返回同一份已支付结果 */
export interface PayOrderResult {
  orderNo: string
  status: string
  payableAmount: number
  paidAt: string
}

/** 取消结果：超时自动关单和用户主动取消共用同一段后端逻辑 */
export interface CancelOrderResult {
  orderNo: string
  status: string
  closedAt: string
}
