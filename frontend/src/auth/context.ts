import { createContext, useContext } from 'react'
import type { Tecnico } from '../types/api'
export interface Auth {
  tecnico: Tecnico | null
  login: (login: string, senha: string) => Promise<void>
  logout: () => Promise<void>
}
export const AuthContext = createContext<Auth | null>(null)
export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('AuthProvider ausente')
  return value
}
