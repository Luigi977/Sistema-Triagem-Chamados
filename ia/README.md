# Recomendação de prioridade

Microsserviço FastAPI, classificadores TF-IDF + regressão logística e
regras versionadas de impacto × urgência. As decisões dos técnicos alimentam
o [aprendizado contínuo de prioridade](APRENDIZADO_CONTINUO.md), com fila
persistente, retreinamento em lotes e validação antes da ativação.
A decisão final pertence ao técnico.

## Executar sem Python instalado no Windows

Na raiz do repositório, com Docker Desktop ativo:

```powershell
docker compose --env-file .env.docker up -d --build
```

Documentação interativa: <http://localhost:8000/docs>.
`GET /health` informa separadamente se o serviço e o modelo estão disponíveis.
Sem modelo, o endpoint funciona com impacto e urgência informados.

Para executar diretamente, instale Python 3.12 e, na pasta `ia`:

```powershell
py -3.12 -m venv .venv
.venv\Scripts\python -m pip install -r requirements.lock.txt
.venv\Scripts\python -m uvicorn src.main:app --host 127.0.0.1 --port 8000
```

## 09.1 — API e matriz

`POST /predict/priority` recebe:

```json
{
  "texto": "O servidor principal caiu e toda a empresa está parada",
  "impacto_informado": "ALTO",
  "urgencia_informada": "ALTO"
}
```

Retorna `prioridade_recomendada=CRITICA`, `origem=INFORMADO`, confianças nulas,
origem por dimensão e `revisao_humana_obrigatoria=true`. A resposta também
inclui prioridade base e versões da matriz/regras. Impacto e urgência aceitam
`BAIXO`, `MEDIO`, `ALTO`; valores são normalizados. Categorias/campos inválidos,
texto vazio e texto acima de 10.000 caracteres retornam 422.

| Impacto / Urgência | BAIXO | MEDIO | ALTO |
| --- | --- | --- | --- |
| ALTO | MEDIA | ALTA | CRITICA |
| MEDIO | BAIXA | MEDIA | ALTA |
| BAIXO | BAIXA | BAIXA | MEDIA |

Dimensão omitida é estimada pelo modelo; sem modelo disponível, retorna 503.
Entradas mistas retornam `origem=MISTO`. Confianças são probabilidades estimadas,
não garantia de acerto; abaixo de 0,65 a resposta sinaliza baixa confiança.
Esse limiar inicial é informativo e não substitui revisão humana.

Após um lote de feedback ser aprovado, entradas sem dimensões informadas podem
usar a prioridade aprendida diretamente das decisões (`metodo_prioridade=
FEEDBACK_TECNICO`). A resposta mantém `prioridade_matriz` e acrescenta
`confianca_prioridade`, sem inventar impacto/urgência para uma correção humana.
Com qualquer dimensão informada, mantém o método `MATRIZ`.

## 09.2 — Dataset

`examples/chamados-sinteticos.csv` contém **81 textos artificiais**, em 27 grupos,
com 27 textos por partição (`treino`, `validacao`, `teste`). É uma fixture para
executar o pipeline, sem conversas reais. Modelos treinados com qualquer amostra
sintética são marcados como sintéticos e ficam bloqueados por padrão na API.

Schema CSV obrigatório: `id,grupo,texto,impacto,urgencia,particao,origem`.
`origem` deve ser `SINTETICO`, `REAL_ANONIMIZADO` ou `REAL_ROTULADO_IA`
(rótulo provisório inferido por IA, sem validação humana). O validador rejeita campos
ausentes, IDs repetidos, rótulos inválidos, partições sem as três classes e
grupos/textos normalizados que atravessem partições.

Para dados reais, anonimizar antes da rotulagem e manter em `data/raw/`,
`data/processed/` ou `data/test/`, todos fora do Git. Usar um mesmo `grupo` para
a conversa inteira e suas paráfrases; atribuir o grupo inteiro a uma partição.
A detecção de textos idênticos não identifica sozinha todas as paráfrases.

Diretrizes iniciais:

