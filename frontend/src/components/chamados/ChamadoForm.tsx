import { useState, type FormEvent } from 'react'
import type { CriarChamado, Natureza, Prioridade, Tecnico } from '../../types/api'
import { natures, priorities } from '../../types/api'
import { label } from '../../utils/presentation'
import { errorMessage } from '../../services/api'
import { ErrorState } from '../ui/States'
export function ChamadoForm({
  tecnico,
  teams,
  onSubmit,
}: {
  tecnico: Tecnico
  teams: string[]
  onSubmit: (body: CriarChamado) => Promise<void>
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const field = (name: string) => String(data.get(name) ?? '').trim() || undefined
    setBusy(true)
    setError('')
    try {
      await onSubmit({
        telefoneCliente: field('telefoneCliente') ?? '',
        nomeCliente: field('nomeCliente'),
        resumo: field('resumo'),
        descricao: field('descricao'),
        natureza: field('natureza') as Natureza | undefined,
        setor: field('setor'),
        prioridadeSugerida: field('prioridadeSugerida') as Prioridade | undefined,
        equipe: tecnico.perfil === 'ADMIN' ? field('equipe') : tecnico.equipe,
        canal: 'MANUAL',
      })
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setBusy(false)
    }
  }
  return (
    <form onSubmit={(event) => void submit(event)} className="stack">
      <p className="muted">
        Abertura pelo canal Manual. A prioridade sugerida será informada por você.
      </p>
      {error && <ErrorState error={error} />}
      <fieldset disabled={busy} className="form-grid">
        <label>
          Telefone do cliente *<input name="telefoneCliente" required maxLength={30} type="tel" />
        </label>
        <label>
          Nome do cliente
          <input name="nomeCliente" maxLength={150} />
        </label>
        <label className="full">
          Resumo
          <input name="resumo" maxLength={200} />
        </label>
        <label className="full">
          Descrição
          <textarea name="descricao" maxLength={10000} rows={3} />
        </label>
        <label>
          Natureza
          <select name="natureza">
            <option value="">Não informada</option>
            {natures.map((value) => (
              <option key={value} value={value}>
                {label(value)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Setor
          <input name="setor" maxLength={150} />
        </label>
        <label>
          Prioridade sugerida manualmente
          <select name="prioridadeSugerida">
            <option value="">Não informada</option>
            {priorities.map((value) => (
              <option key={value} value={value}>
                {label(value)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Equipe
          {tecnico.perfil === 'ADMIN' ? (
            <select name="equipe">
              <option value="">Sem equipe</option>
              {teams.map((value) => (
                <option key={value}>{value}</option>
              ))}
            </select>
          ) : (
            <input value={tecnico.equipe} readOnly />
          )}
        </label>
      </fieldset>
      <button className="button" disabled={busy}>
        {busy ? 'Criando…' : 'Criar chamado'}
      </button>
    </form>
  )
}
