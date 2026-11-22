import axios, { AxiosError, type AxiosRequestConfig } from 'axios'

type Envelope<T> = { code: string; message: string; data: T }
export type Page<T> = { items: T[]; total: number }

export const http = axios.create({ baseURL: '/api/v1', timeout: 15_000, withCredentials: true })
let csrfToken: string | null = null
export function clearCsrf() { csrfToken = null }

async function csrf() {
  if (!csrfToken) {
    const response = await http.get<Envelope<{ token: string }>>('/auth/csrf')
    csrfToken = response.data.data.token
  }
  return csrfToken
}

export async function api<T>(method: 'GET' | 'POST' | 'PATCH' | 'PUT', url: string, data?: unknown, config: AxiosRequestConfig = {}): Promise<T> {
  const headers = { ...config.headers } as Record<string, string>
  if (method !== 'GET' && (url.startsWith('/teacher/') || url.startsWith('/student/'))) headers['X-CSRF-Token'] = await csrf()
  const response = await http.request<Envelope<T>>({ ...config, method, url, data, headers })
  return response.data.data
}

export function errorMessage(error: unknown): string {
  if (error instanceof AxiosError) {
    const data = error.response?.data as { message?: string } | undefined
    return data?.message || (error.response ? `请求失败（${error.response.status}）` : '网络连接失败，请检查后端是否启动')
  }
  return error instanceof Error ? error.message : '操作失败'
}

export function newKey() { return crypto.randomUUID() }
