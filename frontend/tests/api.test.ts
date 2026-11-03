import { afterEach, expect, it, vi } from 'vitest'
import { api, ApiError, setSessionToken, setUnauthorizedHandler } from '../src/services/api'
afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
  setSessionToken(null)
  setUnauthorizedHandler()
})
it('envia Bearer e JSON somente no corpo, sem expor credenciais na URL', async () => {
  const fetcher = vi
    .fn()
    .mockResolvedValue(
      new Response('{"id":1}', { headers: { 'Content-Type': 'application/json' } }),
    )
  vi.stubGlobal('fetch', fetcher)
  setSessionToken('token-sintetico')
  expect(await api('/chamados', { method: 'POST', body: { resumo: 'Teste' } })).toEqual({ id: 1 })
  expect(fetcher).toHaveBeenCalledWith(
    '/api/chamados',
    expect.objectContaining({
      method: 'POST',
      body: '{"resumo":"Teste"}',
      headers: expect.objectContaining({ Authorization: 'Bearer token-sintetico' }),
    }),
  )
})
it('lê texto e resposta 204 vazia', async () => {
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValueOnce(new Response('ok'))
      .mockResolvedValueOnce(new Response(null, { status: 204 })),
  )
  expect(await api('/texto')).toBe('ok')
  expect(await api('/vazio')).toBeUndefined()
})
it('401 encerra sessão, exceto na tentativa de login anônima', async () => {
  const clear = vi.fn()
  setUnauthorizedHandler(clear)
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 401 })))
  await expect(api('/auth/login', { anonymous: true })).rejects.toMatchObject({ status: 401 })
  expect(clear).not.toHaveBeenCalled()
  await expect(api('/auth/me')).rejects.toMatchObject({ status: 401 })
  expect(clear).toHaveBeenCalledOnce()
})
it.each([400, 403, 404, 409, 502, 503, 504])(
  'normaliza erro %s sem renderizar HTML recebido',
  async (status) => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response('<script>privado</script>', { status })),
    )
    try {
      await api('/chamados')
    } catch (cause) {
      expect(cause).toBeInstanceOf(ApiError)
      expect((cause as Error).message).not.toContain('script')
    }
  },
)
it('timeout de POST tem resultado incerto e nunca repete automaticamente', async () => {
  vi.useFakeTimers()
  const fetcher = vi.fn(
    (_url: string, init: RequestInit) =>
      new Promise((_resolve, reject) =>
        init.signal?.addEventListener('abort', () =>
          reject(new DOMException('abort', 'AbortError')),
        ),
      ),
  )
  vi.stubGlobal('fetch', fetcher)
  const promise = expect(
    api('/chamados/1/responder', { method: 'POST', body: { texto: 'Teste' }, timeout: 100 }),
  ).rejects.toMatchObject({ uncertain: true })
  await vi.advanceTimersByTimeAsync(100)
  await promise
  await vi.advanceTimersByTimeAsync(10000)
  expect(fetcher).toHaveBeenCalledOnce()
})
it('propaga cancelamento sem transformá-lo em falha de rede', async () => {
  const controller = new AbortController()
  vi.stubGlobal(
    'fetch',
    vi.fn(
      (_url: string, init: RequestInit) =>
        new Promise((_resolve, reject) =>
          init.signal?.addEventListener('abort', () =>
            reject(new DOMException('abort', 'AbortError')),
          ),
        ),
    ),
  )
  const request = api('/chamados', { signal: controller.signal })
  controller.abort()
  await expect(request).rejects.toMatchObject({ name: 'AbortError' })
})
