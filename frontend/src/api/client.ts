import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { TokenResponse } from './types'

const ACCESS = 'gst.accessToken'
const REFRESH = 'gst.refreshToken'

export const tokens = {
  get access() {
    return localStorage.getItem(ACCESS)
  },
  get refresh() {
    return localStorage.getItem(REFRESH)
  },
  save(t: TokenResponse) {
    localStorage.setItem(ACCESS, t.accessToken)
    localStorage.setItem(REFRESH, t.refreshToken)
  },
  clear() {
    localStorage.removeItem(ACCESS)
    localStorage.removeItem(REFRESH)
  },
}

export const api = axios.create({ baseURL: import.meta.env.VITE_API_URL ?? '/api' })

api.interceptors.request.use((config) => {
  const token = tokens.access
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// On a 401, try one refresh (shared across concurrent requests), then replay the request.
let refreshing: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = tokens.refresh
  if (!refreshToken) return null
  try {
    const { data } = await axios.post<TokenResponse>(`${api.defaults.baseURL}/auth/refresh`, { refreshToken })
    tokens.save(data)
    return data.accessToken
  } catch {
    tokens.clear()
    return null
  }
}

api.interceptors.response.use(
  (r) => r,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
    if (error.response?.status === 401 && original && !original._retried && !/\/auth\/(login|register|refresh)/.test(original.url ?? '')) {
      original._retried = true
      refreshing ??= refreshAccessToken().finally(() => (refreshing = null))
      const token = await refreshing
      if (token) {
        original.headers.Authorization = `Bearer ${token}`
        return api(original)
      }
      window.dispatchEvent(new Event('auth:logout'))
    }
    return Promise.reject(error)
  },
)

export function errorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as { detail?: string } | undefined
    if (data?.detail) return data.detail
    if (err.response?.status === 403) return 'You do not have permission for this action'
    return err.message
  }
  return String(err)
}

/** Downloads an authenticated binary response as a file. */
export async function download(url: string, params: Record<string, unknown>, fallbackName: string) {
  const res = await api.get(url, { params, responseType: 'blob' })
  const disposition = res.headers['content-disposition'] as string | undefined
  const name = disposition?.match(/filename="?([^"]+)"?/)?.[1] ?? fallbackName
  const href = URL.createObjectURL(res.data as Blob)
  const a = document.createElement('a')
  a.href = href
  a.download = name
  a.click()
  URL.revokeObjectURL(href)
}
