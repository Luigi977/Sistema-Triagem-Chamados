import { api } from './api'
import type { Mensagem } from '../types/api'
export const mensagemService = {
  list: (id: string, signal?: AbortSignal) =>
    api<Mensagem[]>(`/chamados/${id}/mensagens`, { signal }),
  reply: (id: number, texto: string) =>
    api<Mensagem>(`/chamados/${id}/responder`, { method: 'POST', body: { texto } }),
}
