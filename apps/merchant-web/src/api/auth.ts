import http from './http'

export interface LoginPayload{
    username: string
    password: string
    
}

export interface LoginResult{
    token: string
    userId: number
    username: string
}

export async function login(payload: LoginPayload): Promise<LoginResult> {
    const { data } = await http.post<LoginResult>('/auth/login', payload)
    return data
}