# Validação da etapa 05

Executada em **03/11/2026**, em Windows, Node 24.19.0 e Java 25 (backend com alvo Java 21). Implementação restrita ao frontend, consumindo os controllers atuais. Nenhum endpoint foi criado ou alterado.

## Resultados finais

| Verificação | Resultado |
| --- | --- |
| `npm run build` | Passou: TypeScript estrito e bundle Vite 8.3.5. |
| `npm run typecheck` | Passou, incluindo fontes, testes e configurações TypeScript. |
| `npm run lint` | Passou. Artefatos temporários dos testes são ignorados. |
| `npm test` | **28 testes passaram**, em três arquivos. |
| `npm run test:e2e:live` | **9 cenários passaram**, contra Spring Boot real e H2 em memória, em aproximadamente 1,5 minuto. |
| Compilação Java para integração | `mvn -q test-compile dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt -Dmdep.includeScope=test` passou. |
| Contrato Postman existente | `node ../scripts/validate-postman.cjs`: JSONs válidos, **122 requisições e 129 scripts** com sintaxe válida. |
| `git diff --check` | Passou. |
| `npm audit --omit=dev` | Nenhuma vulnerabilidade nas dependências de produção. |

O `npm audit` completo apontou 12 ocorrências moderadas na cadeia de desenvolvimento do ESLint/minimatch (GHSA-g3hv-75wg-m249). Não pertencem ao bundle de produção; devem ser acompanhadas nas atualizações das ferramentas.

O CLI Postman disponível é 1.80.0. A implementação reutilizou controllers, DTOs e coleção local existente; não depende de conta Postman, publicação ou novo workspace. Não foi executada a coleção Newman completa: não houve alteração de contrato e ela cria dados e possui fluxos opcionais que enviam pelo provedor. Esta validação não atribui resultados de coleções anteriores ao incremento atual.

## Evidência unitária e de interação

Os testes cobrem Bearer no cabeçalho, corpos JSON/texto/vazios, limpeza de sessão em 401, tratamento de 400/403/404/409/502/503/504 e cancelamento. O timeout de POST é exercitado sem repetição automática.

Também verificam filtros reiniciando a página, navegação de paginação, rejeição de envelope incompatível, resposta antiga que não sobrescreve uma consulta nova, polling sem sobreposição e suspensão na aba oculta. O rascunho permanece após falha e respostas são reconciliadas por ID.

As regras de permissão incluem equipe diferente, outro responsável, atribuição ao próprio técnico, administrador e bloqueio após encerramento. Triagem permite editar/cancelar/aceitar sem HTTP e preserva a recomendação original. O painel de atendimento operacional permanece sem fixtures.

## Evidência de navegador com API real isolada

O harness inicia o backend com H2 em memória, importação de `.env` desabilitada e Evolution explicitamente desabilitada. Banco operacional, serviço Evolution e celulares não participam. Os testes usam credenciais efêmeras em memória, dados fictícios e IDs retornados pela API, sem versionar tokens ou respostas.

Foram verificados:

1. Acesso direto protegido, retorno do login à fila com filtros e envelope real do Spring (`content`, `number`, `size`, `totalElements`, `totalPages`, `first`, `last`).
2. Criação manual pela interface, status e prioridade final persistidos, abertura do ID retornado e atualização de auditoria.
3. Inclusão de uma mensagem sintética pelo endpoint de registro, observada na interface pelo polling sem reload e sem perda do rascunho. HTML permanece texto literal e a quebra de linha é preservada. Essa inclusão **não representa recebimento real pelo WhatsApp**.
4. Resposta via `/responder` com Evolution desabilitada: erro 503 exibido, rascunho preservado e nenhum sucesso indevido. A aceitação/entrega real pelo provedor ainda exige homologação com celulares.
5. Confirmação de encerramento, leitura após sucesso, causa/solução e auditoria consultadas novamente após reload e novo login.
6. Senha inválida, página 404, recurso ausente e sessão revogada no servidor retornando ao login na próxima consulta.
7. Técnico N1 atribuído ao chamado perde edição após encaminhamento concorrente para N2. O rascunho permanece; o servidor rejeita alteração com 403. Encerramento concorrente aparece pelo polling e mantém a conversa em leitura.
8. Demonstração de triagem e avaliação: ações funcionam localmente; observação de rede registra **zero escritas HTTP** nesses fluxos após autenticação.
9. Dashboard e fila em **390, 768 e 1366 px**: navegação, menu móvel e ausência de transbordamento horizontal da página. A tabela permite rolagem horizontal dentro do cartão.

Capturas sintéticas foram inspecionadas para dashboard, fila, atendimento, encerramento, triagem e avaliação. A identidade dos cinco PNGs foi mantida com sidebar azul-escura, ações azuis, fundo claro, cartões brancos e badges com texto. Os títulos repetidos dos protótipos foram removidos; indicadores sem endpoint permanecem indisponíveis. As capturas estão em `test-results/`, ignorado pelo Git.

Na primeira execução, foi necessário instalar o Chromium compatível com Playwright. Os seletores de campos de seleção foram ajustados para seus papéis acessíveis. A regressão final acima foi executada após esses ajustes. No ambiente restrito desta sessão, a suíte de navegador precisou executar fora do sandbox para finalizar os processos auxiliares corretamente.

## Homologação ainda pendente

- **Dois celulares controlados:** recebimento pela Evolution, envio aceito e efetivamente recebido, eco sem duplicação e comportamento com indisponibilidade/timeout reais do provedor.
- Repetir o fluxo no PostgreSQL e nas condições de rede do ambiente de homologação; a evidência de navegador deste incremento usa H2.
- Revisão humana de acessibilidade e uso dos fluxos completos com teclado e dispositivos reais; os testes de largura e a inspeção visual não substituem essa revisão.

Nenhum envio automático foi feito para o número operacional. Nenhum QR, conteúdo privado ou segredo Evolution foi incluído no frontend ou nos artefatos versionáveis. A publicação adotada nesta entrega é o Vite separado em desenvolvimento; o checklist e os comandos estão em [README.md](README.md).
