import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// 后端异常统一返回 { code, message, path, timeStamp }，这里把 message 抛给调用方
http.interceptors.response.use(
  (response) => response,
  (error) => {
    const data = error?.response?.data
    const message =
      (data && typeof data.message === 'string' && data.message) ||
      error?.message ||
      '网络异常，请稍后再试'
    return Promise.reject(new Error(message))
  },
)

export default http
