const labels: Record<string, string> = {
  ABERTO: 'Aberto',
  EM_ATENDIMENTO: 'Em atendimento',
  AGUARDANDO_USUARIO: 'Aguardando usuário',
  ENCERRADO: 'Encerrado',
  BAIXA: 'Baixa',
  MEDIA: 'Média',
  ALTA: 'Alta',
  CRITICA: 'Crítica',
  INCIDENTE: 'Incidente',
  REQUISICAO: 'Requisição',
  DUVIDA: 'Dúvida',
  ADMIN: 'Administrador',
  TECNICO: 'Técnico',
  MANUAL: 'Manual',
  WHATSAPP: 'WhatsApp',
}
export const label = (value: string | null | undefined) =>
  value ? (labels[value] ?? value) : 'Não informado'
// LocalDateTime do Spring não carrega fuso. Interpretar como horário local, sem acrescentar Z.
export function date(value: string | null) {
  if (!value) return 'Não informado'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime())
    ? 'Data indisponível'
    : new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(parsed)
}
export const initials = (name: string) =>
  name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((n) => n[0])
    .join('')
    .toUpperCase()
