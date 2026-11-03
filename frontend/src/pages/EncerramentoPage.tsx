import { useCallback, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, CheckCircle2 } from 'lucide-react'
import { chamadoService } from '../services/chamadoService'
import { errorMessage } from '../services/api'
import { useQuery } from '../hooks/useQuery'
import { usePermissions } from '../hooks/usePermissions'
import { ErrorState, Loading, PageTitle } from '../components/ui/States'
import { Modal } from '../components/ui/Modal'
export function EncerramentoPage() {
  const { id = '' } = useParams()
  return <Encerramento key={id} id={id} />
}
function Encerramento({ id }: { id: string }) {
  const ticket = useQuery(
    useCallback((signal: AbortSignal) => chamadoService.get(id, signal), [id]),
    5000,
  )
  const { canEdit } = usePermissions(ticket.data)
  const navigate = useNavigate()
  const location = useLocation()
  const [cause, setCause] = useState('')
  const [solution, setSolution] = useState('')
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  function submit(event: FormEvent) {
    event.preventDefault()
    if (cause.trim() && solution.trim() && canEdit) setConfirming(true)
  }
  async function close() {
    setBusy(true)
    setError('')
    try {
      await chamadoService.close(Number(id), cause, solution)
      navigate(`/chamados/${id}`, { replace: true, state: location.state })
    } catch (reason) {
      setError(errorMessage(reason))
      ticket.reload()
      setConfirming(false)
    } finally {
      setBusy(false)
    }
  }
  return (
    <>
      <Link className="back-link" to={`/chamados/${id}`} state={location.state}>
        <ArrowLeft size={15} />
        Voltar ao atendimento
      </Link>
      <PageTitle
        title={`Encerrar chamado #${id}`}
        description="Registre o diagnóstico confirmado e a solução aplicada pelo técnico."
      />
      {ticket.loading && <Loading />}
      {ticket.error && <ErrorState error={ticket.error} retry={ticket.reload} />}
      {ticket.data && (
        <section className="card stack narrow">
          <h2>{ticket.data.resumo || `Chamado #${id}`}</h2>
          {error && <ErrorState error={error} />}
          {!canEdit && (
            <div className="notice">
              {ticket.data.status === 'ENCERRADO'
                ? 'Este chamado já está encerrado.'
                : 'Você não tem permissão para encerrar este chamado.'}{' '}
              <Link to={`/chamados/${id}`}>Consultar atendimento</Link>
            </div>
          )}
          <form className="stack" onSubmit={submit}>
            <fieldset className="stack" disabled={!canEdit || busy}>
              <label>
                Causa real identificada *
                <textarea
                  required
                  maxLength={10000}
                  rows={5}
                  value={cause}
                  onChange={(e) => setCause(e.target.value)}
                  placeholder="Descreva a causa confirmada…"
                />
              </label>
              <label>
                Solução aplicada *
                <textarea
                  required
                  maxLength={10000}
                  rows={5}
                  value={solution}
                  onChange={(e) => setSolution(e.target.value)}
                  placeholder="Descreva o que foi feito para resolver…"
                />
              </label>
              <button className="button" disabled={!cause.trim() || !solution.trim()}>
                <CheckCircle2 size={17} />
                Registrar encerramento
              </button>
            </fieldset>
          </form>
        </section>
      )}
      {confirming && (
        <Modal
          title="Confirmar encerramento"
          onClose={() => {
            if (!busy) setConfirming(false)
          }}
        >
          <div className="stack">
            <p>
              Encerrar o chamado #{id}? Após confirmar, o atendimento ficará disponível apenas para
              leitura.
            </p>
            <dl>
              <dt>Causa real</dt>
              <dd className="preserve">{cause}</dd>
              <dt>Solução aplicada</dt>
              <dd className="preserve">{solution}</dd>
            </dl>
            <div className="actions">
              <button className="button" disabled={busy || !canEdit} onClick={() => void close()}>
                {busy ? 'Encerrando…' : 'Confirmar encerramento'}
              </button>
              <button
                className="button secondary"
                disabled={busy}
                onClick={() => setConfirming(false)}
              >
                Cancelar
              </button>
            </div>
          </div>
        </Modal>
      )}
    </>
  )
}
