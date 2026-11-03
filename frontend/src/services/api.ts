let token: string | null = null
let onUnauthorized: (() => void) | undefined
export function setSessionToken(value: string | null) {
  token = value
}
export function setUnauthorizedHandler(handler?: () => void) {
  onUnauthorized = handler
}
export class ApiError extends Error {
  status: number
  uncertain: boolean
  constructor(message: string, status = 0, uncertain = false) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.uncertain = uncertain
  }
}
const guidance: Record<number, string> = {
  400: 'Revise os campos informados e os limites indicados.',
  401: 'Sessão expirada ou credenciais inválidas. Entre novamente.',
  403: 'Você não tem permissão para esta ação. Confira a equipe e o responsável atual.',
  404: 'Recurso não encontrado. Ele pode não estar mais disponível.',
  409: 'O estado foi alterado. Atualize os dados e confira o histórico antes de continuar.',
  502: 'Falha na comunicação com o provedor. Confira o histórico antes de repetir uma ação.',
  503: 'Serviço indisponível. Tente consultar novamente em instantes.',
  504: 'O serviço excedeu o tempo de resposta. Confira o histórico antes de reenviar.',
}
interface Options {
  method?: 'GET' | 'POST' | 'PATCH'
  body?: unknown
  signal?: AbortSignal
  timeout?: number
  anonymous?: boolean
}
export async function api<T>(path: string, options: Options = {}): Promise<T> {
  const controller = new AbortController()
  let timedOut = false
  const abort = () => controller.abort()
  options.signal?.addEventListener('abort', abort, { once: true })
  if (options.signal?.aborted) controller.abort()
  const timeout = window.setTimeout(() => {
    timedOut = true
    controller.abort()
  }, options.timeout ?? 15000)
  const method = options.method ?? 'GET'
  try {
    const response = await fetch(`/api${path}`, {
      method,
      signal: controller.signal,
      cache: 'no-store',
      headers: {
        Accept: 'application/json',
        ...(options.body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(!options.anonymous && token ? { Authorization: `Bearer ${token}` } : {}),
      },
      ...(options.body !== undefined ? { body: JSON.stringify(options.body) } : {}),
    })
    if (response.status === 401 && !options.anonymous) onUnauthorized?.()
    const raw = await response.text()
    let data: unknown = raw || undefined
    if (raw && response.headers.get('content-type')?.includes('json')) {
      try {
        data = JSON.parse(raw) as unknown
      } catch {
        throw new ApiError('Resposta inválida do servidor.', response.status, method !== 'GET')
      }
    }
    if (!response.ok) {
      throw new ApiError(
        guidance[response.status] ??
          `Não foi possível concluir a solicitação (${response.status}).`,
        response.status,
        method !== 'GET' && response.status >= 500,
      )
    }
    return data as T
  } catch (error) {
    if (error instanceof ApiError) throw error
    if (options.signal?.aborted) throw new DOMException('Consulta cancelada', 'AbortError')
    throw new ApiError(
      timedOut
        ? 'Tempo de resposta excedido. Consulte o histórico antes de repetir uma alteração.'
        : 'Sem comunicação com a API. Verifique sua conexão.',
      0,
      method !== 'GET',
    )
  } finally {
    window.clearTimeout(timeout)
    options.signal?.removeEventListener('abort', abort)
  }
}
export const errorMessage = (error: unknown) =>
  error instanceof Error ? error.message : 'Não foi possível concluir a solicitação.'
