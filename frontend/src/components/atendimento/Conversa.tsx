import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { ArrowDown, Send } from 'lucide-react'
import type { Mensagem } from '../../types/api'
import { date } from '../../utils/presentation'
import { Empty, ErrorState, Loading } from '../ui/States'
interface Props {
  messages: Mensagem[]
  loading: boolean
  error?: string
  retry: () => void
  draft: string
  onDraft: (value: string) => void
  sending: boolean
  canSend: boolean
  onSend: () => Promise<void>
  sendError: string
  feedback: string
}
export function Conversa({
  messages,
  loading,
  error,
  retry,
  draft,
  onDraft,
  sending,
  canSend,
  onSend,
  sendError,
  feedback,
}: Props) {
  const viewport = useRef<HTMLDivElement>(null)
  const atBottom = useRef(true)
  const previous = useRef('')
  const [newMessages, setNewMessages] = useState(false)
  const signature = messages.map((item) => item.id).join(',')
  const end = () => {
    const node = viewport.current
    if (node) node.scrollTop = node.scrollHeight
    atBottom.current = true
    setNewMessages(false)
  }
  useLayoutEffect(() => {
    if (signature === previous.current) return
    const node = viewport.current
    if (node && atBottom.current) node.scrollTop = node.scrollHeight
    previous.current = signature
  }, [signature])
  useEffect(() => {
    if (!atBottom.current && messages.length) {
      const timer = window.setTimeout(() => setNewMessages(true), 0)
      return () => window.clearTimeout(timer)
    }
  }, [signature, messages.length])
  return (
    <section className="card conversation">
      <div className="section-header">
        <h2>Conversa</h2>
        <span className="muted small">Atualiza a cada 5 segundos</span>
      </div>
      {loading && <Loading />}
      {error && <ErrorState error={error} retry={retry} />}
      <div
        className="messages"
        ref={viewport}
        onScroll={() => {
          const node = viewport.current
          if (node) {
            atBottom.current = node.scrollHeight - node.scrollTop - node.clientHeight < 64
            if (atBottom.current) setNewMessages(false)
          }
        }}
        role="log"
        aria-label="Histórico de mensagens"
        aria-live="polite"
        aria-relevant="additions"
      >
        {!loading && !error && !messages.length && (
          <Empty>Nenhuma mensagem registrada neste chamado.</Empty>
        )}
        {messages.map((message) => (
          <article key={message.id} className={`message ${message.remetente.toLowerCase()}`}>
            <strong className="message-author">
              {message.remetente === 'TECNICO'
                ? 'Técnico'
                : message.remetente === 'CLIENTE'
                  ? 'Cliente'
                  : 'Sistema'}
            </strong>
            {message.tipo !== 'TEXTO' && (
              <div className="attachment-label">
                Anexo · {message.tipo} · arquivo não disponível na API
              </div>
            )}
            <p>{message.conteudo || 'Sem legenda'}</p>
            <small>{date(message.dataEnvio)}</small>
          </article>
        ))}
      </div>
      {newMessages && (
        <button className="new-messages" onClick={end}>
          <ArrowDown size={15} />
          Novas mensagens
        </button>
      )}
      {feedback && (
        <div className="notice success" role="status">
          {feedback}
        </div>
      )}
      {sendError && <ErrorState error={sendError} />}
      <form
        className="composer"
        onSubmit={(event) => {
          event.preventDefault()
          if (canSend) void onSend()
        }}
      >
        <label className="sr-only" htmlFor="resposta">
          Resposta pelo WhatsApp
        </label>
        <textarea
          id="resposta"
          value={draft}
          onChange={(event) => onDraft(event.target.value)}
          maxLength={10000}
          rows={3}
          placeholder={
            canSend
              ? 'Responder ao cliente no WhatsApp…'
              : 'Atendimento em leitura. Envio indisponível.'
          }
          disabled={sending || !canSend}
        />
        <div className="composer-bottom">
          <small>{draft.length}/10.000 · Enter cria uma nova linha</small>
          <button className="button" disabled={!canSend || sending || !draft.trim()}>
            <Send size={16} />
            {sending ? 'Enviando…' : 'Enviar resposta'}
          </button>
        </div>
      </form>
    </section>
  )
}
