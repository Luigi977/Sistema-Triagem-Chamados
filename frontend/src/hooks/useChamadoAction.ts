import { chamadoService } from '../services/chamadoService'
import type { Action } from '../components/atendimento/ChamadoActions'
export function useChamadoAction(id: number, refresh: () => void) {
  return async (action: Action) => {
    try {
      switch (action.kind) {
        case 'status':
          await chamadoService.status(id, action.value)
          break
        case 'priority':
          await chamadoService.priority(id, action.value)
          break
        case 'assign':
          await chamadoService.assign(id, action.value)
          break
        case 'team':
          await chamadoService.team(id, action.value)
          break
        case 'details':
          await chamadoService.details(id, action.value)
          break
      }
    } finally {
      refresh()
    }
  }
}
