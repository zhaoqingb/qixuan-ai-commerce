import http from './http'

export interface ChatPayload {
  conversationId?: number
  message: string
}

export interface ChatResult {
  conversationId: number
  reply: string
}

/**
 * 大模型要先思考、再调工具、再组织回答，首包经常十几秒。
 * 全局 15s 超时不够用，这里单独放宽到 90s。
 */
export function chat(payload: ChatPayload): Promise<ChatResult> {
  return http.post<ChatResult>('/ai/chat', payload, { timeout: 90000 }).then((res) => res.data)
}

/** Agent 提议的写操作：还没执行，等真人点头 */
export interface ApprovalView {
  id: number
  approvalNo: string
  actionCode: string
  description: string
  status: string
  expiresAt: string
}

export interface ApprovalResult {
  approvalNo: string
  status: string
  message: string
  executedAt: string
}

export function listApprovals(): Promise<ApprovalView[]> {
  return http.get<ApprovalView[]>('/ai/approvals').then((res) => res.data)
}

/** 确认并执行（后端按审批单里固化的参数执行，前端不能改参数） */
export function approveAction(id: number): Promise<ApprovalResult> {
  return http.post<ApprovalResult>(`/ai/approvals/${id}/approve`, {}, { timeout: 30000 }).then((res) => res.data)
}

export function rejectAction(id: number): Promise<void> {
  return http.post(`/ai/approvals/${id}/reject`, {}).then(() => undefined)
}
