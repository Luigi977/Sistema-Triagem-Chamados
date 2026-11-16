# Aprendizado com as decisões do técnico

Antes desta alteração, aceitar ou corrigir uma sugestão apenas registrava a
decisão no banco. Agora cada decisão autenticada cria também um exemplo para
o aprendizado contínuo de **prioridade final**.

## Fluxo implementado

1. **ACEITAR:** registra a prioridade recomendada como referência aprovada.
2. **CORRIGIR:** registra a prioridade final escolhida pelo técnico, preservando
   a sugestão original e a justificativa no histórico.
3. **Alteração manual de prioridade:** também captura um exemplo, usando o
   texto mais recente do cliente, ou descrição/resumo na ausência de mensagem.
   Sem texto disponível, a mudança continua válida, mas não há amostra textual.
4. A decisão e a fila de feedback são gravadas na **mesma transação**. Cada
   triagem/operação tem um evento único; conflitos, erros de validação e
   sugestões não revisadas não produzem exemplos aprovados.
5. O worker Java envia os eventos para a IA. Se ela estiver indisponível,
   conserva a pendência e tenta novamente com espera crescente, limitada a uma
   hora. Essa comunicação acontece fora da transação da decisão.
6. A IA persiste o feedback em SQLite. Reenvios idênticos não duplicam a amostra;
   o mesmo ID com conteúdo diferente retorna 409. Decisões antigas já aprovadas
   no histórico de triagens são incluídas pelo reconciliador após a migração.
7. O worker Python retreina a prioridade em lotes e avalia o candidato. Se o
   macro-F1 ou o recall de CRITICA cair em qualquer coorte de validação,
   preserva o modelo atual. Se passar, substitui o arquivo do modelo de forma
   atômica e as próximas inferências carregam a nova versão automaticamente.

As alterações manuais anteriores à funcionalidade não são reconstruídas:
o histórico de operações antigo não preservava o valor anterior de cada
prioridade nem a entrada textual correspondente. O backfill usa apenas
triagens ACEITA/CORRIGIDA com decisor registrado e snapshot de entrada.

## O que é aprendido

Uma prioridade não identifica univocamente impacto e urgência. Por exemplo,
MEDIA pode vir de BAIXO/ALTO, MEDIO/MEDIO ou ALTO/BAIXO. Portanto, o sistema
**não converte uma correção de prioridade em rótulos inventados de dimensões**.

O retreinamento cria um terceiro classificador TF-IDF + regressão logística,
`priority_feedback`, para aprender diretamente a prioridade final. Os modelos
de impacto e urgência permanecem como estavam. A nova cabeça recebe a base de
treino original e decisões humanas elegíveis, com peso 3 para estas últimas.
Se a decisão humana corresponde ao mesmo texto de uma linha de treino da base,
ela substitui o rótulo inicial desse texto na cabeça de prioridade.

Sem dimensões informadas, um modelo promovido pode usar essa cabeça. A resposta
explicita `metodo_prioridade=FEEDBACK_TECNICO`, `confianca_prioridade` e
`prioridade_matriz`. Impacto e urgência continuam sendo estimativas separadas,
e podem não explicar sozinhos a prioridade aprendida diretamente das decisões.
O painel mostra essa distinção. Se o técnico informar qualquer dimensão, o
caminho existente da matriz é preservado (`metodo_prioridade=MATRIZ`).

As regras de contexto continuam aplicadas depois da prioridade base. Feedback
que contradiga o piso de um serviço crítico confirmado é guardado em
quarentena para revisão, sem forçar o modelo a contrariar a regra em vigor.
Aceitar uma sugestão conta como feedback humano conforme solicitado, mas não
elimina o risco de confirmação automática de um erro do próprio modelo.

## Frequência e avaliação

O cadastro de feedback acontece a cada decisão. O treino é assíncrono, em lote:

Os 83 chamados rotulados por IA não são contados como aprovação de um técnico.
Eles podem compor a base inicial, mantendo sua origem provisória; os limiares
abaixo contam as decisões efetivamente registradas por técnicos.

- Worker Java: a cada 10 segundos, até 20 pendências por ciclo.
- Worker Python: verifica novas amostras a cada 60 segundos.
- Limiar inicial: 20 textos elegíveis distintos e pelo menos três grupos
  humanos de validação. Alterações posteriores geram outro candidato quando
  houver novo feedback elegível; o mesmo lote não é treinado novamente.
- Aproximadamente 80% dos grupos humanos ficam no treino e 20% na validação,
  por hash estável do grupo. As mensagens do mesmo chamado permanecem juntas.
- Quando existem decisões diferentes para o mesmo texto, vale a mais recente
  **pela data da decisão**, não pela ordem de envio. O histórico continua salvo.
- Textos iguais que colidam entre partições ficam em quarentena. Grupos que
  colidem com validação/teste da base são excluídos do treino; feedback de
  validação que coincide com texto já ajustado também é excluído.
- A validação compara o modelo atual e o candidato na validação da base e na
  validação humana, separadamente. A partição de teste não participa da seleção
  ou ativação. Compara macro-F1 e recall de CRITICA, exigindo não regressão.

Esses critérios são iniciais. A base sintética reutiliza frases; sua validação
não comprova qualidade real. Mesmo um candidato promovido permanece com
`homologado=false` e revisão humana obrigatória. A baixa confiança também
considera a probabilidade da prioridade aprendida. Um modelo sintético continua
bloqueado salvo habilitação explícita de desenvolvimento.

## Persistência, configuração e operação

