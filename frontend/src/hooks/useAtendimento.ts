import { useCallback, useRef, useState } from 'react'
import { useQuery } from './useQuery'
import { chamadoService } from '../services/chamadoService'
import { mensagemService } from '../services/mensagemService'
import { ApiError, errorMessage } from '../services/api'
import type { Mensagem } from '../types/api'
export function reconcileMessages(remote: Mensagem[], accepted: Mensagem[]) {
  // Preserve server order. Newly accepted messages join the tail until the next GET includes them.
  const ids = new Set(remote.map((message) => message.id))
  return [...remote, ...accepted.filter((message) => !ids.has(message.id))]
}
export function useAtendimento(id: string) {
  const chamado = useQuery(
    useCallback((signal: AbortSignal) => chamadoService.get(id, signal), [id]),
    5000,
  )
  const messages = useQuery(
    useCallback((signal: AbortSignal) => mensagemService.list(id, signal), [id]),
    5000,
  )
  const operations = useQuery(
    useCallback((signal: AbortSignal) => chamadoService.operations(id, signal), [id]),
    5000,
  )
  const [accepted, setAccepted] = useState<Mensagem[]>([])
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [feedback, setFeedback] = useState('')
  const [sendError, setSendError] = useState('')
  const lock = useRef(false)
  const refresh = () => {
    chamado.reload()
    messages.reload()
    operations.reload()
  }
  async function send() {
    if (lock.current || !draft.trim() || draft.length > 10000) return
    lock.current = true
    setSending(true)
    setFeedback('')
    setSendError('')
    try {
      const message = await mensagemService.reply(Number(id), draft)
      setAccepted((old) => [...old.filter((item) => item.id !== message.id), message])
      setDraft('')
      setFeedback('Envio aceito pela Evolution. Isso não confirma entrega ou leitura.')
      refresh()
    } catch (cause) {
      setSendError(
        `${errorMessage(cause)}${cause instanceof ApiError && cause.uncertain ? ' Resultado incerto: o rascunho foi preservado. Confira o histórico antes de reenviar; o envio não será repetido automaticamente.' : ' O rascunho foi preservado.'}`,
      )
      refresh()
    } finally {
      setSending(false)
      lock.current = false
    }
  }
  return {
    chamado,
    messages,
    operations,
    allMessages: reconcileMessages(messages.data ?? [], accepted),
    draft,
    setDraft,
    sending,
    feedback,
    sendError,
    send,
    refresh,
  }
}
