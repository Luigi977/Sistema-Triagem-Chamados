import { test, expect, type Page } from '@playwright/test'
async function login(page: Page, loginName = 'frontend-laboratorio') {
  await page.getByLabel('Login', { exact: true }).fill(loginName)
  await page.getByLabel('Senha', { exact: true }).fill(process.env.FRONTEND_TEST_PASSWORD!)
  await page.getByRole('button', { name: 'Entrar na central' }).click()
}
test('login, paginação Spring real, criação, rascunho, encerramento e histórico persistido', async ({
  page,
}) => {
  const listResponse = page.waitForResponse(
    (response) => new URL(response.url()).pathname === '/api/chamados' && response.status() === 200,
  )
  const sessionResponse = page.waitForResponse(
    (response) => response.url().endsWith('/api/auth/login') && response.status() === 200,
  )
  await page.goto('/chamados?status=ABERTO')
  await expect(page).toHaveURL(/login/)
  await login(page)
  await expect(page).toHaveURL(/chamados\?status=ABERTO/)
  const envelope = await (await listResponse).json()
  expect(envelope).toMatchObject({
    content: [],
    number: 0,
    size: 10,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  })
  await expect(page.getByText('Nenhum chamado nesta consulta.', { exact: false })).toBeVisible()
  await page.getByRole('button', { name: 'Novo chamado' }).click()
  await page.getByLabel('Telefone do cliente').fill('5500000000000')
  await page.getByLabel('Nome do cliente').fill('Cliente fictício de homologação')
  await page
    .getByLabel('Resumo', { exact: true })
    .fill(`Validação do frontend em banco isolado ${Date.now()}`)
  await page.getByRole('combobox', { name: 'Equipe', exact: true }).selectOption('N1')
  await page.getByRole('button', { name: 'Criar chamado', exact: true }).click()
  await expect(page.getByRole('heading', { name: /^Atendimento #/ })).toBeVisible()
  const ticketUrl = page.url()
  const ticketId = new URL(ticketUrl).pathname.split('/').at(-1)
  const session = await (await sessionResponse).json()
  await page.getByRole('combobox', { name: 'Status', exact: true }).selectOption('EM_ATENDIMENTO')
  await page.getByRole('button', { name: 'Salvar status', exact: true }).click()
  await expect(page.locator('.badge').filter({ hasText: /^Em atendimento$/ })).toBeVisible()
  await page.getByRole('combobox', { name: 'Prioridade final', exact: true }).selectOption('ALTA')
  await page.getByRole('button', { name: 'Salvar prioridade', exact: true }).click()
  await expect(page.locator('dd').filter({ hasText: /^Alta$/ })).toBeVisible()
  await expect(page.getByText('Triagem automática ainda não disponível')).toBeVisible()
  await page
    .getByLabel('Resposta pelo WhatsApp')
    .fill('Mensagem sintética: não deve chegar a nenhum telefone.')
  const incoming = await page.request.post(`/api/chamados/${ticketId}/mensagens`, {
    headers: { Authorization: `Bearer ${session.token}` },
    data: {
      remetente: 'CLIENTE',
      conteudo: 'Mensagem sintética recebida pelo polling.\n<b>Texto preservado sem HTML</b>',
      tipo: 'TEXTO',
    },
  })
  expect(incoming.status()).toBe(201)
  await expect(
    page.getByRole('log').getByText('Mensagem sintética recebida pelo polling.', { exact: false }),
  ).toBeVisible({ timeout: 10000 })
  await expect(page.getByRole('log').locator('b')).toHaveCount(0)
  await expect(page.getByLabel('Resposta pelo WhatsApp')).toHaveValue(
    'Mensagem sintética: não deve chegar a nenhum telefone.',
  )
  await page.evaluate(() => {
    ;(document.activeElement as HTMLElement)?.blur()
    window.scrollTo(0, 0)
  })
  await page.screenshot({ path: 'test-results/atendimento-1366.png', fullPage: true })
  await page.getByRole('button', { name: 'Enviar resposta' }).click()
  await expect(page.getByRole('alert')).toContainText('Serviço indisponível')
  await expect(page.getByLabel('Resposta pelo WhatsApp')).toHaveValue(
    'Mensagem sintética: não deve chegar a nenhum telefone.',
  )
  await page.getByRole('link', { name: 'Encerrar', exact: true }).click()
  await page.getByLabel('Causa real identificada').fill('Causa fictícia confirmada em homologação')
  await page.getByLabel('Solução aplicada').fill('Solução fictícia aplicada no laboratório')
  await page.screenshot({ path: 'test-results/encerramento-1366.png', fullPage: true })
  await page.getByRole('button', { name: 'Registrar encerramento' }).click()
  await page.getByRole('button', { name: 'Confirmar encerramento', exact: true }).click()
  await expect(page).toHaveURL(ticketUrl)
  await expect(
    page.getByText('Chamado encerrado. Atendimento e histórico disponíveis para leitura.'),
  ).toBeVisible()
  await expect(page.getByRole('button', { name: 'Enviar resposta' })).toBeDisabled()
  await expect(
    page.getByText('Causa fictícia confirmada em homologação', { exact: true }),
  ).toBeVisible()
  await page.reload()
  await expect(page).toHaveURL(/login/)
  await login(page)
  await expect(page).toHaveURL(ticketUrl)
  await expect(
    page.getByText('Solução fictícia aplicada no laboratório', { exact: true }),
  ).toBeVisible()
  await expect(page.getByText('ENCERRAMENTO', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Sair da conta' }).click()
  await expect(page).toHaveURL(/login/)
})
test('senha inválida e página 404 navegável', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('Login', { exact: true }).fill('frontend-laboratorio')
  await page.getByLabel('Senha', { exact: true }).fill('senha-invalida')
  await page.getByRole('button', { name: 'Entrar na central' }).click()
  await expect(page.getByRole('alert')).toContainText('credenciais inválidas')
  await login(page)
  await page.goto('/rota-ausente')
  await login(page)
  await expect(page.getByText('Página não encontrada', { exact: true })).toBeVisible()
})
test('sessão revogada no backend retorna ao login na próxima consulta', async ({ page }) => {
  await page.goto('/login')
  const response = page.waitForResponse(
    (r) => r.url().endsWith('/api/auth/login') && r.status() === 200,
  )
  await login(page)
  await expect(page.getByRole('heading', { name: 'Visão geral', exact: true })).toBeVisible()
  const session = await (await response).json()
  await page.request.post('/api/auth/logout', {
    headers: { Authorization: `Bearer ${session.token}` },
  })
  await page.getByRole('link', { name: 'Atendimento', exact: true }).click()
  await expect(page).toHaveURL(/login/)
})

test('recurso ausente apresenta 404 da API com nova tentativa', async ({ page }) => {
  await page.goto('/chamados/9223372036854775806')
  await login(page)
  await expect(page.getByRole('alert')).toContainText('Recurso não encontrado')
  await expect(page.getByRole('button', { name: 'Tentar novamente' })).toBeVisible()
})

test('mudança concorrente de equipe e encerramento bloqueiam edição e preservam rascunho', async ({
  page,
}) => {
  const adminResponse = await page.request.post('/api/auth/login', {
    data: { login: 'frontend-laboratorio', senha: process.env.FRONTEND_TEST_PASSWORD },
  })
  expect(adminResponse.status()).toBe(200)
  const adminSession = await adminResponse.json()
  const headers = { Authorization: `Bearer ${adminSession.token}` }
  const loginName = `laboratorio-${Date.now()}`
  const technicianResponse = await page.request.post('/api/tecnicos', {
    headers,
    data: {
      login: loginName,
      senha: process.env.FRONTEND_TEST_PASSWORD,
      nome: 'Técnico fictício N1',
      perfil: 'TECNICO',
      equipe: 'N1',
    },
  })
  expect(technicianResponse.status()).toBe(201)
  const technician = await technicianResponse.json()
  const creation = await page.request.post('/api/chamados', {
    headers,
    data: {
      telefoneCliente: '5500000000000',
      nomeCliente: 'Cliente fictício concorrência',
      resumo: 'Mudança de equipe no laboratório',
      equipe: 'N1',
      canal: 'MANUAL',
    },
  })
  expect(creation.status()).toBe(201)
  const ticket = await creation.json()
  expect(
    (
      await page.request.patch(`/api/chamados/${ticket.id}/responsavel`, {
        headers,
        data: { responsavelId: technician.id },
      })
    ).status(),
  ).toBe(200)
  await page.goto(`/chamados/${ticket.id}`)
  const technicianSessionResponse = page.waitForResponse(
    (r) => r.url().endsWith('/api/auth/login') && r.status() === 200,
  )
  await login(page, loginName)
  await expect(page.getByLabel('Resposta pelo WhatsApp')).toBeEnabled()
  await page.getByLabel('Resposta pelo WhatsApp').fill('Rascunho preservado após mudança de equipe')
  expect(
    (
      await page.request.patch(`/api/chamados/${ticket.id}/equipe`, {
        headers,
        data: { equipe: 'N2' },
      })
    ).status(),
  ).toBe(200)
  await expect(page.getByLabel('Resposta pelo WhatsApp')).toBeDisabled({ timeout: 10000 })
  await expect(page.getByLabel('Resposta pelo WhatsApp')).toHaveValue(
    'Rascunho preservado após mudança de equipe',
  )
  const technicianSession = await (await technicianSessionResponse).json()
  expect(
    (
      await page.request.patch(`/api/chamados/${ticket.id}/status`, {
        headers: { Authorization: `Bearer ${technicianSession.token}` },
        data: { status: 'EM_ATENDIMENTO' },
      })
    ).status(),
  ).toBe(403)
  expect(
    (
      await page.request.post(`/api/chamados/${ticket.id}/encerrar`, {
        headers,
        data: {
          causaReal: 'Causa fictícia concorrente',
          solucaoAplicada: 'Solução fictícia concorrente',
        },
      })
    ).status(),
  ).toBe(200)
  await expect(
    page.getByText('Chamado encerrado. Atendimento e histórico disponíveis para leitura.'),
  ).toBeVisible({ timeout: 10000 })
  await expect(page.getByLabel('Resposta pelo WhatsApp')).toHaveValue(
    'Rascunho preservado após mudança de equipe',
  )
  await page.request.post('/api/auth/logout', { headers })
})

test('demonstração de triagem e avaliação não faz nenhuma gravação HTTP', async ({ page }) => {
  await page.goto('/triagem')
  await login(page)
  const writes: string[] = []
  page.on('request', (request) => {
    if (request.url().includes('/api/') && request.method() !== 'GET') writes.push(request.url())
  })
  await expect(page.getByText('DEMONSTRAÇÃO · Dados simulados', { exact: true })).toBeVisible()
  await page.screenshot({ path: 'test-results/triagem-1366.png', fullPage: true })
  await page.getByRole('button', { name: 'Editar', exact: true }).click()
  await page.getByRole('combobox', { name: 'Prioridade', exact: true }).selectOption('BAIXA')
  await page.getByRole('button', { name: 'Cancelar', exact: true }).click()
  await page.getByRole('button', { name: 'Aceitar', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Aceite demonstrativo')
  await page.getByRole('link', { name: 'Avaliação da IA' }).click()
  await page.getByRole('button', { name: 'Parcialmente útil' }).click()
  await page.screenshot({ path: 'test-results/avaliacao-1366.png', fullPage: true })
  await expect(page.getByRole('status')).toContainText('Não salva no servidor')
  expect(writes).toEqual([])
})
for (const width of [390, 768, 1366])
  test(`layout utilizável em ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/')
    await login(page)
    await expect(page.getByRole('heading', { name: 'Visão geral', exact: true })).toBeVisible()
    await page.screenshot({ path: `test-results/dashboard-${width}.png`, fullPage: true })
    if (width < 768) {
      await page.getByRole('button', { name: 'Abrir menu' }).click()
      await page.getByRole('link', { name: 'Atendimento', exact: true }).click()
    } else await page.getByRole('link', { name: 'Atendimento', exact: true }).click()
    await expect(page.getByRole('heading', { name: 'Fila de chamados' })).toBeVisible()
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true)
    await page.screenshot({ path: `test-results/fila-${width}.png`, fullPage: true })
  })
