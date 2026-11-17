<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import {
  approveAction,
  chat,
  listApprovals,
  rejectAction,
  type ApprovalView,
} from '@/api/ai'
import { useAuthStore } from '@/stores/auth'

interface ChatBubble {
  role: 'user' | 'assistant' | 'notice'
  content: string
}

const QUICK_ASKS = ['我最近的订单到哪了？', '有哪些耳机推荐？', '退款怎么申请？']

const router = useRouter()
const authStore = useAuthStore()

const open = ref(false)
const draft = ref('')
const pending = ref(false)
const messages = ref<ChatBubble[]>([])
const conversationId = ref<number | null>(null)
const bodyEl = ref<HTMLElement | null>(null)

/** Agent 提议、但还没执行的写操作 */
const approvals = ref<ApprovalView[]>([])
/** 正在执行的那条（防重复点击，同时给按钮做 loading） */
const deciding = ref<number | null>(null)

function convKey(username: string) {
  return `qx_ai_conv_${username}`
}

// 换账号必须换会话，否则 A 的对话会喂给 B 的提问
watch(
  () => authStore.username,
  (name) => {
    messages.value = []
    conversationId.value = null
    if (!name) return
    const saved = localStorage.getItem(convKey(name))
    conversationId.value = saved ? Number(saved) : null
  },
  { immediate: true },
)

function scrollToBottom() {
  nextTick(() => {
    if (bodyEl.value) bodyEl.value.scrollTop = bodyEl.value.scrollHeight
  })
}

/**
 * 拉取「待确认」列表。
 * 模型可能刚生成了新的审批单，所以每轮对话结束、每次打开面板都要刷一次。
 */
async function refreshApprovals() {
  if (!authStore.isLoggedIn) {
    approvals.value = []
    return
  }
  try {
    approvals.value = await listApprovals()
  } catch {
    // 拉不到就算了，不能因为侧栏失效把对话也搞崩
  }
}

// 打开面板时同步一次：可能上次留下的单子还在
watch(open, (value) => {
  if (value) refreshApprovals()
})

async function confirm(item: ApprovalView) {
  if (deciding.value !== null) return
  deciding.value = item.id
  try {
    const result = await approveAction(item.id)
    approvals.value = approvals.value.filter((a) => a.id !== item.id)
    messages.value.push({ role: 'assistant', content: result.message })
    scrollToBottom()
  } catch (error) {
    messages.value.push({ role: 'notice', content: (error as Error).message })
    // 可能是已过期或被别处处理过，刷新让界面回到真实状态
    refreshApprovals()
  } finally {
    deciding.value = null
  }
}

async function cancel(item: ApprovalView) {
  if (deciding.value !== null) return
  deciding.value = item.id
  try {
    await rejectAction(item.id)
    approvals.value = approvals.value.filter((a) => a.id !== item.id)
    messages.value.push({ role: 'notice', content: `已取消：${item.description}` })
  } catch (error) {
    messages.value.push({ role: 'notice', content: (error as Error).message })
    refreshApprovals()
  } finally {
    deciding.value = null
  }
}

async function send(text: string) {
  const content = text.trim()
  if (!content || pending.value) return

  if (!authStore.isLoggedIn) {
    messages.value.push({ role: 'notice', content: '请先登录，我才能查你的订单和收货地址' })
    scrollToBottom()
    return
  }

  messages.value.push({ role: 'user', content })
  draft.value = ''
  pending.value = true
  scrollToBottom()

  try {
    const result = await chat({
      conversationId: conversationId.value ?? undefined,
      message: content,
    })
    conversationId.value = result.conversationId
    localStorage.setItem(convKey(authStore.username), String(result.conversationId))
    messages.value.push({ role: 'assistant', content: result.reply })
  } catch (error) {
    // 模型侧报错也要说出来，否则用户对着空气等
    messages.value.push({ role: 'notice', content: (error as Error).message })
  } finally {
    pending.value = false
    scrollToBottom()
    // 这一轮模型可能刚提了一张待确认单，刷出来让用户能立刻点
    refreshApprovals()
  }
}

