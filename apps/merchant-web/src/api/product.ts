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