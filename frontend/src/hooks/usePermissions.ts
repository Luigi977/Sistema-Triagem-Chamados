import { useAuth } from '../auth/context'
import type { Chamado } from '../types/api'
export function usePermissions(chamado?: Chamado) {
  const { tecnico } = useAuth()
  const admin = tecnico?.perfil === 'ADMIN'
  const canEdit =
    !!tecnico &&
    !!chamado &&
    chamado.status !== 'ENCERRADO' &&
    (admin ||
      (tecnico.equipe === chamado.equipe &&
        (chamado.responsavelId === null || chamado.responsavelId === tecnico.id)))
  return { admin, canEdit }
}
