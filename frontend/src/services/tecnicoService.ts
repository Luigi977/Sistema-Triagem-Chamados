import { api } from './api'
import type { Tecnico } from '../types/api'
export const tecnicoService = {
  list: (signal?: AbortSignal) => api<Tecnico[]>('/tecnicos', { signal }),
  teams: (signal?: AbortSignal) => api<string[]>('/equipes', { signal }),
}
