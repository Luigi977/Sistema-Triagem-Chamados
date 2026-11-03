import { useCallback, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { ArrowLeft, CheckCircle2, RefreshCw } from 'lucide-react'
import { useAtendimento } from '../hooks/useAtendimento'
import { useQuery } from '../hooks/useQuery'
import { usePermissions } from '../hooks/usePermissions'
import { useReferences } from '../hooks/useReferences'
import { useChamadoAction } from '../hooks/useChamadoAction'
import { useAuth } from '../auth/context'
import { chamadoService } from '../services/chamadoService'
import { date, label } from '../utils/presentation'
import { Badge, Empty, ErrorState, Loading, PageTitle } from '../components/ui/States'
import { Conversa } from '../components/atendimento/Conversa'
import { ClienteHistory } from '../components/atendimento/ClienteHistory'
import { ChamadoActions } from '../components/atendimento/ChamadoActions'
import { TriagemPanel } from '../components/triagem/TriagemPanel'
export function AtendimentoPage() {
  const { id = '' } = useParams()
  return <Atendimento key={id} id={id} />
}
function Atendimento({ id }: { id: string }) {
  const location = useLocation()
  const state = location.state as { returnTo?: string } | null
  const returnTo =
    state?.returnTo?.startsWith('/') && !state.returnTo.startsWith('//')
      ? state.returnTo
      : '/chamados'
  const controller = useAtendimento(id)
  const { chamado, messages, operations } = controller
  const ticket = chamado.data
  const { admin, canEdit } = usePermissions(ticket)
  const { tecnico } = useAuth()
  const references = useReferences()
  const action = useChamadoAction(Number(id), controller.refresh)
  const [historyPage, setHistoryPage] = useState(0)
  const phone = ticket?.telefoneCliente
  const history = useQuery(
    useCallback(
      async (signal: AbortSignal) =>
        phone ? chamadoService.history(phone, historyPage, signal) : undefined,
      [phone, historyPage],
    ),
  )
  const compatible = (references.technicians.data ?? []).filter(
    (t) => t.ativo && t.equipe === ticket?.equipe && (admin || t.id === tecnico?.id),
  )
  if (chamado.loading) return <Loading />
  if (!ticket)
    return <ErrorState error={chamado.error ?? 'Chamado indisponível.'} retry={chamado.reload} />
  return (
    <>
      <Link className="back-link" to={returnTo}>
        <ArrowLeft size={15} />
        Voltar à fila
      </Link>
      <PageTitle
        title={`Atendimento #${ticket.id}`}
        description={`${ticket.nomeCliente || 'Nome não informado'} · aberto em ${date(ticket.dataAbertura)}`}
      >
        <div className="actions">
          <Badge value={ticket.status} />
          <button
            className="icon-button"
            aria-label="Atualizar atendimento"
            onClick={controller.refresh}
          >
            <RefreshCw size={17} />
          </button>
          {canEdit && (
            <Link className="button" to={`/chamados/${ticket.id}/encerrar`} state={{ returnTo }}>
              <CheckCircle2 size={17} />
              Encerrar
            </Link>
          )}
        </div>
      </PageTitle>
      {chamado.error && <ErrorState error={chamado.error} retry={chamado.reload} />}
      {!canEdit && (
        <div className="notice">
          {ticket.status === 'ENCERRADO'
            ? 'Chamado encerrado. Atendimento e histórico disponíveis para leitura.'
            : 'Você pode consultar este chamado. Alterações dependem da equipe e do responsável atual.'}
        </div>
      )}
      <div className="attendance-grid">
        <div className="stack">
          <Conversa
            messages={controller.allMessages}
            loading={messages.loading}
            error={messages.error}
            retry={messages.reload}
            draft={controller.draft}
            onDraft={controller.setDraft}
            sending={controller.sending}
            canSend={canEdit}
            onSend={controller.send}
            sendError={controller.sendError}
            feedback={controller.feedback}
          />
          {canEdit && (
            <>
              {references.technicians.error && (
                <ErrorState
                  error={references.technicians.error}
                  retry={references.technicians.reload}
                />
              )}
              {references.teams.error && (
                <ErrorState error={references.teams.error} retry={references.teams.reload} />
              )}
              <ChamadoActions
                chamado={ticket}
                admin={admin}
                technicians={compatible}
                teams={references.teams.data ?? []}
                onAction={action}
              />
            </>
          )}
          <section className="card stack">
            <h2>Histórico de operações</h2>
            {operations.loading && <Loading />}
            {operations.error && <ErrorState error={operations.error} retry={operations.reload} />}
            {operations.data &&
              (operations.data.length ? (
                <ol className="operations">
                  {operations.data.map((op) => (
                    <li key={op.id}>
                      <strong>{label(op.acao)}</strong>
                      <span>
                        {date(op.dataOperacao)} ·{' '}
                        {references.technicians.data?.find((t) => t.id === op.autorId)?.nome ??
                          (op.autorId ? `Técnico #${op.autorId}` : 'Sistema')}
                      </span>
                    </li>
                  ))}
                </ol>
              ) : (
                <Empty>Nenhuma operação registrada.</Empty>
              ))}
          </section>
        </div>
        <aside className="stack">
          <section className="card stack">
            <h2>Dados do cliente e chamado</h2>
            <dl className="details-list">
              {[
                ['Cliente', ticket.nomeCliente],
                ['Telefone', ticket.telefoneCliente],
                ['Resumo', ticket.resumo],
                ['Descrição', ticket.descricao],
                ['Setor', ticket.setor],
                ['Natureza', label(ticket.natureza)],
                ['Prioridade final', label(ticket.prioridadeFinal)],
                ['Prioridade sugerida', label(ticket.prioridadeSugerida)],
                ['Equipe', ticket.equipe],
                ['Responsável', ticket.responsavel],
                ['Canal', label(ticket.canal)],
                ['Abertura', date(ticket.dataAbertura)],
                ...(ticket.status === 'ENCERRADO'
                  ? [
                      ['Encerramento', date(ticket.dataEncerramento)],
                      ['Causa real', ticket.causaReal],
                      ['Solução aplicada', ticket.solucaoAplicada],
                    ]
                  : []),
              ].map(([name, value]) => (
                <div key={name}>
                  <dt>{name}</dt>
                  <dd>{value || 'Não informado'}</dd>
                </div>
              ))}
            </dl>
          </section>
          <ClienteHistory
            chamado={ticket}
            history={history.data}
            loading={history.loading}
            error={history.error}
            retry={history.reload}
            onPage={setHistoryPage}
            returnTo={returnTo}
          />
          <TriagemPanel />
        </aside>
      </div>
    </>
  )
}
