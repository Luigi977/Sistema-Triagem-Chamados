# Validação da primeira implementação — 14/11/2026

## Código e contratos

- Python 3.12 em Docker: `python -m pytest -q`, **17 testes passaram**.
  Matriz completa, entradas inválidas, modelo ausente, contexto confirmado,
  treinamento/inferência, avaliação e detecção de vazamento entre partições.
- Java: `mvn test`, **62 testes passaram**, sem falhas ou erros. Inclui os oito
  novos testes de prioridade, a migração V6 e a regressão existente. A suíte usa
  H2 em memória e servidor HTTP isolado; nenhum envio WhatsApp é feito.
- Frontend: `npm.cmd run build` e `npm.cmd run lint` passaram.
- `node scripts/validate-postman.cjs`: **147 requisições e 156 scripts** válidos.

## Integração HTTP e navegador

Newman 6.2.1 executou a coleção completa em duas iterações, com backend H2,
credenciais efêmeras e Python real isolado: **240 requisições, 370 verificações,
zero falhas**. A pasta 06C de prioridade estava habilitada; as pastas opcionais
de WhatsApp/Evolution ficaram desabilitadas. Não foram usados banco, dados ou
número operacional. O total executado é menor que o inventário da coleção
porque esses cenários opcionais foram pulados.

Chrome automatizado contra esse ambiente verificou login, criação de chamado,
análise da matriz, aceite, nova análise, correção com justificativa, inferência
real do modelo sintético, aviso de origem, histórico preservado, prioridade
final preservada e novo login após reload. Layouts de 1366 e 390 pixels foram
conferidos sem rolagem horizontal. As capturas contêm somente dados sintéticos
e ficam em `backend/target/`, fora do Git.

O formulário de prioridade manual também foi exercitado no navegador, com
persistência pelo endpoint existente, sem depender de resposta da IA.

## Treinamento e limitações

Os 81 exemplos versionados foram validados e divididos em 27 textos por
partição. Treinamento gerou os dois pipelines e metadados privados locais em
`ia/models/`. Avaliação separada gerou JSON e matrizes PNG em `ia/reports/local/`.

| Métrica no teste sintético | Valor |
| --- | --- |
| Macro-F1 de impacto | 0,167 |
| Macro-F1 de urgência | 0,556 |
| Macro-F1 da prioridade resultante | 0,278 |
| Críticos subpriorizados | 3 de 3 |

Esses resultados demonstram o pipeline, **não atendem a uma homologação de
qualidade**. A cobertura vocabular e a diversidade do exemplo são pequenas.
Não há dataset real rotulado/revisado disponível nesta implementação.
É necessário preparar dados representativos, revisar rótulos com o atendimento
e estabelecer metas antes de habilitar inferência operacional. Não houve ajuste
com o conjunto de teste para melhorar artificialmente esses números.

O modelo sintético é bloqueado por padrão; só foi habilitado no ambiente
isolado para testar o fluxo. A matriz com impacto/urgência informados funciona
independentemente do modelo. Toda recomendação exige decisão humana.

Foram observados avisos de depreciação nas dependências Python; a execução dos
testes, treinamento e avaliação terminou com sucesso. As versões efetivamente
utilizadas estão fixadas em `requirements.lock.txt`.

## Instalação local atualizada

Os seis serviços do Compose ficaram saudáveis após a reconstrução. O histórico
do PostgreSQL confirmou as migrações V1 a V6 aplicadas com sucesso. Login,
identidade administrativa e logout foram conferidos pelo proxy do painel na
porta 5173; o serviço Python na porta 8000 retornou CRITICA para ALTO/ALTO
informados. Essa checagem não criou chamados nem mensagens no banco principal.
O modelo sintético permaneceu bloqueado na instalação principal.

## Aprendizado com decisões humanas — 15/11/2026

Foi implementada a captura transacional de aceite, correção e alteração manual
de prioridade, com outbox PostgreSQL, recebimento idempotente autenticado na
IA, SQLite persistente e retreinamento assíncrono da prioridade final. A V7
preserva as triagens anteriores; o reconciliador inclui as decisões antigas
com autor e snapshot de entrada. Os detalhes estão em
[APRENDIZADO_CONTINUO.md](APRENDIZADO_CONTINUO.md).

Validação executada nesta implementação:

- **65 testes Java**, sem falhas, incluindo migrações e cenários de autorização,
  decisão duplicada, captura de correção, retentativa, backfill de feedback e
  mascaramento de nome coincidente com endereço de e-mail.
- **37 testes Python**, sem falhas, incluindo token obrigatório, persistência,
  idempotência, mascaramento, quarentena, colisão com teste reservado, ordenação
  temporal de decisões antigas, treino em lote, publicação/rejeição de candidato,
  recarga de modelo sem reinício e preservação da matriz quando informada.
- Frontend: `npm run build` e `npm run lint` concluídos com sucesso.
- Coleção/ambiente Postman: JSONs válidos, **155 requisições e 169 scripts** com
  sintaxe válida. As novas variáveis de segredo estão vazias nos exemplos.
- Newman com backend Java/H2 e FastAPI/SQLite locais reais: **128 requisições e
  203 verificações**, sem falhas. As pastas 06C/06D foram habilitadas; Evolution
  e WhatsApp ficaram desativados. As demais 27 requisições opcionais não foram
  enviadas. O script também confirmou aceite, correção e mudança manual no
  banco de feedback da IA, com as prioridades escolhidas pelo técnico.
- Preparação do segredo privado com `start-stack.ps1 -PrepareOnly` e validação
  de `docker compose config --quiet` concluídas, sem exibir credenciais.

Os testes de publicação do modelo forçam a aprovação/rejeição da métrica no
fixture para verificar os caminhos de ativação; testes separados exercitam a
regra real de não regressão em macro-F1 e recall de CRITICA. O smoke HTTP não
retreina modelos e não altera o arquivo ativo; o treino é coberto pela suíte
Python com arquivos temporários. Esses resultados validam o fluxo técnico,
não comprovam melhoria de qualidade no atendimento operacional.

Docker Desktop estava indisponível nesta sessão. Não houve migração ou
reinício dos contêineres operacionais. Para aplicar a atualização nessa
instalação, execute `scripts/start-stack.ps1` com Docker ativo. Os testes usaram
somente dados fictícios e bancos isolados; o token/ambiente e o relatório bruto
Newman efêmeros foram removidos ao finalizar o smoke.
