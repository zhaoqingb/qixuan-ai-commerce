import http, { clearToken, getToken } from './http'

export interface ChatResponse {
  conversationId: number
  reply: string
}

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

/** 发一条消息。conversationId 传 null 表示开新会话 */
export async function chat(message: string, conversationId?: number | null): Promise<ChatResponse> {
  const { data } = await http.post<ChatResponse>('/ai/chat', {
    message,
    conversationId: conversationId ?? null,
  })
  return data
}

/** 当前用户待处理的审批单 */
export async function fetchPendingApprovals(): Promise<ApprovalView[]> {
  const { data } = await http.get<ApprovalView[]>('/ai/approvals')
  return data
}

/**
 * 请求停止当前会话的 Agent 循环。
 * 注意：粒度在轮次之间，当前这次模型调用结束后才生效，不是立刻中断。
 */
export async function cancelChat(conversationId: number): Promise<void> {
  await http.post(`/ai/conversations/${conversationId}/cancel`)
}

export interface StreamHandlers {
  /** 会话建立 */
  onStart?: (conversationId: number) => void
  /** 文字增量，每收到一段就 append */
  onToken?: (text: string) => void
  /** 正在调用哪个工具 */
  onTool?: (name: string) => void
  /** 工具执行完，ok 表示成功 */
  onToolResult?: (name: string, ok: boolean) => void
  /** 最终完整回答（用它覆盖流式累积的内容，保证与库一致） */
  onReply?: (content: string, conversationId: number) => void
  onError?: (message: string) => void
}

/**
 * 流式对话（SSE over POST）。
 *
 * 为什么不用 EventSource：它只支持 GET，也不能带 Authorization 头。
 * 所以用 fetch + ReadableStream 自己解析 SSE 格式。
 */
export async function chatStream(
  message: string,
  conversationId: number | null,
  handlers: StreamHandlers,
): Promise<void> {
  const token = getToken()
  const response = await fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ message, conversationId }),
  })

  if (response.status === 401) {
    clearToken()
    window.location.href = '/login'
    return
  }
  if (!response.ok) {
    throw new Error(`请求失败（${response.status}）`)
  }
  if (!response.body) {
    throw new Error('当前浏览器不支持流式读取')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })

    // SSE 以空行分隔事件；最后一段可能是半截事件，留在 buffer 里等下一块
    const blocks = buffer.split('\n\n')
    buffer = blocks.pop() ?? ''

    for (const block of blocks) {
      const lines = block.split('\n')
      const eventLine = lines.find((line) => line.startsWith('event:'))
      const dataLine = lines.find((line) => line.startsWith('data:'))
      if (!eventLine || !dataLine) continue

      const event = eventLine.slice(6).trim()
      const raw = dataLine.slice(5).trim()
      if (!raw) continue

      let payload: Record<string, unknown>
      try {
        payload = JSON.parse(raw)
      } catch {
        continue
      }

      if (event === 'start') {
        handlers.onStart?.(payload.conversationId as number)
      } else if (event === 'token') {
        handlers.onToken?.(payload.text as string)
      } else if (event === 'tool') {
        handlers.onTool?.(payload.name as string)
      } else if (event === 'toolResult') {
        handlers.onToolResult?.(payload.name as string, payload.ok as boolean)
      } else if (event === 'reply') {
        handlers.onReply?.(payload.content as string, payload.conversationId as number)
      } else if (event === 'error') {
        handlers.onError?.((payload.message as string) ?? '处理失败')
      }
    }
  }
}

export async function approveApproval(id: number): Promise<ApprovalResult> {
  const { data } = await http.post<ApprovalResult>(`/ai/approvals/${id}/approve`)
  return data
}

export async function rejectApproval(id: number): Promise<void> {
  await http.post(`/ai/approvals/${id}/reject`)
}
