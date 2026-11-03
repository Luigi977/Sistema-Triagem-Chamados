import type { FormEvent } from 'react'
import { Search } from 'lucide-react'
import { priorities, statuses } from '../../types/api'
import { label } from '../../utils/presentation'
export function Filters({
  params,
  onChange,
}: {
  params: URLSearchParams
  onChange: (params: URLSearchParams) => void
}) {
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const next = new URLSearchParams(params)
    for (const name of ['status', 'telefone', 'prioridadeFinal', 'responsavel', 'size']) {
      const value = String(data.get(name) ?? '').trim()
      if (value) next.set(name, value)
      else next.delete(name)
    }
    next.set('page', '0')
    onChange(next)
  }
  return (
    <form className="filters" onSubmit={submit} key={params.toString()}>
      <label>
        Status
        <select name="status" defaultValue={params.get('status') ?? ''}>
          <option value="">Todos os status</option>
          {statuses.map((value) => (
            <option key={value} value={value}>
              {label(value)}
            </option>
          ))}
        </select>
      </label>
      <label>
        Telefone
        <input
          name="telefone"
          type="search"
          defaultValue={params.get('telefone') ?? ''}
          placeholder="Buscar telefone"
        />
      </label>
      <label>
        Prioridade final
        <select name="prioridadeFinal" defaultValue={params.get('prioridadeFinal') ?? ''}>
          <option value="">Todas</option>
          {priorities.map((value) => (
            <option key={value} value={value}>
              {label(value)}
            </option>
          ))}
        </select>
      </label>
      <label>
        Responsável
        <input
          name="responsavel"
          defaultValue={params.get('responsavel') ?? ''}
          placeholder="Nome do técnico"
        />
      </label>
      <label>
        Por página
        <select name="size" defaultValue={params.get('size') ?? '10'}>
          {[10, 20, 50].map((size) => (
            <option key={size}>{size}</option>
          ))}
        </select>
      </label>
      <button className="button" type="submit">
        <Search size={16} />
        Filtrar
      </button>
      <button className="text-button" type="button" onClick={() => onChange(new URLSearchParams())}>
        Limpar
      </button>
    </form>
  )
}
