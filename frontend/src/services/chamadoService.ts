import { api, ApiError } from './api'
import type {
  Chamado,
  CriarChamado,
  Detalhes,
  Operacao,
  Page,
  Prioridade,
  Status,
} from '../types/api'
export function parsePage<T>(value: Page<T>): Page<T> {
  if (
    !value ||
    !Array.isArray(value.content) ||
    !Number.isInteger(value.number) ||
    value.number < 0 ||
    !Number.isInteger(value.size) ||
    value.size <= 0 ||
    !Number.isInteger(value.totalElements) ||
    value.totalElements < 0 ||
    !Number.isInteger(value.totalPages) ||
    value.totalPages < 0 ||
    typeof value.last !== 'boolean' ||
    typeof value.first !== 'boolean'
  ) {
    throw new ApiError('A API retornou uma paginação incompatível. Consulte o contrato do backend.')
  }
  return value
}
export const chamadoService = {
  list: async (params: URLSearchParams, signal?: AbortSignal) =>
    parsePage(await api<Page<Chamado>>(`/chamados?${params}`, { signal })),
  get: (id: string, signal?: AbortSignal) =>
    api<Chamado>(`/chamados/${encodeURIComponent(id)}`, { signal }),
  create: (body: CriarChamado) => api<Chamado>('/chamados', { method: 'POST', body }),
  operations: (id: string, signal?: AbortSignal) =>
    api<Operacao[]>(`/chamados/${id}/operacoes`, { signal }),
  history: async (phone: string, page: number, signal?: AbortSignal) =>
    parsePage(
      await api<Page<Chamado>>(
        `/chamados/cliente/${encodeURIComponent(phone)}?page=${page}&size=5&sort=dataAbertura,desc`,
        { signal },
      ),
    ),
  status: (id: number, status: Exclude<Status, 'ENCERRADO'>) =>
    api<Chamado>(`/chamados/${id}/status`, { method: 'PATCH', body: { status } }),
  priority: (id: number, prioridadeFinal: Prioridade) =>
    api<Chamado>(`/chamados/${id}/prioridade`, { method: 'PATCH', body: { prioridadeFinal } }),
  assign: (id: number, responsavelId: number) =>
    api<Chamado>(`/chamados/${id}/responsavel`, { method: 'PATCH', body: { responsavelId } }),
  details: (id: number, body: Detalhes) =>
    api<Chamado>(`/chamados/${id}/detalhes`, { method: 'PATCH', body }),
  team: (id: number, equipe: string) =>
    api<Chamado>(`/chamados/${id}/equipe`, { method: 'PATCH', body: { equipe } }),
  close: (id: number, causaReal: string, solucaoAplicada: string) =>
    api<Chamado>(`/chamados/${id}/encerrar`, {
      method: 'POST',
      body: { causaReal, solucaoAplicada },
    }),
}