function resetConversation() {
  conversationId.value = null
  messages.value = []
  localStorage.removeItem(convKey(authStore.username))
}

function goLogin() {
  open.value = false
  router.push({ path: '/login', query: { redirect: window.location.pathname } })
}
</script>

<template>
  <div class="svc">
    <Transition name="svc-panel">
      <section v-if="open" class="svc__panel">
        <header class="svc__head">
          <div class="svc__ident">
            <span class="svc__avatar">栖</span>
            <div>
              <strong>栖选智能客服</strong>
              <small><i class="svc__dot" />在线 · 可查订单与库存</small>
            </div>
          </div>
          <div class="svc__tools">
            <button type="button" title="开启新对话" @click="resetConversation">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M12 5v14M5 12h14"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                />
              </svg>
            </button>
            <button type="button" title="收起" @click="open = false">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M7 7l10 10M17 7L7 17"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                />
              </svg>
            </button>
          </div>
        </header>

        <div ref="bodyEl" class="svc__body">
          <div v-if="messages.length === 0" class="svc__intro">
            <p class="svc__intro-title">你好，我是栖选智能客服 👋</p>
            <p class="svc__intro-text">
              订单进度、物流、库存、退款申请，都可以直接问我。我会实时查数据库，不编答案。
            </p>
            <div class="svc__quick">
              <button
                v-for="item in QUICK_ASKS"
                :key="item"
                type="button"
                @click="send(item)"
              >
                {{ item }}
              </button>
            </div>
          </div>

          <div
            v-for="(item, index) in messages"
            :key="index"
            class="svc__row"
            :class="`is-${item.role}`"
          >
            <div v-if="item.role === 'notice'" class="svc__notice">{{ item.content }}</div>
            <div v-else class="svc__bubble">{{ item.content }}</div>
          </div>

          <div v-if="pending" class="svc__row is-assistant">
            <div class="svc__bubble svc__bubble--typing">
              <i /><i /><i />
            </div>
          </div>

          <div v-if="!authStore.isLoggedIn" class="svc__login">
            <p>登录后才能查询订单、地址等信息</p>
            <button type="button" @click="goLogin">去登录</button>
          </div>
        </div>

        <div v-if="approvals.length" class="svc__approvals">
          <p class="svc__approvals-head">
            <i class="svc__approvals-dot" />
            待你确认 · {{ approvals.length }}
          </p>

          <article v-for="item in approvals" :key="item.id" class="svc__approval">
            <div class="svc__approval-body">
              <span class="svc__approval-code">{{ item.actionCode }}</span>
              <p class="svc__approval-desc">{{ item.description }}</p>
              <p class="svc__approval-note">确认后立即执行，10 分钟内有效</p>
            </div>
            <div class="svc__approval-actions">
              <button
                type="button"
                class="svc__approval-cancel"
                :disabled="deciding !== null"
                @click="cancel(item)"
              >
                取消
              </button>
              <button
                type="button"
                class="svc__approval-ok"
                :disabled="deciding !== null"
                @click="confirm(item)"
              >
                {{ deciding === item.id ? '执行中…' : '确认执行' }}
              </button>
            </div>
          </article>
        </div>

        <form class="svc__composer" @submit.prevent="send(draft)">
          <input
            v-model="draft"
            type="text"
            maxlength="500"
            placeholder="输入问题，回车发送"
            :disabled="pending"
          />
          <button type="submit" :disabled="pending || !draft.trim()">发送</button>
        </form>
      </section>
    </Transition>

    <button
      type="button"
      class="svc__fab"
      :class="{ 'is-open': open }"
      aria-label="智能客服"
      @click="open = !open"
    >
      <svg v-if="!open" viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M20 12.5c0 3.6-3.6 6.5-8 6.5-1 0-2-.15-2.9-.42L5 20l1.1-3.2C4.8 15.5 4 14.1 4 12.5 4 8.9 7.6 6 12 6s8 2.9 8 6.5Z"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linejoin="round"
        />
        <circle cx="9" cy="12.4" r="1.1" fill="currentColor" />
        <circle cx="12" cy="12.4" r="1.1" fill="currentColor" />
        <circle cx="15" cy="12.4" r="1.1" fill="currentColor" />
      </svg>
      <svg v-else viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M7 7l10 10M17 7L7 17"
          fill="none"
          stroke="currentColor"
          stroke-width="2.2"
          stroke-linecap="round"
        />
      </svg>
    </button>
  </div>
