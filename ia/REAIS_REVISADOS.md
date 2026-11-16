# Preenchimento dos 83 chamados reais

Uma cópia generalizada para publicação está em
[`../dataset/publico/`](../dataset/publico/README.md). Ela substitui as frases
originais por descrições técnicas, remove os vínculos de contato e preserva
os rótulos e partições. Os arquivos descritos abaixo continuam privados.

Os 83 textos mascarados da planilha receberam uma segunda revisão por IA,
com impacto, urgência, prioridade, justificativa, confiança qualitativa e
pontos a confirmar específicos de cada mensagem. Foram preservados o texto,
os IDs de referência, os grupos relacionados e a divisão 53/16/14.

Os campos registram `status_revisao_ia=CONCLUIDA`, `revisor=IA`,
`origem=REAL_ROTULADO_IA` e `revisao_humana=PENDENTE`. Revisão por IA não é
aprovação humana nem homologação da qualidade operacional. Os rótulos são
hipóteses quando faltam alcance, prazo ou alternativa; as pendências descrevem
essa falta sem acrescentar fatos à mensagem original.

## Arquivos preenchidos

Todos os arquivos com dados reais ficam em
`data/processed/base-ampla-com-reais-v1/`, fora do Git:

| Arquivo | Conteúdo |
| --- | --- |
| `chamados-reais-revisados.xlsx` | Apenas os 83 reais, com justificativas e pontos a confirmar |
| `chamados-reais-revisados.csv` | A mesma tabela em CSV com cabeçalhos legíveis |
| `reais-rotulados-ia.csv` | Os 83 reais com schema técnico e metadados de revisão |
| `tabela-com-reais.xlsx` | Tabela completa, aba exclusiva dos reais e instruções |
| `tabela-com-reais.csv` | Tabela completa com cabeçalhos legíveis |
| `treinamento-com-reais.csv` | Dataset integrado para `train.py`, com origem de cada linha |
| `resumo.json` | Distribuição, alterações e hashes dos arquivos de origem |

A base completa tem **108.091 registros**, dos quais **108.008 sintéticos e
83 reais rotulados por IA**. Os reais aparecem primeiro na tabela completa;
no Excel também estão na aba “Reais revisados por IA”. As origens permanecem
distintas para permitir avaliação separada. Não houve retreinamento ou
alteração de endpoint nesta etapa.

## Resultado da revisão

| Dimensão | BAIXO | MEDIO | ALTO |
| --- | ---: | ---: | ---: |
| Impacto | 57 | 26 | 0 |
| Urgência | 40 | 42 | 1 |

As prioridades resultantes são 66 BAIXA e 17 MEDIA. Não foi confirmado alcance
organizacional nos textos para atribuir impacto ALTO. A única urgência ALTO
mantida é provisória: há pedido explícito de digitalização urgente, mas falta
confirmar o prazo e a alternativa disponível. A confiança desse rótulo foi
reduzida de MEDIA para BAIXA.

Duas urgências passaram de ALTO para MEDIO: falha individual ao salvar uma
planilha e interrupção da impressão de crachás. A análise anterior havia
suposto risco imediato ou necessidade sem contorno. A segunda revisão
registrou que esses fatos não estão confirmados nas mensagens. Os textos,
justificativas anteriores e resultados dos modelos antigos foram preservados
nos arquivos das rodadas anteriores.

Há 38 rótulos com confiança qualitativa BAIXA, 44 MEDIA e 1 ALTA. Os campos
de confirmação incluem alcance, prazo, alternativa e identificação do sistema
ou equipamento. Perguntas de acompanhamento não foram automaticamente
convertidas em novos incidentes confirmados; sua dependência de contexto está
anotada para a revisão do atendimento.

| Partição | Sintéticos | Reais | Total |
| --- | ---: | ---: | ---: |
| Treino | 64.808 | 53 | 64.861 |
| Validação | 21.600 | 16 | 21.616 |
| Teste | 21.600 | 14 | 21.614 |

Somente as 53 linhas reais de treino podem ajustar os pesos num treinamento
com essa divisão. Os novos rótulos são outra versão da referência; não devem
ser comparados aos resultados antigos como se nenhuma referência tivesse
mudado. Os sintéticos continuam reutilizando modelos de frase; quantidade
não substitui diversidade e revisão de situações reais.

## Reprodução e validação

As anotações explícitas estão no arquivo privado
`data/processed/revisao-reais-v2.json`, com justificativas e pontos de
confirmação para todos os IDs. A rotina confere o hash da fonte e proíbe
alterar texto, grupos, partições ou status humano pela configuração de revisão.

Na pasta `ia`:

```powershell
# Use outro destino se a saída já existir.
.venv\Scripts\python review_real_dataset.py --source data/processed/chamados-mistos-v1-rotulados.csv --review data/processed/revisao-reais-v2.json --output data/processed/base-ampla-com-reais-v1
.venv\Scripts\python -m pytest -q
```

Quando for treinar com essa nova base, use o arquivo integrado:

```powershell
.venv\Scripts\python train.py --dataset data/processed/base-ampla-com-reais-v1/treinamento-com-reais.csv --output models/priority-amplo-com-reais-v1.joblib
```

O CSV isolado com 83 reais não tem todas as classes exigidas pelo treinador;
por isso, não deve ser usado sozinho com o validador atual. O arquivo integrado
preserva essa cobertura por meio dos sintéticos.

**27 testes automatizados passaram**, incluindo preenchimento da revisão,
preservação de textos/grupos/partições, autoria IA, recusa de falsificar revisão
humana e integridade do Excel com a terceira aba. Os 83 IDs foram conferidos,
e o Excel completo foi comparado ao CSV por leitura XML em fluxo. Os dados
reais continuam excluídos do versionamento.
