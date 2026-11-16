# Treinamento da recomendação de prioridade

## Como funciona

A IA recebe o texto do chamado e estima duas coisas separadamente:

- **Impacto:** qual é o alcance do problema? Uma pessoa/estação (BAIXO), uma
  equipe/setor (MEDIO), ou vários setores/a organização (ALTO).
- **Urgência:** quanto tempo o atendimento pode esperar? Dias (BAIXO), um
  prazo próximo com alternativa (MEDIO), ou interrupção imediata sem
  alternativa/prazo imediato confirmado (ALTO).

O TF-IDF transforma palavras e expressões em números. A regressão logística
aprende a relacionar esses números aos rótulos dos exemplos. O treinamento
compara também uma representação que combina palavras e pedaços de palavras
(3 a 5 caracteres), útil para variações de escrita. Isso não equivale a
compreender conversas como um modelo de linguagem: negações, abreviações e
informações ausentes continuam sendo fontes de erro.

Impacto e urgência entram na matriz já existente:

| Impacto / Urgência | BAIXO | MEDIO | ALTO |
| --- | --- | --- | --- |
| BAIXO | BAIXA | BAIXA | MEDIA |
| MEDIO | BAIXA | MEDIA | ALTA |
| ALTO | MEDIA | ALTA | CRITICA |

Por exemplo, “só meu scanner falhou, tenho alternativa até amanhã” tem impacto
BAIXO e urgência MEDIO: prioridade BAIXA. “Toda a empresa parou agora, sem
alternativa” corresponde a ALTO/ALTO: CRITICA. O nome do equipamento sozinho
não determina prioridade. A palavra “urgente” também não comprova alcance.

Quando o técnico informa uma dimensão, esse valor é usado diretamente; o
modelo estima apenas as dimensões ausentes. Um serviço de criticidade alta
confirmada pode aplicar o piso ALTA, preservando CRITICA. Cargo e setor
informados no contexto não elevam prioridade sozinhos. As probabilidades
exibidas não foram calibradas como taxas de acerto. Abaixo de 0,65 em qualquer
dimensão estimada, a API sinaliza baixa confiança. A decisão continua humana.
Correções ficam no histórico e agora alimentam o [aprendizado contínuo](APRENDIZADO_CONTINUO.md):
o feedback é capturado a cada decisão, e o retreinamento ocorre automaticamente
em lotes com validação, separado da requisição do técnico.

## O que foi melhorado

O treinamento agora compara quatro candidatos em cada dimensão: TF-IDF de
palavras com C=1 (configuração anterior), e palavras + caracteres com
C=0,5, 2 e 8. C controla a regularização. O maior macro-F1 na validação vence;
essa métrica dá o mesmo peso às três classes. Empates preservam o primeiro
candidato, mais simples. Todas as opções usam balanceamento de classes.

O vocabulário e os pesos são ajustados apenas no treino. A validação escolhe
a configuração e o teste mede o resultado depois. O modelo não é reajustado
com validação ou teste. Os metadados registram os candidatos, seus escores,
configuração escolhida, semente, grupos, versão do scikit-learn e hash dos
dados. O validador também rejeita textos idênticos com rótulos contraditórios,
CSV incompatível, textos acima do limite da API e vazamento de grupos/textos
idênticos entre partições. Não detecta automaticamente todas as paráfrases.

A base `examples/chamados-contextuais.csv` tem **405 exemplos artificiais**:
243 de treino, 81 de validação e 81 de teste. Inclui os 81 exemplos anteriores
e 324 novos, com temas como internet, câmera, scanner, e-mail e impressão.
Cada combinação de impacto/urgência está representada igualmente em cada
partição. O mesmo assunto contextual e suas variantes ficam juntos; os
assuntos novos de teste não aparecem no treino.

**Limitação:** os modelos de frase de alcance/prazo são reutilizados entre
partições. Isso facilita o teste e produz métricas otimistas. Essa base serve
para desenvolvimento e não substitui chamados reais revisados. O gerador não
lê a planilha privada nem copia suas conversas.

## Planilha recebida