</template>

<style scoped>
.svc {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 900;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.svc__fab {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: var(--qx-grad);
  color: #fff;
  cursor: pointer;
  box-shadow: 0 10px 26px rgba(255, 90, 31, 0.4);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.svc__fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 30px rgba(255, 90, 31, 0.46);
}

.svc__fab svg {
  width: 26px;
  height: 26px;
}

.svc__panel {
  display: flex;
  flex-direction: column;
  width: 380px;
  height: 560px;
  max-height: calc(100vh - 140px);
  border-radius: 18px;
  background: var(--qx-card);
  box-shadow: var(--qx-shadow-lg);
  overflow: hidden;
}

.svc-panel-enter-active,
.svc-panel-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
  transform-origin: bottom right;
}

.svc-panel-enter-from,
.svc-panel-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.97);
}

.svc__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  background: var(--qx-grad);
  color: #fff;
}

.svc__ident {
  display: flex;
  align-items: center;
  gap: 10px;
}

.svc__avatar {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.22);
  font-size: 16px;
  font-weight: 700;
}

.svc__ident strong {
  display: block;
  font-size: 14px;
}

.svc__ident small {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 2px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.85);
}

.svc__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #8ef0b6;
}

.svc__tools {
  display: flex;
  gap: 2px;
}

.svc__tools button {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: rgba(255, 255, 255, 0.9);
  cursor: pointer;
  transition: background 0.16s ease;
}

.svc__tools button:hover {
  background: rgba(255, 255, 255, 0.2);
}

.svc__tools svg {
  width: 16px;
  height: 16px;
}

.svc__body {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  background: var(--qx-bg);
}

.svc__intro {
  padding: 4px 2px 0;
}

.svc__intro-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--qx-ink);
}

.svc__intro-text {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--qx-ink-2);
}

.svc__quick {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 16px;
}

.svc__quick button {
  padding: 10px 12px;
  border: 1px solid var(--qx-line);
  border-radius: 10px;
  background: #fff;
  color: var(--qx-ink-2);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, color 0.16s ease, background 0.16s ease;
}

.svc__quick button:hover {
  border-color: var(--qx-brand);
  background: var(--qx-brand-soft);
  color: var(--qx-brand-dark);
}

.svc__row {
  display: flex;
  margin-bottom: 10px;
}

.svc__row.is-user {
  justify-content: flex-end;
}

.svc__bubble {
  max-width: 82%;
  padding: 10px 13px;
  border-radius: 14px;
  font-size: 13.5px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.svc__row.is-assistant .svc__bubble {
  background: #fff;
  color: var(--qx-ink);
  border-bottom-left-radius: 5px;
  box-shadow: 0 2px 8px rgba(31, 36, 48, 0.05);
}

.svc__row.is-user .svc__bubble {
  background: var(--qx-grad);
  color: #fff;
  border-bottom-right-radius: 5px;
}

.svc__notice {
  width: 100%;
  padding: 8px 12px;
  border-radius: 10px;
  background: #fff6e8;
  color: #a5670f;
  font-size: 12.5px;
  line-height: 1.6;
  text-align: center;
}

.svc__bubble--typing {
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 13px 15px;
}

.svc__bubble--typing i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--qx-ink-3);
  animation: qx-svc-blink 1.1s infinite;
}

