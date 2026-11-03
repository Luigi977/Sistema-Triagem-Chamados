import { useCallback } from 'react'
import { useQuery } from './useQuery'
import { tecnicoService } from '../services/tecnicoService'
export function useReferences() {
  const teams = useQuery(useCallback((signal: AbortSignal) => tecnicoService.teams(signal), []))
  const technicians = useQuery(
    useCallback((signal: AbortSignal) => tecnicoService.list(signal), []),
  )
  return { teams, technicians }
}
