<template>
  <div class="chat">
    <div v-if="approvals.length" class="approvals">
      <div class="approvals__title">
        <span class="approvals__dot"></span>
        待审批 {{ approvals.length }} 项 · AI 不会直接改数据，需要你确认
      </div>
      <div v-for="item in approvals" :key="item.id" class="approval">
        <div class="approval__main">
          <div class="approval__desc">{{ item.description }}</div>
          <div class="approval__meta">
            <code class="tag">{{ item.actionCode }}</code>
            <span>{{ item.approvalNo }}</span>
            <span>有效期至 {{ formatTime(item.expiresAt) }}</span>
          </div>
        </div>
        <div class="approval__actions">
          <el-button size="small" type="primary" :loading="acting === item.id" @click="onApprove(item)">
            通过
          </el-button>
          <el-button size="small" :loading="acting === item.id" @click="onReject(item)">
            拒绝
          </el-button>
        </div>
      </div>
    </div>

    <div ref="listRef" class="messages">
      <div v-if="!messages.length" class="empty">
        <div class="empty__title">试试这样问</div>
        <div class="empty__item" @click="quickAsk('帮我查一下我的订单')">
          帮我查一下我的订单
        </div>
        <div class="empty__item" @click="quickAsk('店铺里有哪些商品，库存还剩多少')">
          店铺里有哪些商品，库存还剩多少
        </div>
        <div class="empty__item" @click="quickAsk('把无线鼠标的库存补到 1000')">
          把无线鼠标的库存补到 1000
        </div>
      </div>

      <div
        v-for="(msg, index) in messages"
        :key="index"
        :class="['row', msg.role === 'user' ? 'row--user' : 'row--ai']"
      >
        <div v-if="msg.role === 'ai'" class="avatar avatar--ai">AI</div>
        <div class="row__body">
          <div class="row__meta">
            <span class="row__name">{{ msg.role === 'user' ? '我' : '助手' }}</span>
            <span v-if="msg.type === 'action'" class="tag tag--green">已执行</span>
            <span class="row__time">{{ msg.time }}</span>
          </div>

          <!-- 思考过程：默认收起，想看的自己点开 -->
          <div v-if="msg.role === 'ai' && (msg.tools?.length || msg.thinking)" class="thinking">
            <div class="thinking__toggle" @click="msg.showThinking = !msg.showThinking">
              <span :class="['thinking__arrow', { 'thinking__arrow--open': msg.showThinking }]">▸</span>
              <span>思考过程</span>
              <span v-if="msg.tools?.length" class="thinking__badge">{{ msg.tools.length }} 步</span>
            </div>
            <div v-if="msg.showThinking" class="thinking__body">
              <div v-for="(step, i) in msg.tools" :key="i" class="thinking__step">
                {{ i + 1 }}. {{ step }}
              </div>
              <div v-if="msg.thinking" class="thinking__text">{{ msg.thinking }}</div>
            </div>
          </div>

          <div :class="['bubble', msg.role === 'user' ? 'bubble--user' : 'bubble--ai']">
            {{ msg.content }}<span v-if="msg.streaming" class="cursor"></span>
            <span v-if="msg.streaming && !msg.content" class="thinking">{{ toolHint || '正在思考…' }}</span>
          </div>
        </div>
        <div v-if="msg.role === 'user'" class="avatar avatar--user">我</div>
      </div>

      <div v-if="toolHint && sending" class="tool-hint">
        <span class="tool-hint__dot"></span>
        {{ toolHint }}
      </div>
    </div>

    <div class="composer">
      <el-input
        v-model="draft"
        type="textarea"
        :rows="2"
        resize="none"
        placeholder="输入消息，Enter 发送，Shift+Enter 换行"
        @keydown.enter.exact.prevent="onSend"
      />
      <el-button v-if="sending" type="danger" plain @click="onCancel">停止</el-button>
      <el-button v-else type="primary" :disabled="!draft.trim()" @click="onSend">发送</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import {
  approveApproval,
  cancelChat,
  chatStream,
  fetchPendingApprovals,
  rejectApproval,
  type ApprovalView,
} from '../../api/ai'

interface ChatMessage {
  role: 'user' | 'ai'
  content: string
  time: string
  type?: 'action'
  /** 正在流式输出：显示光标 */
  streaming?: boolean
  /** 中间轮次的文字（用户想看再展开） */
  thinking?: string
  /** 这次回答里用到的工具，展示成"查库存 / 补货"这类人话 */
  tools?: string[]
  /** 思考过程是否展开 */
  showThinking?: boolean
}

const messages = ref<ChatMessage[]>([])
const draft = ref('')
const sending = ref(false)
const conversationId = ref<number | null>(null)
const approvals = ref<ApprovalView[]>([])
const acting = ref<number | null>(null)
const listRef = ref<HTMLElement | null>(null)
/** 当前正在执行的工具，显示成"正在查库存…" */
const toolHint = ref('')

