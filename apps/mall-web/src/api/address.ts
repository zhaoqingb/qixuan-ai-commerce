import http from './http'
import type { Address } from '@/types/mall'

export interface AddressCreateRequest {
  receiverName: string
  receiverPhone: string
  province: string
  city: string
  district: string
  detailAddress: string
  isDefault: boolean
}

/** 当前用户的全部地址，默认地址排在最前 */
export async function fetchAddressList(): Promise<Address[]> {
  const { data } = await http.get<Address[]>('/addresses')
  return data
}

/** 新增地址。用户的第一条地址后端会强制设为默认 */
export async function createAddress(payload: AddressCreateRequest): Promise<Address> {
  const { data } = await http.post<Address>('/addresses', payload)
  return data
}

export async function deleteAddress(addressId: number): Promise<void> {
  await http.delete(`/addresses/${addressId}`)
}

export async function setDefaultAddress(addressId: number): Promise<void> {
  await http.post(`/addresses/${addressId}/default`)
}