.svc__bubble--typing i:nth-child(2) {
  animation-delay: 0.16s;
}

.svc__bubble--typing i:nth-child(3) {
  animation-delay: 0.32s;
}

@keyframes qx-svc-blink {
  0%,
  60%,
  100% {
    opacity: 0.25;
  }
  30% {
    opacity: 1;
  }
}

.svc__login {
  margin-top: 14px;
  padding: 12px;
  border-radius: 12px;
  background: #fff;
  border: 1px dashed var(--qx-line);
  text-align: center;
}

.svc__login p {
  font-size: 12.5px;
  color: var(--qx-ink-3);
}

.svc__login button {
  margin-top: 8px;
  padding: 7px 18px;
  border: none;
  border-radius: 16px;
  background: var(--qx-grad);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

/* ---------- 待确认操作（Agent 提议、等人点头） ---------- */
.svc__approvals {
  max-height: 42%;
  padding: 10px 12px 2px;
  overflow-y: auto;
  background: #fffaf6;
  border-top: 1px solid #ffe2d3;
}

.svc__approvals-head {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--qx-brand-dark);
}

.svc__approvals-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--qx-brand);
  box-shadow: 0 0 0 3px rgba(255, 107, 53, 0.18);
}

.svc__approval {
  margin-bottom: 8px;
  padding: 10px 12px;
  border: 1px solid #ffd9c4;
  border-radius: 12px;
  background: #fff;
}

.svc__approval-code {
  display: inline-block;
  padding: 1px 7px;
  border-radius: 6px;
  background: var(--qx-brand-soft);
  color: var(--qx-brand-dark);
  font-size: 10.5px;
  letter-spacing: 0.5px;
}

.svc__approval-desc {
  margin-top: 6px;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--qx-ink);
}

.svc__approval-note {
  margin-top: 3px;
  font-size: 11px;
  color: var(--qx-ink-3);
}

.svc__approval-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}

.svc__approval-cancel {
  padding: 6px 14px;
  border: 1px solid var(--qx-line);
  border-radius: 15px;
  background: #fff;
  color: var(--qx-ink-2);
  font-size: 12.5px;
  cursor: pointer;
  transition: color 0.16s ease, border-color 0.16s ease;
}

.svc__approval-cancel:hover:not(:disabled) {
  border-color: var(--qx-ink-3);
  color: var(--qx-ink);
}

.svc__approval-ok {
  padding: 6px 16px;
  border: none;
  border-radius: 15px;
  background: var(--qx-grad);
  color: #fff;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(255, 90, 31, 0.24);
  transition: filter 0.16s ease;
}

.svc__approval-ok:hover:not(:disabled) {
  filter: brightness(1.05);
}

.svc__approval-ok:disabled,
.svc__approval-cancel:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
}

.svc__composer {
  display: flex;
  gap: 8px;
  padding: 12px;
  background: #fff;
  border-top: 1px solid var(--qx-line);
}

.svc__composer input {
  flex: 1;
  height: 38px;
  padding: 0 14px;
  border: 1.5px solid var(--qx-line);
  border-radius: 19px;
  outline: none;
  background: #fbfcfe;
  font-size: 13px;
  color: var(--qx-ink);
  transition: border-color 0.16s ease;
}

.svc__composer input:focus {
  border-color: var(--qx-brand);
  background: #fff;
}

.svc__composer button {
  height: 38px;
  padding: 0 18px;
  border: none;
  border-radius: 19px;
  background: var(--qx-grad);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.16s ease;
}

.svc__composer button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

@media (max-width: 520px) {
  .svc {
    right: 14px;
    bottom: 14px;
  }

  .svc__panel {
    width: calc(100vw - 28px);
    height: calc(100vh - 120px);
  }
}
</style>
