import http from './http'

export interface LoginResult {
  token: string
  userId: number
  username: string
}

export interface RegisterResult {
  userId: number
  username: string
}

export async function login(username: string, password: string): Promise<LoginResult> {
  const { data } = await http.post<LoginResult>('/auth/login', { username, password })
  return data
}

export async function register(username: string, password: string): Promise<RegisterResult> {
  const { data } = await http.post<RegisterResult>('/auth/register', { username, password })
  return data
}
