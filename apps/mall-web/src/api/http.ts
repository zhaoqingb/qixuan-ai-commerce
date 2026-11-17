import axios from 'axios'

const TOKEN_KEY = 'qx_mall_token'
const USER_KEY = 'qx_mall_username'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function readUsername(): string {
  return localStorage.getItem(USER_KEY) ?? ''
}

export function saveSession(token: string, username: string): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, username)
}

export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// 每个请求自动带上 token，业务代码不用关心登录态
http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

// 后端异常统一返回 { code, message, path, timeStamp }，这里把 message 抛给调用方
http.interceptors.response.use(
  (response) => response,
  (error) => {
    const status: number | undefined = error?.response?.status
    const data = error?.response?.data
    const message =
      (data && typeof data.message === 'string' && data.message) ||
      error?.message ||
      '网络异常，请稍后再试'

    // token 过期或被篡改：清掉本地登录态，回登录页并记住来路
    if (status === 401 && !window.location.pathname.startsWith('/login')) {
      clearSession()
      const redirect = encodeURIComponent(window.location.pathname + window.location.search)
      window.location.href = `/login?redirect=${redirect}`
    }

    return Promise.reject(new Error(message))
  },
)

export default http
