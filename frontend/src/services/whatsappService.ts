import { api } from './api'
import type { WhatsappStatus } from '../types/api'
export const whatsappService = {
  status: (signal?: AbortSignal) => api<WhatsappStatus>('/whatsapp/status', { signal }),
}