- Impacto BAIXO: uma pessoa/estação; MEDIO: equipe/setor; ALTO: vários setores
  ou organização. Confirmar o alcance descrito em vez de inferi-lo pelo cargo.
- Urgência BAIXO: pode aguardar dias; MEDIO: prazo próximo com alternativa;
  ALTO: interrupção imediata sem alternativa ou prazo imediato confirmado.
- Informação insuficiente exige esclarecimento/revisão. Não inventar rótulos.
- Dois avaliadores devem revisar os casos reais, resolver discordâncias e
  registrar a versão das diretrizes antes de usar os rótulos para homologação.

## 09.3 — Treinamento

Na raiz, para treinar o exemplo sintético:

```powershell
docker compose --env-file .env.docker run --rm --no-deps ia python train.py
```

Ou, na pasta `ia`, com ambiente virtual:

```powershell
.venv\Scripts\python train.py --dataset examples/chamados-sinteticos.csv --output models/priority.joblib
```

O vocabulário e os dois classificadores são ajustados somente em `treino`.
O treinamento compara quatro configurações por dimensão: palavras com C=1
e palavras + caracteres com C=0,5, 2 ou 8. Escolhe o maior macro-F1 na
validação; em empate, mantém o primeiro candidato. As classes são balanceadas
e a semente é 42. O teste não participa do ajuste ou da seleção.
Metadados incluem hash do dataset, versão do scikit-learn e grupos de treino.
Os artefatos ficam em `models/`, ignorado pelo Git. Joblib pode executar código:
carregar somente artefatos locais confiáveis.

A preparação da planilha de chamados, a base contextual de 405 exemplos,
os testes com chamados semelhantes e os resultados da comparação estão em
[TREINAMENTO.md](TREINAMENTO.md). A planilha fornecida não contém impacto e
urgência validados; seus textos não foram usados como dados reais rotulados.

Para a tabela ampliada de **108.008 registros sintéticos** em Excel e CSV,
consulte [BASE_AMPLA.md](BASE_AMPLA.md). O catálogo tem 200 cenários em 40
famílias; as variantes permanecem na mesma partição da respectiva família.

Para testar inferência do modelo sintético, defina
`IA_ALLOW_SYNTHETIC_MODEL=true` no `.env.docker` privado e recrie o serviço:

```powershell
docker compose --env-file .env.docker up -d --force-recreate ia
```

Essa opção é exclusivamente para desenvolvimento. A interface exibe o aviso
de modelo sintético. Para novos artefatos, reinicie a IA: o modelo é carregado
uma vez por processo. Sem habilitar a opção, a matriz informada continua disponível.

## 09.4 — Avaliação

```powershell
docker compose --env-file .env.docker run --rm --no-deps ia python evaluate.py
```

Gera `reports/local/metricas.json` e matrizes PNG de impacto, urgência e
prioridade. Calcula precisão/recall/F1/suporte por classe, macro-F1 e comparação
com classe majoritária de treino; registra também críticos subpriorizados.
As partições são verificadas pelo hash do dataset e pelos grupos de treino.

Relatórios sintéticos demonstram execução, **não qualidade operacional**.
`homologado=false` é preservado: dataset representativo, revisão dos rótulos e
metas de recall para críticos precisam de validação com o atendimento real.
Não publicar relatórios com exemplos de conversas privadas.

## 09.5 — Contexto

Campo opcional `contexto`: `cargo`, `setor`, `criticidade_servico`
(`BAIXA`, `MEDIA`, `ALTA`) e `confirmado` (padrão falso).
A regra `SERVICO_CRITICO_CONFIRMADO` aplica piso `ALTA` somente quando
criticidade alta foi confirmada pelo técnico. Preserva prioridade base,
retorna justificativa e versão `contexto-v1`; nunca reduz `CRITICA`.
Cargo e setor ficam como contexto auditável e não elevam prioridade sozinhos.
A regra inicial deve ser homologada com os critérios de atendimento do TCC.

## 09.6 — Integração

