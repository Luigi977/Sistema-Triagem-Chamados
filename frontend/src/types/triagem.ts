import type { Natureza, Prioridade } from './api'
// Contrato interno proposto, sem endpoint correspondente nesta etapa.
export interface Recomendacao {
  idDemonstrativo: string
  natureza: Natureza
  prioridade: Prioridade
  impacto: string
  urgencia: string
  causas: string[]
  estado: 'PENDENTE' | 'ACEITA'
  origem: 'SIMULADA'
}
