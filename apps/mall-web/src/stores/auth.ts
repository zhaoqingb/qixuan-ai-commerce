import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { login as apiLogin, register as apiRegister } from '@/api/auth'
import { clearSession, getToken, readUsername, saveSession } from '@/api/http'

/**
 * 登录态放在 store 里：顶栏显示用户名、购物车判断要不要发请求、
 * 订单页决定要不要跳登录，都从这里读。
 *
 * token 本身落地在 localStorage，刷新页面不会掉线。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const username = ref<string>(readUsername())

  const isLoggedIn = computed(() => Boolean(token.value))

  async function login(name: string, password: string): Promise<void> {
    const result = await apiLogin(name, password)
    token.value = result.token
    username.value = result.username
    saveSession(result.token, result.username)
  }

  async function register(name: string, password: string): Promise<void> {
    await apiRegister(name, password)
    // 注册成功直接登录，省掉用户再输一遍
    await login(name, password)
  }

  function logout(): void {
    token.value = null
    username.value = ''
    clearSession()
  }

  return { token, username, isLoggedIn, login, register, logout }
})
