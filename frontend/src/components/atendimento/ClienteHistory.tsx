import { Link } from 'react-router-dom'
import type { Chamado, Page } from '../../types/api'
import { Badge, Empty, ErrorState, Loading } from '../ui/States'
import { Pagination } from '../chamados/Pagination'
export function ClienteHistory({
  chamado,
  history,
  loading,
  error,
  retry,
  onPage,
  returnTo,
}: {
  chamado: Chamado
  history?: Page<Chamado>
  loading: boolean
  error?: string
  retry: () => void
  onPage: (page: number) => void
  returnTo: string
}) {
  return (
    <section className="card stack">
      <h2>Histórico do cliente</h2>
      {chamado.telefoneCliente ? (
        <>
          {loading && <Loading />}
          {error && <ErrorState error={error} retry={retry} />}
          {history && (
            <>
              <ul className="history-list">
                {history.content
                  .filter((item) => item.id !== chamado.id)
                  .map((item) => (
                    <li key={item.id}>
                      <Link to={`/chamados/${item.id}`} state={{ returnTo }}>
                        #{item.id} · {item.resumo || 'Sem resumo'}
                      </Link>
                      <Badge value={item.status} />
                    </li>
                  ))}
              </ul>
              {history.content.every((item) => item.id === chamado.id) && (
                <Empty>Sem outros chamados nesta página.</Empty>
              )}
              <Pagination
                page={history.number}
                totalPages={history.totalPages}
                total={history.totalElements}
                onChange={onPage}
              />
            </>
          )}
        </>
      ) : (
        <>
          <p className="muted">
            Contato sem telefone disponível. A API não oferece histórico completo por contato LID.
          </p>
          {chamado.chamadoAnteriorId && (
            <Link to={`/chamados/${chamado.chamadoAnteriorId}`} state={{ returnTo }}>
              Abrir chamado anterior #{chamado.chamadoAnteriorId}
            </Link>
          )}
        </>
      )}
    </section>
  )
}
