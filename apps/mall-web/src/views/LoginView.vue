<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const mode = ref<'login' | 'register'>('login')
const submitting = ref(false)
const errorMessage = ref('')

const form = reactive({ username: '', password: '', confirm: '' })

const title = computed(() => (mode.value === 'login' ? '欢迎回来' : '创建账号'))
const subtitle = computed(() =>
  mode.value === 'login' ? '登录后可以加购物车、下单和查看订单' : '注册即刻可用，密码至少 8 位',
)

function switchMode(next: 'login' | 'register') {
  mode.value = next
  errorMessage.value = ''
}

function validate(): string {
  const username = form.username.trim()
  if (username.length < 3) return '用户名至少 3 个字符'
  if (form.password.length < 8) return '密码至少 8 位'
  if (mode.value === 'register' && form.password !== form.confirm) return '两次输入的密码不一致'
  return ''
}

async function submit() {
  if (submitting.value) return

  const message = validate()
  if (message) {
    errorMessage.value = message
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    const username = form.username.trim()
    if (mode.value === 'login') {
      await auth.login(username, form.password)
    } else {
      await auth.register(username, form.password)
    }

    // 登录前想去哪，登录后回哪；没有就来路为空 → 首页
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirect)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '操作失败，请重试'
  } finally {
    submitting.value = false
  }
}

/** 一键填入演示账号，省得每次手敲 */
function fillDemo() {
  mode.value = 'login'
  form.username = 'demo_test'
  form.password = '12345678'
  errorMessage.value = ''
}
</script>

<template>
  <div class="login">
    <section class="login__art">
      <div class="art__mark">栖</div>
      <h1>认真挑选的<br />每一件好物</h1>
      <p>数码 · 家居 · 美妆 · 食品，跨店下单一次结算</p>
      <ul class="art__points">
        <li><span>01</span>多商户正品直供</li>
        <li><span>02</span>满 99 元包邮</li>
        <li><span>03</span>7 天无理由退换</li>
      </ul>
      <div class="art__glow art__glow--a"></div>
      <div class="art__glow art__glow--b"></div>
    </section>

    <section class="login__panel">
      <div class="panel__card">
        <header class="panel__head">
          <h2>{{ title }}</h2>
          <p>{{ subtitle }}</p>
        </header>

        <div class="tabs">
          <button
            type="button"
            class="tabs__item"
            :class="{ 'is-active': mode === 'login' }"
            @click="switchMode('login')"
          >
            登录
          </button>
          <button
            type="button"
            class="tabs__item"
            :class="{ 'is-active': mode === 'register' }"
            @click="switchMode('register')"
          >
            注册
          </button>
        </div>

        <form class="form" @submit.prevent="submit">
          <label class="field">
            <span class="field__label">用户名</span>
            <input
              v-model="form.username"
              type="text"
              autocomplete="username"
              placeholder="3-32 个字符"
            />
          </label>

          <label class="field">
            <span class="field__label">密码</span>
            <input
              v-model="form.password"
              type="password"
              :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
              placeholder="至少 8 位"
            />
          </label>

          <label v-if="mode === 'register'" class="field">
            <span class="field__label">确认密码</span>
            <input
              v-model="form.confirm"
              type="password"
              autocomplete="new-password"
              placeholder="再输一次"
            />
          </label>

          <p v-if="errorMessage" class="form__error">{{ errorMessage }}</p>

          <button type="submit" class="form__submit" :disabled="submitting">
            {{ submitting ? '处理中…' : mode === 'login' ? '登 录' : '注册并登录' }}
          </button>
        </form>

        <footer class="panel__foot">
          <button type="button" class="link" @click="fillDemo">使用演示账号</button>
          <RouterLink to="/" class="link">先去逛逛</RouterLink>
        </footer>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login {
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  min-height: calc(100vh - 68px);
}

/* ---------- 左侧品牌视觉 ---------- */
.login__art {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 8%;
  overflow: hidden;
  background: linear-gradient(150deg, #ff7a45 0%, #ff5a1f 45%, #e8440f 100%);
  color: #fff;
}

.art__mark {
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  margin-bottom: 28px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(8px);
  font-size: 28px;
  font-weight: 700;
}

.login__art h1 {
  margin: 0 0 16px;
  font-size: 40px;
  line-height: 1.28;
  font-weight: 600;
  letter-spacing: 1px;
}

.login__art p {
  margin: 0 0 36px;
  font-size: 15px;
  color: rgba(255, 255, 255, 0.82);
}

.art__points {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.art__points li {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.92);
}

.art__points span {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.16);
  font-size: 11px;
  letter-spacing: 1px;
}

.art__glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(10px);
  opacity: 0.5;
  pointer-events: none;
}

.art__glow--a {
  width: 320px;
  height: 320px;
  right: -80px;
  top: -60px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.45), transparent 70%);
}

.art__glow--b {
  width: 260px;
  height: 260px;
  right: 40px;
  bottom: -70px;
  background: radial-gradient(circle, rgba(255, 214, 190, 0.6), transparent 70%);
}

/* ---------- 右侧表单 ---------- */
.login__panel {
  display: grid;
  place-items: center;
  padding: 40px 24px;
  background: #fff;
}

.panel__card {
  width: 100%;
  max-width: 380px;
}

.panel__head h2 {
  margin: 0 0 8px;
  font-size: 26px;
  font-weight: 600;
  color: var(--qx-ink);
}

.panel__head p {
  margin: 0 0 26px;
  font-size: 13px;
  color: var(--qx-ink-3);
}

.tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 22px;
  padding: 4px;
  border-radius: 12px;
  background: #f4f6fa;
}

.tabs__item {
  flex: 1;
  height: 36px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--qx-ink-2);
  font-size: 14px;
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease, box-shadow 0.18s ease;
}

.tabs__item.is-active {
  background: #fff;
  color: var(--qx-brand-dark);
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(31, 36, 48, 0.08);
}

.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.field__label {
  font-size: 12px;
  color: var(--qx-ink-2);
}

.field input {
  height: 44px;
  padding: 0 14px;
  border: 1.5px solid var(--qx-line);
  border-radius: 11px;
  background: #fbfcfe;
  color: var(--qx-ink);
  font-size: 14px;
  outline: none;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.field input:focus {
  border-color: var(--qx-brand);
  background: #fff;
  box-shadow: 0 0 0 4px rgba(255, 107, 53, 0.1);
}

.form__error {
  margin: 0;
  padding: 9px 12px;
  border-radius: 9px;
  background: #fff1f0;
  color: #d4380d;
  font-size: 12.5px;
}

.form__submit {
  height: 46px;
  margin-top: 4px;
  border: none;
  border-radius: 11px;
  background: var(--qx-grad);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 2px;
  cursor: pointer;
  transition: filter 0.18s ease, transform 0.12s ease;
}

.form__submit:hover:not(:disabled) {
  filter: brightness(1.05);
}

.form__submit:active:not(:disabled) {
  transform: translateY(1px);
}

.form__submit:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.panel__foot {
  display: flex;
  justify-content: space-between;
  margin-top: 22px;
  font-size: 12.5px;
}

.link {
  padding: 0;
  border: none;
  background: none;
  color: var(--qx-ink-3);
  cursor: pointer;
  text-decoration: none;
  transition: color 0.18s ease;
}

.link:hover {
  color: var(--qx-brand-dark);
}

@media (max-width: 900px) {
  .login {
    grid-template-columns: 1fr;
  }

  .login__art {
    display: none;
  }

  .login__panel {
    padding: 32px 20px 56px;
  }
}
</style>
