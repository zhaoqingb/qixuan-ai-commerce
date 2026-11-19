import http from './http'

export interface SeckillActivity {
  id: number
  name: string
  startAt: string
  endAt: string
  status: string
  createdAt: string
  updatedAt: string
}

/** 预检查发现的一个问题，code 给程序用，message 给人看 */
export interface ActivityCheckIssue {
  code: string
  message: string
  itemId: number | null
}

export interface CreateActivityPayload {
  name: string
  startAt: string
  endAt: string
}

// 注意：路径不带 /api，http 实例的 baseURL 已经是 /api 了
export async function fetchActivities(): Promise<SeckillActivity[]> {
  const { data } = await http.get<SeckillActivity[]>('/admin/seckill/activities')
  return data
}

/** 返回空数组表示检查通过，可以上线 */
export async function precheckActivity(id: number): Promise<ActivityCheckIssue[]> {
  const { data } = await http.get<ActivityCheckIssue[]>(
    `/admin/seckill/activities/${id}/precheck`,
  )
  return data
}

export async function onlineActivity(id: number): Promise<SeckillActivity> {
  const { data } = await http.post<SeckillActivity>(`/admin/seckill/activities/${id}/online`)
  return data
}

export async function offlineActivity(id: number): Promise<SeckillActivity> {
  const { data } = await http.post<SeckillActivity>(`/admin/seckill/activities/${id}/offline`)
  return data
}

export async function createActivity(payload: CreateActivityPayload): Promise<SeckillActivity> {
  const { data } = await http.post<SeckillActivity>('/admin/seckill/activities', payload)
  return data
}