/**
 * 会话缓存 key。
 * 现在存在 localStorage：切页面、刷新都不丢，但换设备/清缓存就没了。
 * 以后接后端历史接口（ai_message 表里已经有数据），这一层可以换成服务端加载。
 */
const STORAGE_KEY = 'merchant_ai_chat'

function loadHistory() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return
    const saved = JSON.parse(raw) as { messages?: ChatMessage[]; conversationId?: number }
    messages.value = saved.messages ?? []
    conversationId.value = saved.conversationId ?? null
  } catch {
    // 缓存损坏就当没有，不要因此让页面白屏
    localStorage.removeItem(STORAGE_KEY)
  }
}

function saveHistory() {
  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        // 只留最近 50 条：localStorage 有 5MB 上限，长对话全存会写失败
        messages: messages.value.slice(-50),
        conversationId: conversationId.value,
      }),
    )
  } catch {
    // 写不进去不影响当前会话，只是刷新后会丢
  }
}

function now(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function scrollToBottom() {
  await nextTick()
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight
  }
}

function formatTime(iso: string): string {
  if (!iso) return '-'
  return iso.slice(5, 16).replace('T', ' ')
}

function push(role: 'user' | 'ai', content: string, type?: 'action') {
  messages.value.push({ role, content, time: now(), type })
}

let lastApprovalCount = 0

async function loadApprovals() {
  try {
    const list = await fetchPendingApprovals()
    // 审批单变多了就弹通知，避免它悄悄出现在页面上方用户没看见
    if (list.length > lastApprovalCount) {
      ElNotification({
        title: '新的待审批操作',
        message: list[0]?.description ?? 'AI 提交了一个需要你确认的操作',
        type: 'warning',
        duration: 0, // 不自动消失，避免一眨眼就没了
      })
    }
    lastApprovalCount = list.length
    approvals.value = list
  } catch {
    // 拦截器已提示
  }
}

function quickAsk(text: string) {
  draft.value = text
  onSend()
}

async function onSend() {
  const text = draft.value.trim()
  if (!text || sending.value) return

  push('user', text)
  draft.value = ''
  sending.value = true

  // 先放一个空的助手消息，流式往里追加；
  // 一旦出现工具调用，就把已经吐出的文字认定为"思考过程"挪进折叠区
  messages.value.push({
    role: 'ai',
    content: '',
    time: now(),
    streaming: true,
    thinking: '',
    tools: [],
  })
  const slot = messages.value.length - 1
  const msg = messages.value[slot]
  toolHint.value = ''
  await scrollToBottom()

  try {
    await chatStream(text, conversationId.value, {
      onStart: (id) => {
        conversationId.value = id
      },
      onToken: (delta) => {
        msg.content += delta
        scrollToBottom()
      },
      onTool: (name) => {
        // 出现工具调用 = 刚才那段文字是推理，不是最终回答
        if (msg.content.trim()) {
          msg.thinking = `${msg.thinking ?? ''}${msg.content.trim()}\n`
          msg.content = ''
        }
        msg.tools = [...(msg.tools ?? []), describeTool(name)]
        toolHint.value = describeTool(name)
        scrollToBottom()
      },
      onToolResult: () => {
        toolHint.value = ''
      },
      onReply: (content, id) => {
        conversationId.value = id
        msg.content = content
        msg.streaming = false
        toolHint.value = ''
        scrollToBottom()
      },
      onError: (message) => {
        msg.content = message
        msg.streaming = false
      },
    })
  } catch (error) {
    msg.content = error instanceof Error ? `出错了：${error.message}` : '出错了，请稍后再试'
    msg.streaming = false
  } finally {
    sending.value = false
    toolHint.value = ''
    // 只在一次对话结束后写盘：流式过程中每个 token 都写会卡
    saveHistory()
    await loadApprovals()
  }
}

/** 工具名翻译成人话，显示在"正在思考"后面 */
function describeTool(name: string): string {
  const map: Record<string, string> = {
    list_my_products: '正在查看店铺商品…',
    get_sku_stock: '正在查库存…',
    list_my_orders: '正在查你的订单…',
    get_order_status: '正在查订单状态…',
    list_pending_shipments: '正在查待发货订单…',
    list_my_refunds: '正在查退款记录…',
    restock_sku: '正在提交补货申请…',
    ship_order: '正在提交发货申请…',
  }
  return map[name] ?? `正在执行 ${name}…`
}

async function onCancel() {
  if (!conversationId.value) {
    ElMessage.info('还没有正在执行的任务')
    return
  }
  try {
    await cancelChat(conversationId.value)
    // 措辞要如实：不是立刻停，是"下一步不再继续"
    ElMessage.info('已请求停止，当前这一步结束后就会中断')
  } catch {
    // 拦截器已提示
  }
}

