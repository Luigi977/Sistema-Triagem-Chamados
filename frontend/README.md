# Frontend · Triagem TI

Aplicação React + TypeScript + Vite da etapa 05. Consome exclusivamente os contratos existentes do Spring Boot; triagem e avaliação simuladas são isoladas dos atendimentos reais.

## Requisitos e execução

- Node **24 ou superior** para a combinação de ferramentas deste projeto. Vite 8 requer Node 20.19+ ou 22.12+ ([documentação oficial](https://vite.dev/guide/)); este projeto fixa um requisito mais recente para manter as ferramentas alinhadas. Validação local com Node 24.19.0.
- npm e backend Java 21+ com banco configurado; consulte o [README do projeto](../README.md) e o [guia Docker](../docs/docker.md).
- No Windows com bloqueio de scripts PowerShell, use `npm.cmd` e `npx.cmd` nos comandos abaixo.

```powershell
cd frontend
npm ci
Copy-Item .env.example .env.local
npm run dev
```

Abra `http://localhost:5173`. Configure `.env.local` antes de iniciar:

| Variável              | Padrão                  | Uso                                                                                     |
| --------------------- | ----------------------- | --------------------------------------------------------------------------------------- |
| `DEV_API_TARGET`      | `http://localhost:8085` | Destino do proxy `/api` e `/health`; exclusiva do servidor Vite, ausente do bundle.     |
| `VITE_BACKEND_ORIGIN` | `http://localhost:8085` | Origem pública usada para abrir `/whatsapp.html` para administradores. Sem credenciais. |
| `VITE_ENABLE_AI_DEMO` | `false`                 | Habilita explicitamente as demonstrações de `/triagem` e `/avaliacao`.                  |

O backend tem porta padrão 8080 em `application.properties`. Para o destino inicial solicitado, inicie-o com `--server.port=8085`, ou ajuste as duas origens acima para a porta realmente utilizada. No Docker, confira a porta publicada. Não aponte o proxy para a porta da Evolution.

Exemplo de inicialização Java, depois de configurar o banco e as variáveis privadas de bootstrap conforme o guia do backend:

```powershell
cd backend
mvn spring-boot:run '-Dspring-boot.run.arguments=--server.port=8085'
```

O primeiro administrador é criado pelo backend somente quando não há técnicos e `TRIAGEM_ADMIN_LOGIN`/`TRIAGEM_ADMIN_PASSWORD` estão definidas em ambiente privado. O frontend não cadastra técnicos. Não coloque essas variáveis em arquivos `VITE_*`.

## Comandos

| Comando                                      | Resultado                                                                                       |
| -------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `npm run dev`                                | Vite separado com proxy de desenvolvimento.                                                     |
| `npm run build`                              | Verificação TypeScript e bundle em `dist/`.                                                     |
| `npm run preview`                            | Conferência local do build; não é hospedagem de produção nem inclui o proxy de desenvolvimento. |
| `npm run typecheck`                          | TypeScript estrito.                                                                             |
| `npm run lint`                               | ESLint e regras React Hooks.                                                                    |
| `npm test`                                   | Testes de HTTP, concorrência, polling, filtros, rascunho e demonstração.                        |
| `npm run test:watch`                         | Testes em modo contínuo.                                                                        |
| `npm run test:e2e` / `npm run test:e2e:live` | Navegador contra Spring Boot e H2 isolados, sem Evolution.                                      |
| `npm run format`                             | Formatação dos fontes e configurações.                                                          |

## Sessão e funcionamento

Login: `POST /api/auth/login`, identidade: `GET /api/auth/me`, saída: `POST /api/auth/logout`. O Bearer fica exclusivamente em memória. Recarregar pede novo login e volta à rota solicitada, com os filtros. Não há armazenamento de senha/token no navegador. Erro 401 remove a sessão local. Logout tenta revogar a sessão no servidor; se a comunicação falhar, a tela de login informa que só foi possível encerrar localmente.

Os services centralizam HTTP, timeout (15 segundos), cancelamento e mensagens para 400/401/403/404/409/502/503/504. GETs oferecem nova tentativa manual e polling. **POST/PATCH não são repetidos automaticamente.** Um timeout de escrita pode ter ocorrido depois de o servidor aceitar a operação: confira o histórico antes de reenviar.

A fila conserva filtros e página na URL, usa paginação real do Spring e `sort=dataAbertura,desc`. O contrato de paginação é validado no service. `totalElements` é apresentado somente como total da consulta corrente. Indicadores agregados sem endpoint ficam indisponíveis.

Atendimento consulta chamado, mensagens e auditoria independentemente, com polling de cinco segundos sem sobreposição, suspenso quando a aba está oculta e cancelado ao sair. O texto permanece durante polling e falhas. A mensagem retornada de `/responder` é reconciliada por ID com o próximo GET. Sucesso significa **aceitação pela Evolution**, sem comprovar entrega ou leitura. O endpoint de registro `/mensagens` não é usado para enviar respostas. Anexos são identificados como texto, sem download. Datas `LocalDateTime` são interpretadas no horário local do navegador sem acrescentar fuso `Z`; use o navegador no fuso operacional do backend nesta versão.

Técnicos editam somente chamados não encerrados, da própria equipe, livres ou atribuídos a eles. A atribuição de técnico comum fica restrita a si próprio. Administradores podem escolher técnicos ativos da equipe e encaminhar para outra equipe, removendo o responsável anterior. O servidor continua sendo a autoridade e pode rejeitar uma ação por concorrência. Encerramento exige causa e solução e deixa a conversa em leitura.

O indicador WhatsApp consulta `/api/whatsapp/status`; uma falha é apresentada como indisponibilidade, independentemente de `/health`. O pareamento permanece no backend em `/whatsapp.html`, com seu próprio login administrativo.

## Demonstração de IA

Por padrão, `/triagem`, `/avaliacao` e o painel do atendimento mostram indisponibilidade da integração. `VITE_ENABLE_AI_DEMO=true` libera cenários fictícios somente nas páginas de demonstração. O painel em um chamado real permanece operacional e sem recomendação simulada. O mesmo componente `TriagemPanel` apresenta os dois contextos.

Fixtures de IA ficam em `src/mocks`, com contrato interno próprio e `origem: SIMULADA`. Edição, cancelamento, aceite e avaliação são estado local. A recomendação original permanece consultável; recarregar restaura tudo. Não existem endpoints de triagem inventados, gravações de demonstração nem associação com clientes reais. `triagemDemoService` é o limite para uma integração futura com auditoria própria.

## Teste integrado isolado

Compile o backend atual e gere o classpath de teste (H2 já é dependência de teste):

```powershell
cd backend
mvn test-compile dependency:build-classpath '-Dmdep.outputFile=target/test-classpath.txt' '-Dmdep.includeScope=test'
cd ../frontend
npx playwright install chromium
npm run test:e2e:live
```

A configuração inicia Java em `127.0.0.1:18085`, H2 **em memória**, e Vite em `127.0.0.1:15173`. Ela sobrescreve a importação de `.env`, datasource e Evolution para impedir acesso ao banco e ao número operacionais. Gera senha efêmera aleatória em memória; não registra senha nem token. Não reutiliza servidores existentes nessas portas. O banco deixa de existir ao parar o processo. Não execute o harness apontando para outro backend.

O fluxo cria dados sintéticos únicos/captura IDs, autentica pelo navegador, verifica o envelope Spring, abre atendimento, exercita indisponibilidade de envio e preservação de rascunho, encerra, autentica novamente após reload e consulta causa/solução/histórico persistidos no backend. Outros cenários verificam demonstração sem escrita e layout em 390/768/1366px. Screenshots somente de dados sintéticos ficam em `test-results/`, ignorado pelo Git. O teste integrado **não comprova entrega WhatsApp**: Evolution permanece desabilitada.

## Homologação manual e publicação

- [ ] Usar backend/banco de homologação e dois celulares controlados, sem número operacional.
- [ ] Receber uma mensagem externa, conferir criação e atualização pelo polling sem reload.
- [ ] Responder uma vez, conferir texto no aparelho e uma única mensagem no histórico após eco/polling.
- [ ] Em falha ou timeout do provedor, conferir rascunho e ausência de sucesso indevido/reenvio automático.
- [ ] Validar técnico de outra equipe, atribuição concorrente, sessão expirada e encerramento por outro técnico.
- [ ] Confirmar causa/solução após login e nova consulta; verificar bloqueio de edição após encerramento.
- [ ] Revisar as cinco telas contra `docs/tela-1.png` a `tela-5.png` e navegação por teclado em desktop, 390 e 768px.
- [ ] Registrar somente resultados, sem conteúdo privado, números reais, QR ou credenciais.

Primeira entrega: **Vite separado em desenvolvimento**. Uma publicação exige hospedagem do bundle com fallback SPA e proxy `/api`/`health` na mesma origem. Docker com frontend/proxy fica para uma exigência de publicação em acesso único; `vite preview` serve apenas para conferir o build. Nenhuma mudança no backend, endpoints ou coleção Postman é necessária para esta implementação.

Veja [VALIDACAO.md](VALIDACAO.md) para evidências executadas e limites da homologação.