Foram encontrados **83 chamados e 27 contatos**. As colunas são ID, Nome do
contato, Destino, Natureza sugerida, Natureza validada e Texto inicial.
“Natureza validada” está vazia nas 83 linhas. Não existem impacto e urgência
validados. Natureza (incidente/requisição/dúvida) não determina prioridade.
“Destino” contém marcações de revisão/rotulagem, e não setores de atendimento.

`import_xlsx.py` lê a primeira aba e cria uma fila privada em
`data/processed/chamados-revisao.csv`, fora do Git. Preserva os textos para
revisão, mas remove a coluna de nomes e mascara ocorrências conhecidas de
nomes, e-mails, telefones, URLs e IPs. Isso é um mascaramento inicial, não uma
garantia de anonimização: revisar manualmente nomes não cadastrados, senhas,
outros identificadores e contexto identificável antes de usar os dados.

IDs locais substituem os IDs da fonte. Os chamados do mesmo contato recebem
o mesmo grupo pseudônimo para facilitar uma divisão conservadora sem misturar
mensagens relacionadas. A fila deixa impacto, urgência, partição e origem
vazios: não pode ser treinada acidentalmente pelo validador.

Para criar um dataset real:

1. Revisar o mascaramento e remover credenciais, dados pessoais e conteúdos
   que não sejam apropriados para treinamento.
2. Confirmar alcance e prazo com o atendimento. Mensagens sem essas informações
   precisam de esclarecimento ou exclusão, sem inventar rótulos.
3. Dois avaliadores revisam BAIXO/MEDIO/ALTO para impacto e urgência e resolvem
   discordâncias conforme as diretrizes em `README.md`.
4. Distribuir grupos inteiros entre `treino`, `validacao` e `teste`. Unir também
   grupos de textos duplicados/paráfrases, mesmo com contatos diferentes. Cada
   partição precisa das três classes em ambas as dimensões. Manter o teste
   reservado e buscar representatividade; 83 textos podem ser insuficientes.
5. Preencher `origem=REAL_ANONIMIZADO` apenas após a revisão e salvar o CSV
   revisado em `data/processed/`. As colunas auxiliares podem ser removidas.

## Reproduzir localmente

Na pasta `ia`, com Python 3.12 e dependências instaladas conforme o README:

```powershell
.venv\Scripts\python import_xlsx.py --input "CAMINHO-PRIVADO\chamados.xlsx" --output data/processed/chamados-revisao.csv
.venv\Scripts\python examples/generate_contextual.py
.venv\Scripts\python train.py --dataset examples/chamados-contextuais.csv --output models/priority-contextual.joblib
.venv\Scripts\python evaluate.py --dataset examples/chamados-contextuais.csv --model models/priority-contextual.joblib --output reports/contextual
.venv\Scripts\python check_scenarios.py
.venv\Scripts\python compare_training.py
.venv\Scripts\python -m pytest -q
```

A importação recusa sobrescrever uma fila existente; use outro nome se ela já
foi criada. Os relatórios detalhados e matrizes ficam em `reports/contextual/`,
ignorados pelo Git. `check_scenarios.py` usa o endpoint pelo TestClient em
processo, sem backend Java ou banco. Os 12 casos públicos estão em
`examples/cenarios-prioridade.json`; são cenários exploratórios adicionais,
não entram no treinamento nem na escolha do modelo.

O novo artefato está em `models/priority-contextual.joblib`. Para experimentar
localmente, em uma sessão PowerShell na pasta `ia`:

```powershell
$env:IA_MODEL_PATH = 'models/priority-contextual.joblib'
$env:IA_ALLOW_SYNTHETIC_MODEL = 'true'
.venv\Scripts\python predict.py "A internet caiu em todos os setores agora, sem alternativa para continuar."
```

O arquivo padrão `models/priority.joblib` foi preservado. O novo modelo ainda é
sintético e continua sujeito ao bloqueio padrão da API. A instalação em
execução não foi alterada. Um processo que já carregou um modelo precisa ser
reiniciado para usar outro artefato.

## Resultados locais — 15/11/2026

Comparação em **um mesmo teste de 81 textos**, sem usá-lo para selecionar:

| Configuração | Exemplos de treino | Acurácia de prioridade | Macro-F1 de prioridade | Críticos subpriorizados |
| --- | ---: | ---: | ---: | ---: |
| Configuração anterior, base de 81 | 27 | 56,8% | 0,591 | 3 de 9 |
| Configuração fixa, base de 405 | 243 | 92,6% | 0,936 | 0 de 9 |
| Seleção por validação, base de 405 | 243 | 90,1% | 0,915 | 0 de 9 |

A ampliação dos exemplos foi a principal contribuição nesta rodada. A seleção
por validação não garante superar a configuração fixa no teste: ficou abaixo
dela aqui. Não trocamos o vencedor com base nesse resultado. A configuração
escolhida foi palavras/C=1 para impacto e palavras+caracteres/C=8 para urgência.
O macro-F1 de validação foi 0,886 e 0,874 respectivamente. No teste, o macro-F1
foi 1,000 para impacto e 0,864 para urgência.

Nos **12 chamados semelhantes**, o modelo acertou a prioridade de **9 (75%)**.
Oito receberam baixa confiança, incluindo os três erros:

| Caso | Esperada | Recomendada |
| --- | --- | --- |
| Internet lenta apenas em um notebook, sem pressa | BAIXA | MEDIA |
| Impressora de um setor, prazo até amanhã com alternativa | MEDIA | ALTA |
| Câmera individual, mensagem abreviada e sem pressa | BAIXA | MEDIA |

Os três erros superestimaram o impacto. Os casos de internet caída na empresa
inteira e e-mail interrompido em um setor tiveram CRITICA e ALTA corretamente.
Esses erros mostram por que são necessários exemplos reais variados, negações
e abreviações na futura base revisada. O conjunto de cenários foi mantido após
a avaliação, sem ajustar o modelo para decorar suas respostas.

**21 testes passaram** em Python 3.12.16/scikit-learn 1.6.1, incluindo o
contrato da API, seleção, isolamento do teste, importação e mascaramento.
Houve avisos de compatibilidade/depreciação nas dependências, sem falha final.
Docker Desktop estava indisponível: não foram executados Compose nem Newman.
Os endpoints e a matriz não foram alterados nesta melhoria.