async function onApprove(item: ApprovalView) {
  try {
    await ElMessageBox.confirm(`确认执行「${item.description}」吗？`, '确认执行', {
      type: 'warning',
      confirmButtonText: '确认执行',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  acting.value = item.id
  try {
    const result = await approveApproval(item.id)
    ElMessage.success(result.message || '已执行')
    push('ai', result.message, 'action')
    await loadApprovals()
    await scrollToBottom()
  } catch {
    // 拦截器已提示
  } finally {
    acting.value = null
  }
}

async function onReject(item: ApprovalView) {
  acting.value = item.id
  try {
    await rejectApproval(item.id)
    ElMessage.success('已拒绝')
    await loadApprovals()
  } catch {
    // 拦截器已提示
  } finally {
    acting.value = null
  }
}

onMounted(() => {
  loadHistory()
  loadApprovals()
})
</script>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 64px);
  padding: 16px 24px;
  gap: 12px;
}

/* ===== 待审批 ===== */
.approvals {
  border: 1px solid #f0d9b5;
  background: #fdf6ec;
  border-radius: 10px;
  padding: 14px 16px;
}

.approvals__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  color: #b88230;
  margin-bottom: 10px;
}

.approvals__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #e6a23c;
  flex-shrink: 0;
}

.approval {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 0;
  border-top: 1px dashed #f0d9b5;
}

.approval:first-of-type {
  border-top: none;
  padding-top: 0;
}

.approval__desc {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}

.approval__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.approval__actions {
  flex-shrink: 0;
}

.tag {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  background: #f4f4f5;
  color: #909399;
  font-family: ui-monospace, monospace;
}

.tag--green {
  background: #f0f9eb;
  color: #67c23a;
}

/* ===== 消息区 ===== */
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 8px 4px;
}

.empty {
  padding: 40px 0;
  text-align: center;
}

.empty__title {
  font-size: 13px;
  color: #909399;
  margin-bottom: 14px;
}

.empty__item {
  display: inline-block;
  margin: 4px;
  padding: 8px 14px;
  font-size: 13px;
  color: #606266;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.15s;
}

.empty__item:hover {
  border-color: #409eff;
  color: #409eff;
}

.row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 18px;
}

.row--user {
  justify-content: flex-end;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 500;
  flex-shrink: 0;
}

.avatar--ai {
  background: #18212f;
  color: #fff;
}

.avatar--user {
  background: #409eff;
  color: #fff;
}

.row__body {
  max-width: 68%;
  display: flex;
  flex-direction: column;
}

.row--user .row__body {
  align-items: flex-end;
}

.row__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  font-size: 11px;
  color: #b1b3b8;
}

.row__name {
  font-size: 12px;
  font-weight: 500;
  color: #606266;
}

.bubble {
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble--user {
  background: #409eff;
  color: #fff;
  border-top-right-radius: 2px;
}

.bubble--ai {
  background: #fff;
  color: #303133;
  border: 1px solid #ebeef5;
  border-top-left-radius: 2px;
}

.bubble--typing {
  color: #909399;
  font-style: italic;
}

/* 思考过程折叠块 */
.thinking {
  margin-bottom: 6px;
  max-width: 100%;
}

.thinking__toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px 2px 4px;
  font-size: 12px;
  color: #909399;
  cursor: pointer;
  border-radius: 4px;
  user-select: none;
  transition: background 0.15s;
}

.thinking__toggle:hover {
  background: #f5f7fa;
}

.thinking__arrow {
  display: inline-block;
  font-size: 10px;
  transition: transform 0.15s;
}

.thinking__arrow--open {
  transform: rotate(90deg);
}

.thinking__badge {
  padding: 0 6px;
  font-size: 11px;
  color: #409eff;
  background: #ecf5ff;
  border-radius: 8px;
}

.thinking__body {
  margin-top: 4px;
  padding: 8px 12px;
  border-left: 2px solid #e4e7ed;
  font-size: 12px;
  color: #909399;
  line-height: 1.8;
}

.thinking__step {
  color: #606266;
}

.thinking__text {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed #ebeef5;
  white-space: pre-wrap;
}

/* 流式输出时的闪烁光标 */
.cursor {
  display: inline-block;
  width: 2px;
  height: 14px;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: #409eff;
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.thinking {
  color: #909399;
}

/* 工具执行提示 */
.tool-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: -6px 0 12px 42px;
  font-size: 12px;
  color: #909399;
}

.tool-hint__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #409eff;
  animation: pulse 1.2s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
}

/* ===== 输入区 ===== */
.composer {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  padding-top: 4px;
}

.composer :deep(textarea) {
  font-family: inherit;
  font-size: 14px;
}
</style>