O React chama exclusivamente o Spring Boot autenticado. A IA não recebe token,
telefone ou nome do cliente. O backend escolhe a última mensagem `CLIENTE/TEXTO`
por ID de persistência; na ausência, usa descrição e depois resumo.
A entrada e o contexto são preservados privadamente no banco para auditoria.

Rotas sob `/api/chamados/{id}/triagens-prioridade`:

- `POST`: `{}` para estimar ambas as dimensões, ou
  `{"impactoInformado":"ALTO","urgenciaInformada":"ALTO","contexto":{"cargo":"Analista","criticidadeServico":"ALTA","confirmado":true}}`.
  Retorna 201 e a análise. Só `prioridadeSugerida` muda.
- `GET`: histórico, mais recente primeiro, incluindo falhas e decisões.
- `POST /{triagemId}/decisao`: `{"acao":"ACEITAR"}` ou
  `{"acao":"CORRIGIR","prioridadeFinal":"MEDIA","justificativa":"Alternativa confirmada"}`.
  Retorna 200; salva prioridade final, autor e data sem alterar o resultado original.

Aceite/correção também criam feedback para treinamento na mesma transação.
A resposta e o histórico incluem `feedbackTreinamento` (PENDENTE ou ENVIADO).
Alterações pela rota de prioridade manual também alimentam a fila. ENVIADO
significa recebido pela IA; o treino e a avaliação são assíncronos. Consulte
[APRENDIZADO_CONTINUO.md](APRENDIZADO_CONTINUO.md) para limiares, rotas internas
protegidas, configuração do segredo compartilhado e estados de aprendizado.

Erros: 400 entrada inválida; 401 sem sessão; 403 outra equipe/responsável;
404 recurso ausente; 409 chamado encerrado, sugestão antiga, já decidida ou
entrada alterada; 502 resposta inválida/falha de comunicação com IA;
503 IA/modelo indisponível; 504 timeout. Falhas upstream ficam no histórico.
Nenhum desses erros impede atendimento manual.

HTTP é executado fora da transação de banco. Locks curtos por chamado
coordenam solicitação/decisão com outras alterações. A decisão exige a última
análise e a mesma versão de entrada/operações; em conflito, solicitar nova análise.
Resultados concluídos após mudança ficam `OBSOLETA`. Revisões antigas são
preservadas; uma falha de nova análise também exige nova tentativa explícita.

`IA_ENABLED` habilita o cliente Java; `IA_API_URL` aponta para Python;
`IA_TIMEOUT` controla timeout (10s). No Compose, cliente habilitado por padrão.
`IA_AUTO_ENABLED=true` ativa o worker de WhatsApp. O padrão é falso para
permitir homologação antes de analisar mensagens operacionais. Quando ativo,
analisa a última mensagem de texto já persistida de cada chamado WhatsApp aberto,
sem atrasar o webhook. Chave única por mensagem evita duplicação após reinício;
falhas não são repetidas automaticamente. O painel permite nova tentativa.
Um processamento interrompido é marcado como falha após dez minutos.

Frontend: `cd frontend; npm.cmd run dev`, com proxy para o backend na porta
8080 (ajustável por `DEV_API_TARGET` no `.env.local`). Login usa as credenciais
existentes e mantém Bearer só em memória. Abra um chamado, analise e revise.

## Verificação

```powershell
docker compose --env-file .env.docker run --rm --no-deps ia python -m pytest -q
cd backend
mvn test
cd ../frontend
npm.cmd run build
npm.cmd run lint
```

Os testes não usam o WhatsApp operacional. Para regressão HTTP consulte
[Postman](../docs/postman/README.md); habilite os cenários IA somente em banco de teste.

Resultados executados e limitações estão em [VALIDACAO.md](VALIDACAO.md).

Implementação baseada nos contratos oficiais de [FastAPI](https://fastapi.tiangolo.com/tutorial/body/)
e nos [pipelines do scikit-learn](https://scikit-learn.org/stable/modules/generated/sklearn.pipeline.Pipeline.html).
