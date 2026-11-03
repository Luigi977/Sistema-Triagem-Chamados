import { act, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { Filters } from '../src/components/chamados/Filters'
import { Pagination } from '../src/components/chamados/Pagination'
import { TriagemPanel } from '../src/components/triagem/TriagemPanel'
import { recommendationFixture } from '../src/mocks/triagem'
import { useAtendimento, reconcileMessages } from '../src/hooks/useAtendimento'
import { useQuery } from '../src/hooks/useQuery'
import { parsePage } from '../src/services/chamadoService'
import { chamado, message, page } from './fixtures'
afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
})
it('mudar filtros reinicia página e preserva os valores enviados ao servidor', async () => {
  const change = vi.fn()
  render(<Filters params={new URLSearchParams('page=4&status=ABERTO')} onChange={change} />)
  await userEvent.selectOptions(screen.getByLabelText('Status'), 'ENCERRADO')
  await userEvent.type(screen.getByLabelText('Telefone'), '5500000')
  await userEvent.click(screen.getByRole('button', { name: 'Filtrar' }))
  const params = change.mock.calls[0][0] as URLSearchParams
  expect(params.get('page')).toBe('0')
  expect(params.get('status')).toBe('ENCERRADO')
  expect(params.get('telefone')).toBe('5500000')
})
it('paginação usa a página pedida sem ordenar os resultados localmente', async () => {
  const change = vi.fn()
  render(<Pagination page={1} totalPages={3} total={25} onChange={change} />)
  await userEvent.click(screen.getByLabelText('Próxima página'))
  expect(change).toHaveBeenCalledWith(2)
})
it('edição, cancelamento e aceite de triagem ficam locais e preservam original', async () => {
  const fetcher = vi.fn()
  vi.stubGlobal('fetch', fetcher)
  render(<TriagemPanel fixture={structuredClone(recommendationFixture)} />)
  await userEvent.click(screen.getByRole('button', { name: 'Editar' }))
  await userEvent.selectOptions(screen.getByLabelText('Prioridade'), 'CRITICA')
  await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
  expect(screen.getByText('Alta')).toBeVisible()
  expect(screen.queryByText('Crítica')).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Editar' }))
  await userEvent.selectOptions(screen.getByLabelText('Prioridade'), 'BAIXA')
  await userEvent.click(screen.getByRole('button', { name: 'Aplicar edição local' }))
  await userEvent.click(screen.getByRole('button', { name: 'Aceitar' }))
  expect(screen.getByRole('status')).toHaveTextContent(
    'Aceite demonstrativo; não salvo no servidor',
  )
  expect(recommendationFixture.prioridade).toBe('ALTA')
  expect(fetcher).not.toHaveBeenCalled()
})
it('atendimento operacional não mostra fixture mesmo quando existe demonstração', () => {
  render(<TriagemPanel />)
  expect(screen.getByText('Triagem automática ainda não disponível')).toBeVisible()
  expect(screen.queryByText(/DEMO-LAB/)).toBeNull()
})
it('falha no envio preserva rascunho, orienta conferência e não repete POST', async () => {
  const fetcher = vi.fn(async (url: string, init?: RequestInit) => {
    if (init?.method === 'POST') return new Response(null, { status: 504 })
    return new Response(
      JSON.stringify(url.endsWith('/mensagens') || url.endsWith('/operacoes') ? [] : chamado),
      { headers: { 'Content-Type': 'application/json' } },
    )
  })
  vi.stubGlobal('fetch', fetcher)
  const { result } = renderHook(() => useAtendimento('701'))
  await waitFor(() => expect(result.current.chamado.data).toBeDefined())
  act(() => result.current.setDraft('Meu rascunho de laboratório'))
  await act(async () => {
    await result.current.send()
  })
  expect(result.current.draft).toBe('Meu rascunho de laboratório')
  expect(result.current.sendError).toContain('Resultado incerto')
  expect(result.current.feedback).toBe('')
  expect(fetcher.mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(1)
})
it('resposta aceita e polling reconciliam o mesmo ID apenas uma vez', () => {
  expect(reconcileMessages([message], [message])).toEqual([message])
})
it('resposta antiga de filtro cancelado não sobrescreve a consulta nova', async () => {
  let release: (value: string) => void = () => undefined
  const old = vi.fn(
    () =>
      new Promise<string>((resolve) => {
        release = resolve
      }),
  )
  const next = vi.fn(async () => 'consulta nova')
  const { result, rerender } = renderHook(({ loader }) => useQuery(loader), {
    initialProps: { loader: old as (signal: AbortSignal) => Promise<string> },
  })
  rerender({ loader: next })
  await waitFor(() => expect(result.current.data).toBe('consulta nova'))
  await act(async () => {
    release('consulta antiga')
  })
  expect(result.current.data).toBe('consulta nova')
})
it('não sobrepõe polling e suspende consultas quando a página está oculta', async () => {
  vi.useFakeTimers()
  let release: (value: string) => void = () => undefined
  const loader = vi.fn(
    () =>
      new Promise<string>((resolve) => {
        release = resolve
      }),
  )
  renderHook(() => useQuery(loader, 5000))
  await act(async () => {
    await vi.advanceTimersByTimeAsync(10000)
  })
  expect(loader).toHaveBeenCalledOnce()
  await act(async () => {
    release('ok')
  })
  Object.defineProperty(document, 'hidden', { configurable: true, value: true })
  await act(async () => {
    await vi.advanceTimersByTimeAsync(5000)
  })
  expect(loader).toHaveBeenCalledOnce()
  Object.defineProperty(document, 'hidden', { configurable: true, value: false })
  fireEvent(document, new Event('visibilitychange'))
  expect(loader).toHaveBeenCalledTimes(2)
})
it('valida o envelope Spring e rejeita um array sem paginação', () => {
  expect(parsePage(page([chamado])).totalElements).toBe(1)
  expect(() => parsePage([] as unknown as ReturnType<typeof page>)).toThrow(
    'paginação incompatível',
  )
})
