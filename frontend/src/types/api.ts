export const statuses = ['ABERTO', 'EM_ATENDIMENTO', 'AGUARDANDO_USUARIO', 'ENCERRADO'] as const
export const priorities = ['BAIXA', 'MEDIA', 'ALTA', 'CRITICA'] as const
export const natures = ['INCIDENTE', 'REQUISICAO', 'DUVIDA'] as const
export type Status = (typeof statuses)[number]
export type Prioridade = (typeof priorities)[number]
export type Natureza = (typeof natures)[number]
export interface Tecnico {
  id: number
  login: string
  nome: string
  perfil: 'ADMIN' | 'TECNICO'
  equipe: string
  ativo: boolean
}
export interface Sessao {
  token: string
  expiraEm: string
  tecnico: Tecnico
}
export interface Chamado {
  id: number
  telefoneCliente: string | null
  contatoWhatsappId: string | null
  chamadoAnteriorId: number | null
  nomeCliente: string | null
  status: Status
  prioridadeSugerida: Prioridade | null
  prioridadeFinal: Prioridade | null
  responsavel: string | null
  responsavelId: number | null
  causaReal: string | null
  solucaoAplicada: string | null
  dataAbertura: string
  dataEncerramento: string | null
  resumo: string | null
  descricao: string | null
  natureza: Natureza | null
  setor: string | null
  canal: 'MANUAL' | 'WHATSAPP' | null
  equipe: string | null
}
export interface CriarChamado {
  telefoneCliente: string
  nomeCliente?: string
  prioridadeSugerida?: Prioridade
  resumo?: string
  descricao?: string
  natureza?: Natureza
  setor?: string
  canal: 'MANUAL'
  equipe?: string
}
export interface Detalhes {
  resumo?: string
  descricao?: string
  natureza?: Natureza
  setor?: string
  canal?: 'MANUAL' | 'WHATSAPP'
}
export interface Mensagem {
  id: number
  chamadoId: number
  remetente: 'CLIENTE' | 'TECNICO' | 'SISTEMA'
  conteudo: string | null
  tipo: string
  dataEnvio: string
  identificadorExterno: string | null
  autorId: number | null
}
export interface Operacao {
  id: number
  autorId: number | null
  acao: string
  dataOperacao: string
}
export interface Page<T> {
  content: T[]
  number: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  numberOfElements: number
  empty: boolean
  sort?: { empty: boolean; sorted: boolean; unsorted: boolean }
  pageable?: {
    pageNumber: number
    pageSize: number
    offset: number
    paged: boolean
    unpaged: boolean
  }
}
export interface WhatsappStatus {
  instancia: string
  estado: 'open' | 'close' | 'connecting'
}
