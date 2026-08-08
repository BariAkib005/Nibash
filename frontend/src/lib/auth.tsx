import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api, clearToken, getToken, setToken } from './api'
import type { ApiBuilding, ApiUser, SignupPayload } from '../types'

interface AuthState {
  user: ApiUser | null
  building: ApiBuilding | null
  /** True until the initial /me/ probe settles, so routes don't flash the login page. */
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  signup: (payload: SignupPayload) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<ApiUser | null>(null)
  const [building, setBuilding] = useState<ApiBuilding | null>(null)
  const [loading, setLoading] = useState(true)

  // Re-hydrate the session on boot so a page refresh keeps you logged in.
  useEffect(() => {
    if (!getToken()) {
      setLoading(false)
      return
    }
    let cancelled = false
    api
      .me()
      .then((session) => {
        if (cancelled) return
        setUser(session.user)
        setBuilding(session.building)
      })
      .catch(() => {
        if (!cancelled) clearToken()
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const result = await api.login(email, password)
    setToken(result.token)
    setUser(result.user)
    setBuilding(result.building)
  }, [])

  const signup = useCallback(async (payload: SignupPayload) => {
    const result = await api.signup(payload)
    setToken(result.token)
    setUser(result.user)
    setBuilding(result.building)
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.logout()
    } finally {
      // Even if the network call fails, the local session must end.
      clearToken()
      setUser(null)
      setBuilding(null)
    }
  }, [])

  const value = useMemo<AuthState>(
    () => ({ user, building, loading, login, signup, logout }),
    [user, building, loading, login, signup, logout],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>')
  return context
}
