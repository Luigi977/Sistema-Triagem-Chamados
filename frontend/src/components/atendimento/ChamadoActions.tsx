import { useState, type FormEvent } from 'react'
import type { Chamado, Detalhes, Natureza, Prioridade, Status, Tecnico } from '../../types/api'
import { natures, priorities, statuses } from '../../types/api'
import { label } from '../../utils/presentation'
import { errorMessage } from '../../services/api'
import { ErrorState } from '../ui/States'
export type Action =
  | { kind: 'status'; value: Exclude<Status, 'ENCERRADO'> }
  | { kind: 'priority'; value: Prioridade }
  | { kind: 'assign'; value: number }
  | { kind: 'team'; value: string }
  | { kind: 'details'; value: Detalhes }
export function ChamadoActions({
  chamado,
  admin,
  technicians,
  teams,
  onAction,
}: {
  chamado: Chamado
  admin: boolean
  technicians: Tecnico[]
  teams: string[]
  onAction: (action: Action) => Promise<void>
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>, kind: Action['kind']) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const value = String(data.get('value'))
    let action: Action
    if (kind === 'details') {
      const optional = (name: string) => String(data.get(name) ?? '').trim() || undefined
      action = {
        kind,
        value: {
          resumo: optional('resumo'),
          descricao: optional('descricao'),
          natureza: optional('natureza') as Natureza | undefined,
          setor: optional('setor'),
          canal: optional('canal') as Detalhes['canal'],
        },
      }
    } else if (kind === 'assign') action = { kind, value: Number(value) }
    else if (kind === 'status') action = { kind, value: value as Exclude<Status, 'ENCERRADO'> }
    else if (kind === 'priority') action = { kind, value: value as Prioridade }
    else action = { kind, value }
    setBusy(true)
    setError('')
    setSuccess('')
    try {
      await onAction(action)
      setSuccess('Alteração salva.')
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setBusy(false)
    }
  }
  return (
    <section className="card stack">
      <h2>Ações do atendimento</h2>
      {error && <ErrorState error={error} />}
      {success && (
        <div role="status" className="notice success">
          {success}
        </div>
      )}
      <fieldset disabled={busy} className="stack">
        <form className="inline-form" onSubmit={(event) => void submit(event, 'status')}>
          <label>
            Status
            <select name="value" defaultValue={chamado.status} key={`status-${chamado.status}`}>
              {statuses
                .filter((value) => value !== 'ENCERRADO')
                .map((value) => (
                  <option key={value} value={value}>
                    {label(value)}
                  </option>
                ))}
            </select>
          </label>
          <button className="button secondary">Salvar status</button>
        </form>
        <form className="inline-form" onSubmit={(event) => void submit(event, 'priority')}>
          <label>
            Prioridade final
            <select
              required
              name="value"
              defaultValue={chamado.prioridadeFinal ?? ''}
              key={`priority-${chamado.prioridadeFinal}`}
            >
              <option value="" disabled>
                Selecione
              </option>
              {priorities.map((value) => (
                <option key={value} value={value}>
                  {label(value)}
                </option>
              ))}
            </select>
          </label>
          <button className="button secondary">Salvar prioridade</button>
        </form>
        <form className="inline-form" onSubmit={(event) => void submit(event, 'assign')}>
          <label>
            Responsável
            <select required name="value" defaultValue="">
              <option value="" disabled>
                Selecione um técnico
              </option>
              {technicians.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.nome}
                </option>
              ))}
            </select>
          </label>
          <button className="button secondary" disabled={!technicians.length}>
            Atribuir
          </button>
        </form>
        {admin && (
          <form className="inline-form" onSubmit={(event) => void submit(event, 'team')}>
            <label>
              Encaminhar para equipe
              <select required name="value" defaultValue="">
                <option value="" disabled>
                  Selecione
                </option>
                {teams.map((team) => (
                  <option key={team}>{team}</option>
                ))}
              </select>
            </label>
            <button className="button secondary">Encaminhar</button>
            <small className="muted full">Mudar a equipe remove o responsável atual.</small>
          </form>
        )}
        <details>
          <summary>Editar detalhes do chamado</summary>
          <form className="stack details-form" onSubmit={(event) => void submit(event, 'details')}>
            <label>
              Resumo
              <input name="resumo" maxLength={200} defaultValue={chamado.resumo ?? ''} />
            </label>
            <label>
              Descrição
              <textarea name="descricao" maxLength={10000} defaultValue={chamado.descricao ?? ''} />
            </label>
            <label>
              Natureza
              <select name="natureza" defaultValue={chamado.natureza ?? ''}>
                <option value="">Manter atual</option>
                {natures.map((value) => (
                  <option key={value} value={value}>
                    {label(value)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Setor
              <input name="setor" maxLength={150} defaultValue={chamado.setor ?? ''} />
            </label>
            <label>
              Canal
              <select name="canal" defaultValue={chamado.canal ?? ''}>
                <option value="">Manter atual</option>
                <option value="MANUAL">Manual</option>
                <option value="WHATSAPP">WhatsApp</option>
              </select>
            </label>
            <p className="small muted">Campos em branco preservam o valor atual.</p>
            <button className="button">Salvar detalhes</button>
          </form>
        </details>
      </fieldset>
    </section>
  )
}
