import { useCallback, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { Plus, RefreshCw, ListFilter } from 'lucide-react'
import { useAuth } from '../auth/context'
import { useQuery } from '../hooks/useQuery'
import { useReferences } from '../hooks/useReferences'
import { chamadoService } from '../services/chamadoService'
import { ChamadosTable } from '../components/chamados/ChamadosTable'
import { Filters } from '../components/chamados/Filters'
import { Pagination } from '../components/chamados/Pagination'
import { ChamadoForm } from '../components/chamados/ChamadoForm'
import { Modal } from '../components/ui/Modal'
import { Empty, ErrorState, Loading, PageTitle } from '../components/ui/States'
import type { CriarChamado } from '../types/api'
export function ChamadosPage({ dashboard = false }: { dashboard?: boolean }) {
  const [params, setParams] = useSearchParams()
  const location = useLocation()
  const navigate = useNavigate()
  const { tecnico } = useAuth()
  const references = useReferences()
  const [creating, setCreating] = useState(false)
  const page = Math.max(0, Number.parseInt(params.get('page') ?? '0', 10) || 0)
  const size = [10, 20, 50].includes(Number(params.get('size'))) ? Number(params.get('size')) : 10
  const queryParams = new URLSearchParams({
    page: String(page),
    size: String(size),
    sort: 'dataAbertura,desc',
  })
  for (const name of ['status', 'telefone', 'prioridadeFinal', 'responsavel']) {
    const value = params.get(name)
    if (value) queryParams.set(name, value)
  }
  const query = queryParams.toString()
  const queue = useQuery(
    useCallback(
      (signal: AbortSignal) => chamadoService.list(new URLSearchParams(query), signal),
      [query],
    ),
  )
  const returnTo = location.pathname + location.search
  async function create(body: CriarChamado) {
    const created = await chamadoService.create(body)
    queue.reload()
    navigate(`/chamados/${created.id}`, { state: { returnTo } })
  }
  return (
    <>
      <PageTitle
        title={dashboard ? 'Visão geral' : 'Fila de chamados'}
        description={
          dashboard
            ? 'Acompanhe a fila real e mantenha os atendimentos em movimento.'
            : 'Encontre, organize e acompanhe os chamados da central.'
        }
      >
        <button className="button" onClick={() => setCreating(true)}>
          <Plus size={17} />
          Novo chamado
        </button>
      </PageTitle>
      {dashboard && (
        <div className="metrics">
          <section className="card metric">
            <span>Total desta consulta</span>
            <strong>{queue.data?.totalElements ?? '—'}</strong>
            <small>Considera os filtros abaixo</small>
          </section>
          {[
            { title: 'Distribuição por natureza', text: 'Depende de indicador global na API' },
            { title: 'Variação por horário', text: 'Depende de consulta por período' },
            { title: 'Pendências da IA', text: 'Integração ainda não disponível' },
          ].map((item) => (
            <section key={item.title} className="card metric">
              <span>{item.title}</span>
              <strong className="metric-unavailable">Indisponível</strong>
              <small>{item.text}</small>
            </section>
          ))}
        </div>
      )}
      <section className="card queue">
        <div className="section-header">
          <div>
            <h2>
              <ListFilter size={19} />
              {dashboard ? 'Fila de chamados' : 'Todos os chamados'}
            </h2>
            <p>Selecione um chamado para abrir o atendimento.</p>
          </div>
          <button className="icon-button" onClick={queue.reload} aria-label="Atualizar fila">
            <RefreshCw size={17} />
          </button>
        </div>
        <Filters params={params} onChange={setParams} />
        <div className="queue-order">Mais recentes primeiro · ordenação no servidor</div>
        {queue.loading && <Loading />}
        {queue.error && <ErrorState error={queue.error} retry={queue.reload} />}
        {queue.data && (
          <>
            {queue.data.content.length ? (
              <ChamadosTable chamados={queue.data.content} returnTo={returnTo} />
            ) : (
              <Empty>
                Nenhum chamado nesta consulta. Altere os filtros ou abra um novo chamado.
              </Empty>
            )}
            <Pagination
              page={queue.data.number}
              totalPages={queue.data.totalPages}
              total={queue.data.totalElements}
              onChange={(next) => {
                const updated = new URLSearchParams(params)
                updated.set('page', String(next))
                setParams(updated)
              }}
            />
          </>
        )}
      </section>
      {creating && tecnico && (
        <Modal title="Abrir chamado manual" onClose={() => setCreating(false)}>
          {references.teams.error && (
            <ErrorState error={references.teams.error} retry={references.teams.reload} />
          )}
          <ChamadoForm tecnico={tecnico} teams={references.teams.data ?? []} onSubmit={create} />
        </Modal>
      )}
    </>
  )
}
