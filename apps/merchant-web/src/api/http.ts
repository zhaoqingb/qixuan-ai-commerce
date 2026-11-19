import axios from "axios"
import { ElMessage } from "element-plus"

const TOKEN_KEY = 'qx_merchant_token'

export function getToken(): string | null{
    return localStorage.getItem(TOKEN_KEY)
}

export function saveToken(token: string): void{
    localStorage.setItem(TOKEN_KEY,token)
}

export function clearToken(): void{
    localStorage.removeItem(TOKEN_KEY)
}

// 创建统一的Axios实例
const http = axios.create({
    baseURL: "/api",
    timeout:10000,
})

http.interceptors.request.use((config) => {
    const token = getToken()
    if (token) {
        config.headers.set('Authorization', `Bearer ${token}`)
    }
    return config
})


http.interceptors.response.use(
    (response) => response,
    (error) => {
        const status = error.response?.status
        const message=error.response?.data?.message??'网络异常，请稍后重试'
        const url = error.config?.url ?? ''
        const isLoginApi = url.includes("/auth/login")
        if (status === 401) {
            if (isLoginApi) {
                ElMessage.error(message)
            } else {
                clearToken()
                ElMessage.error("登录过期，请重新登录")
                if (window.location.pathname !== "/login") {
                    window.location.href = "/login"
                }
            }
        } else if (status === 403){
            ElMessage.error("没有权限访问该资源")
        } else {
            ElMessage.error(message)
        }
        return Promise.reject(new Error(message))
})

export default http