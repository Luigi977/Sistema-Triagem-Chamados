import { Link } from 'react-router-dom'
import type { Chamado } from '../../types/api'
import { date } from '../../utils/presentation'
import { Badge } from '../ui/States'
export function ChamadosTable({ chamados, returnTo }: { chamados: Chamado[]; returnTo: string }) {
  return (
    <div className="table-scroll">
      <table>
        <caption className="sr-only">Chamados por data de abertura decrescente</caption>
        <thead>
          <tr>
            <th>Chamado / Cliente</th>
            <th>Natureza</th>
            <th>Prioridade final</th>
            <th>Sugerida</th>
            <th>Equipe / Responsável</th>
            <th>Status</th>
            <th>Abertura</th>
          </tr>
        </thead>
        <tbody>
          {chamados.map((chamado) => (
            <tr key={chamado.id}>
              <td>
                <Link className="ticket-link" to={`/chamados/${chamado.id}`} state={{ returnTo }}>
                  #{chamado.id} · {chamado.nomeCliente || 'Nome não informado'}
                </Link>
                <span className="table-summary">{chamado.resumo || 'Sem resumo'}</span>
                <small className="muted">
                  {chamado.telefoneCliente || 'Contato sem telefone disponível'}
                </small>
              </td>
              <td>
                <Badge value={chamado.natureza} />
              </td>
              <td>
                <Badge value={chamado.prioridadeFinal} />
              </td>
              <td>
                <Badge value={chamado.prioridadeSugerida} />
              </td>
              <td>
                {chamado.equipe || 'Sem equipe'}
                <small className="table-summary">{chamado.responsavel || 'Não atribuído'}</small>
              </td>
              <td>
                <Badge value={chamado.status} />
              </td>
              <td className="date-cell">{date(chamado.dataAbertura)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
