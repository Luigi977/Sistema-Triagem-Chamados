import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { AuthContext } from './context'
import { authService } from '../services/authService'
import { setSessionToken, setUnauthorizedHandler } from '../services/api'
import type { Tecnico } from '../types/api'
export function AuthProvider({ children }: { children: ReactNode }) {
  const [tecnico, setTecnico] = useState<Tecnico | null>(null)
  const [expiry, setExpiry] = useState<number | null>(null)
  const clear = useCallback(() => {
    setSessionToken(null)
    setTecnico(null)
    setExpiry(null)
  }, [])
  useEffect(() => {
    setUnauthorizedHandler(clear)
    return () => setUnauthorizedHandler(undefined)
  }, [clear])
  useEffect(() => {
    if (!expiry) return
    const remaining = expiry - Date.now()
    const timer = window.setTimeout(clear, Math.max(0, remaining))
    return () => window.clearTimeout(timer)
  }, [expiry, clear])
  async function login(login: string, senha: string) {
    const session = await authService.login(login, senha)
    setSessionToken(session.token)
    try {
      const identity = await authService.me()
      setTecnico(identity)
      setExpiry(new Date(session.expiraEm).getTime())
    } catch (error) {
      clear()
      throw error
    }
  }
  async function logout() {
    try {
      await authService.logout()
    } finally {
      clear()
    }
  }
  return <AuthContext.Provider value={{ tecnico, login, logout }}>{children}</AuthContext.Provider>
}