Referências técnicas: [TF-IDF e caracteres no scikit-learn](https://scikit-learn.org/stable/modules/generated/sklearn.feature_extraction.text.TfidfVectorizer.html)
e [prevenção de vazamento de dados](https://scikit-learn.org/stable/common_pitfalls.html#data-leakage).

## Segunda rodada: classificação dos 83 chamados por IA

A pedido do usuário, cada um dos 83 textos recebeu impacto e urgência inferidos
por IA, justificativa, confiança qualitativa e marcação de revisão humana
pendente. Essa é uma rotulagem provisória, não uma referência humana validada.
Suposições de alcance ou prazo foram descritas nas justificativas. Não foi
atribuído impacto ALTO sem evidência de alcance entre setores/organização.

Distribuição dos rótulos propostos:

| Dimensão | BAIXO | MEDIO | ALTO |
| --- | ---: | ---: | ---: |
| Impacto | 57 | 26 | 0 |
| Urgência | 40 | 40 | 3 |

37 rótulos receberam confiança qualitativa BAIXA, 45 MEDIA e 1 ALTA. A confiança
se refere à interpretação do texto, não à probabilidade do classificador.
Perguntas de acompanhamento e mensagens sem contexto foram mantidas porque
o pedido foi classificar todas as linhas; essa inclusão limita a qualidade.

Os arquivos privados estão em `data/processed/` e permanecem fora do Git:

- `rotulos-ia-v1.json`: 83 anotações explícitas e hash da fila rotulada.
- `chamados-mistos-v1-rotulados.xlsx`: planilha com os 83 textos mascarados,
  IDs T001–T083, dimensões, prioridade de referência, justificativa e partição.
- `chamados-mistos-v1-rotulados.csv`: mesmos rótulos em CSV UTF-8 com BOM.
- `chamados-mistos-v1.rotulos.md`: tabela legível dos 83 rótulos e justificativas.
- `chamados-mistos-v1.csv`: dataset de 488 linhas, combinando os 405 exemplos
  sintéticos anteriores e os 83 textos com `origem=REAL_ROTULADO_IA`.

`prepare_labeled.py` aplica somente as anotações explícitas, sem chamar um
modelo para inventar os rótulos. Confere cobertura de todos os IDs e o hash do
texto revisado. Une mensagens do mesmo contato, textos idênticos e os grupos
explicitamente relacionados à sincronização de ponto. A divisão conservadora
resultou em 24 grupos de chamados reais. Contatos diferentes com incidentes
semelhantes podem ainda ter relações não detectáveis sem datas/histórico completo.

A divisão real foi **53 no treino, 16 na validação e 14 no teste**. Ela foi
escolhida entre 256 sementes para aproximar a proporção 60/20/20 e distribuição
dos rótulos, sem avaliar previsões (semente escolhida: 177). Os grupos inteiros
permanecem numa única partição. Os 83 exemplos fazem parte do dataset, mas só
os 53 de treino ajustam vocabulário e pesos. Os sintéticos foram preservados
para cobrir as classes ausentes, inclusive impacto ALTO.

O novo modelo `models/priority-misto-ia-v1.joblib` foi selecionado pela
validação: palavras/C=1 para ambas as dimensões. O artefato e os metadados
registram `rotulagem_ia=true`, `sintetico=true` e `homologado=false`.

Comparação antes/depois, mantendo os testes anteriores e o mesmo teste real:

| Avaliação de prioridade | Modelo anterior | Modelo retreinado |
| --- | ---: | ---: |
| 14 chamados reais reservados, referência IA | 4/14 (28,6%) | 12/14 (85,7%) |
| 12 cenários semelhantes anteriores | 9/12 (75%) | 10/12 (83,3%) |
| 81 exemplos sintéticos de teste anteriores | 73/81 (90,1%) | 66/81 (81,5%) |

Nos 14 chamados reais, o acordo com os rótulos propostos foi 11/14 (78,6%) em
impacto e 9/14 (64,3%) em urgência. **Sempre prever prioridade BAIXA também
acerta 12/14**: o novo resultado de prioridade não supera esse baseline. Os
dois erros foram classificados como BAIXA embora a referência fosse MEDIA e
ALTA. Todos os 14 receberam baixa confiança. A base real de teste não tem
CRITICA; portanto não valida recall de críticos. No teste sintético, o modelo
retreinado subpriorizou 3 dos 9 críticos, contra zero no modelo anterior.

Esses resultados medem concordância com rótulos IA, não exatidão comprovada
no atendimento. A rodada melhorou nos cenários semelhantes e na adaptação ao
vocabulário real, mas perdeu desempenho no teste sintético e não justifica
substituição automática. O modelo anterior e a instalação ativa foram
preservados. Não foram feitas novas alterações de dados ou parâmetros para
melhorar artificialmente os resultados após observar os testes.

Reprodução na pasta `ia`, usando os arquivos privados locais:

```powershell
# Use um novo destino se o arquivo já existir.
.venv\Scripts\python prepare_labeled.py --review data/processed/chamados-revisao.csv --labels data/processed/rotulos-ia-v1.json --output data/processed/chamados-mistos-v1.csv
.venv\Scripts\python train.py --dataset data/processed/chamados-mistos-v1.csv --output models/priority-misto-ia-v1.joblib
.venv\Scripts\python evaluate.py --dataset data/processed/chamados-mistos-v1.csv --model models/priority-misto-ia-v1.joblib --output reports/misto-ia-v1
.venv\Scripts\python evaluate_labeled.py --dataset data/processed/chamados-mistos-v1.csv
.venv\Scripts\python check_scenarios.py --model models/priority-misto-ia-v1.joblib --output reports/misto-ia-v1/cenarios.json
.venv\Scripts\python -m pytest -q
```

Os relatórios detalhados ficam em `reports/misto-ia-v1/`, fora do Git. A
validação final terminou com **22 testes aprovados**, incluindo rastreabilidade
dos rótulos IA, isolamento de grupos e recusa de aplicar rótulos a uma fila
alterada. A avaliação continuou local, sem alterar endpoints ou acionar
WhatsApp/banco operacional.

## Terceira rodada: oito exemplos fornecidos pelo usuário

Os oito exemplos da tabela foram preservados em
`examples/exemplos-usuario.csv`, incluindo impacto, urgência e prioridade
exatamente como fornecidos. São exemplos ilustrativos com `origem=SINTETICO`
e `rotulador=USUARIO`, não chamados reais homologados. Todos são consistentes
com a matriz. As formas femininas de urgência foram normalizadas para
BAIXO/MEDIO/ALTO, conforme o contrato interno.

O exemplo do diretor foi mantido em MEDIO/ALTO por ser um rótulo explícito do
usuário. Isso não cria uma regra que eleve qualquer chamado de diretor. O
financeiro no fechamento e a recuperação de arquivo antes de reunião também
preservam os rótulos propostos; o texto sozinho não detalha todas as
consequências operacionais. Usar esses exemplos como referência de rotulagem
exige explicitar essas consequências nas futuras diretrizes do atendimento.

`augment_training.py` acrescentou somente as oito linhas ao **treino**. IDs,
grupos, textos e rótulos de validação e teste permaneceram idênticos à segunda
rodada. A rotina valida prioridades contra a matriz, recusa sobrescrever a
versão anterior e rejeita IDs ou grupos/textos duplicados entre partições.

O dataset privado `data/processed/chamados-mistos-v2.csv` tem 496 linhas:
304 de treino, 97 de validação e 95 de teste. Inclui 413 exemplos sintéticos
e os mesmos 83 chamados rotulados por IA, com a divisão 53/16/14 preservada.
O modelo novo está em `models/priority-misto-ia-v2.joblib`; a seleção pela
validação permaneceu palavras/C=1 nas duas dimensões. O modelo ativo não foi
substituído.

| Verificação de prioridade | Segunda rodada (v1) | Terceira rodada (v2) |
| --- | ---: | ---: |
| 12 cenários semelhantes anteriores | 10/12 | 11/12 |
| 14 chamados reais reservados, referência IA | 12/14 | 12/14 |
| 81 exemplos sintéticos reservados | 66/81 | 66/81 |
| 8 exemplos fornecidos, agora no treino | 4/8 | 8/8 |

O resultado nos oito exemplos é **aderência aos dados de treino**, não
avaliação independente. Os outros conjuntos foram reutilizados entre rodadas;
continuam úteis para regressão, mas não substituem uma avaliação futura com
novos chamados reais e rótulos humanos. Não houve novo ajuste após observar
esses resultados.

Nos chamados reais, o impacto permaneceu em 11/14 e a urgência caiu de 9/14
para 8/14. A prioridade continua empatada com prever sempre BAIXA, e todos os
14 casos receberam baixa confiança. No teste sintético, os mesmos 3 dos 9
críticos continuaram subpriorizados. Por isso, o aumento para 11/12 cenários
semelhantes não comprova uma melhoria geral. A revisão dos rótulos reais e a
diversificação dos exemplos seguem necessárias.

Reprodução na pasta `ia`:

```powershell
# O destino deve ser novo para preservar os dados da rodada anterior.
.venv\Scripts\python augment_training.py --base data/processed/chamados-mistos-v1.csv --output data/processed/chamados-mistos-v2.csv
.venv\Scripts\python train.py --dataset data/processed/chamados-mistos-v2.csv --output models/priority-misto-ia-v2.joblib
.venv\Scripts\python evaluate_labeled.py --dataset data/processed/chamados-mistos-v2.csv --before models/priority-misto-ia-v1.joblib --after models/priority-misto-ia-v2.joblib --output reports/misto-ia-v2/comparacao.json
.venv\Scripts\python check_scenarios.py --model models/priority-misto-ia-v2.joblib --output reports/misto-ia-v2/cenarios.json
.venv\Scripts\python -m pytest -q
```

Os relatórios locais estão em `reports/misto-ia-v2/`. **23 testes passaram**,
incluindo a preservação de validação/teste e a rejeição de uma prioridade
incompatível com a matriz. Os endpoints não foram alterados nesta rodada.