A migração V7 cria `feedback_treinamento_ia` no PostgreSQL da aplicação. A IA
mantém `ia/data/learning/feedback.sqlite3`, ignorado pelo Git e persistido por
bind mount. A cabeça treinada fica no próprio artefato do modelo; candidatos,
relatórios agregados e cópias do modelo anterior ficam em
`models/feedback-candidates/`, também privados. O CSV da base permanece como
referência estável: os novos exemplos são lidos do SQLite no retreinamento.

Nome conhecido do cliente e telefone são mascarados no envio; a IA também
mascara e-mails, telefones, URLs e IPs no texto antes de armazenar. Nome de
técnico, token de sessão, cargo e justificativa não são enviados como atributos
de treinamento. Isso é redução automática de dados, não garantia de anonimato;
nomes de terceiros e contexto identificável podem permanecer no texto privado.

`scripts/start-stack.ps1` gera `DOCKER_IA_FEEDBACK_TOKEN` e acrescenta essa chave
a instalações existentes sem substituir seus outros segredos. O Compose
entrega o mesmo valor ao backend e à IA como `IA_FEEDBACK_TOKEN`. Chamadas de
aprendizado precisam do header `X-Feedback-Token`; sem configuração, retornam
503, e com credencial ausente/incorreta retornam 401. Não enviar esse segredo
ao navegador ou versioná-lo em ambientes Postman.

| Variável | Padrão / finalidade |
| --- | --- |
| `IA_FEEDBACK_TOKEN` | Segredo compartilhado, no mínimo 32 caracteres |
| `IA_FEEDBACK_DELAY_MS` | 10000; frequência do envio Java |
| `IA_LEARNING_ENABLED` | true; ativa o worker Python |
| `IA_LEARNING_INTERVAL_SECONDS` | 60; intervalo do worker Python |
| `IA_LEARNING_MIN_FEEDBACK` | 20; exemplos únicos elegíveis mínimos |
| `IA_LEARNING_MIN_VALIDATION_GROUPS` | 3; grupos humanos mínimos para validação |
| `IA_LEARNING_DATASET` | Vazio: usa a base ampla com reais, se existir; caso contrário, a base contextual de exemplo |
| `IA_FEEDBACK_DB` | `data/learning/feedback.sqlite3` |
| `IA_MODEL_PATH` | `models/priority.joblib`; precisa existir e estar habilitado |

O Compose monta `data/processed` somente para leitura, permitindo usar a base
privada de 108.091 linhas. O volume de treino aumenta o custo de cada lote.
Execute uma instância de worker por banco de feedback; a rotina usa também uma
lease persistente de até uma hora para impedir treinos concorrentes. Não
escreva manualmente no mesmo arquivo de modelo enquanto o worker treina.

Para atualizar a instalação, com Docker Desktop ativo:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-stack.ps1
```

O registro do feedback funciona mesmo sem modelo de inferência habilitado;
o retreinamento aguarda uma base/modelo disponível, dados suficientes e
validação. Ao desligar o worker Python, os eventos continuam sendo recebidos
para processamento posterior. Ao desativar o cliente Java, as decisões ficam
na fila até reativação. A decisão do técnico não espera pelo treino.

## Contratos

`POST /api/chamados/{id}/triagens-prioridade/{triagemId}/decisao` mantém o corpo
existente. Sua resposta e o histórico passam a incluir `feedbackTreinamento`:

```json
{"eventoId":"UUID gerado automaticamente","estado":"PENDENTE","tentativas":0}
```

`ENVIADO` significa recebido pela IA, não modelo já treinado/ativado.

Rotas internas da IA, com credencial compartilhada:

- `POST /learning/feedback`: `evento_id` e `grupo` UUID, `texto`, `acao`
  (ACEITAR/CORRIGIR), `prioridade_final`, `decidida_em` com fuso e
  `servico_critico_confirmado`. Retorna 201 novo, 200 reenvio idêntico,
  409 conflito e 422 corpo inválido.
- `GET /learning/status`: quantidades, partições, limiares e resultado do
  último ciclo. Não retorna textos, tokens ou identificação de técnicos.

Principais estados do worker: AGUARDANDO_DADOS, AGUARDANDO_MODELO_BASE,
AGUARDANDO_VALIDACAO, AGUARDANDO_CLASSES, TREINANDO, REJEITADO_METRICAS,
BLOQUEADO_MODELO_SINTETICO, ATIVADO e FALHA_TREINAMENTO.

## Validação

As suítes cobrem decisões/filas na mesma transação, autorização, reenvio,
correção sem adulterar a sugestão, alteração manual, backfill, persistência,
mascaramento, idempotência, grupos reservados, ordenação de decisões antigas,
publicação atômica, troca de versão, rejeição por regressão e ciclo do worker.
Os testes de publicação forçam apenas a aprovação da métrica no fixture para
exercitar a troca de arquivo; testes separados verificam a regra real de
macro-F1 e recall de críticos. Não são evidência de qualidade operacional.

O [Postman](../docs/postman/README.md) inclui a pasta opcional 06D para testar
o recebimento interno. O script `scripts/test-learning-flow.py` executa Java
com H2 em memória, FastAPI com SQLite temporário e a coleção Newman completa,
com Evolution/WhatsApp desativados. Também verifica por HTTP que ACEITAR,
CORRIGIR e alteração manual chegam efetivamente ao banco de feedback da IA.

Referências: [ciclo de vida FastAPI](https://fastapi.tiangolo.com/advanced/events/)
e [separação de dados no scikit-learn](https://scikit-learn.org/stable/common_pitfalls.html#data-leakage).
