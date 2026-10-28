import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, tokens } from '../api/client'
import type { Role, TokenResponse, UserInfo } from '../api/types'

interface RegisterInput {
  businessName: string
  businessGstin: string
  fullName: string
  email: string
  password: string
}

interface AuthState {
  user: UserInfo | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (input: RegisterInput) => Promise<void>
  logout: () => Promise<void>
  can: (...roles: Role[]) => boolean
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!tokens.access) {
      setLoading(false)
      return
    }
    api
      .get<UserInfo>('/auth/me')
      .then((r) => setUser(r.data))
      .catch(() => tokens.clear())
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    const onLogout = () => setUser(null)
    window.addEventListener('auth:logout', onLogout)
    return () => window.removeEventListener('auth:logout', onLogout)
  }, [])

  const accept = (t: TokenResponse) => {
    tokens.save(t)
    setUser(t.user)
  }

  const login = useCallback(async (email: string, password: string) => {
    accept((await api.post<TokenResponse>('/auth/login', { email, password })).data)
  }, [])

  const register = useCallback(async (input: RegisterInput) => {
    accept((await api.post<TokenResponse>('/auth/register', input)).data)
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.post('/auth/logout', { refreshToken: tokens.refresh })
    } finally {
      tokens.clear()
      setUser(null)
    }
  }, [])

  const can = useCallback((...roles: Role[]) => !!user && roles.includes(user.role), [user])

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout, can }}>{children}</AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
