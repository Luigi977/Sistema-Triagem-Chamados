import { api } from './api'
import type { Sessao, Tecnico } from '../types/api'
export const authService = {
  login: (login: string, senha: string) =>
    api<Sessao>('/auth/login', { method: 'POST', body: { login, senha }, anonymous: true }),
  me: (signal?: AbortSignal) => api<Tecnico>('/auth/me', { signal }),
  logout: () => api<void>('/auth/logout', { method: 'POST' }),
}
