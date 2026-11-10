import http from "./http"

export interface ProductListItem{
    id: number
    name: string
    categoryName: string
    minPrice: number | null
    availableStock: number
    status:number
}

export async function getProducts(
    storeId:number
): Promise<ProductListItem[]>{
    const response = await http.get<ProductListItem[]>(
        "/merchant/products",
        {
            params: {
                storeId
            }
        }

    )
    return response.data
}

export interface ProductCreateSku{
    skuCode: string
    specs: Record<string, string>
    price: number
    stock: number
    
}

export interface ProductCreateRequest{
    storeId: number
    categoryId: number
    name:string
    description:string
    skus:ProductCreateSku[]
}

export interface ProductCreateResponse{
    spuId: number
    skuIds: number[]
    
}

export async function createProduct(
    request:ProductCreateRequest
): Promise<ProductCreateResponse>{
    const response = await http.post<ProductCreateResponse>(
        '/merchant/products',
        request
    )
    return response.data
}


export async function updateProductStatus(
  productId: number,
  storeId: number,
  status: 0 | 1
): Promise<void> {

  await http.patch(
    `/merchant/products/${productId}/status`,
    {
      storeId,
      status
    }
  )
}
