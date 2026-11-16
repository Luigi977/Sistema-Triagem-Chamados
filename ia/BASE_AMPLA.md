# Tabela ampla de exemplos para treinamento

A versão com os **83 chamados reais preenchidos e revisados por IA** está
descrita em [REAIS_REVISADOS.md](REAIS_REVISADOS.md). Ela contém 108.091
registros com origens separadas e preserva as partições dos reais.

A tabela contém **108.008 registros sintéticos**: 200 cenários de atendimento,
nove combinações de impacto/urgência e 60 variações por combinação, mais os
oito exemplos fornecidos pelo usuário. Não há textos ou nomes da planilha
privada dos 83 chamados nesta geração.

## Arquivos gerados

Em `data/processed/base-ampla-v1/`, fora do Git:

| Arquivo | Uso |
| --- | --- |
| `tabela-chamados.xlsx` | Excel com filtros, cabeçalho fixo e aba Leia-me |
| `tabela-chamados.csv` | Tabela UTF-8 com BOM e cabeçalhos legíveis |
| `treinamento-amplo.csv` | Dataset com schema compatível com `train.py` |
| `resumo.json` | Quantidades, distribuição, hash e limitações |

As quatro primeiras colunas da tabela são **Mensagem do chamado, Impacto,
Urgência e Prioridade**, como no exemplo do usuário. Colunas adicionais
identificam ID, família, partição, origem sintética e fonte. No CSV técnico,
esses campos são `texto`, `impacto`, `urgencia` e `prioridade`, acompanhados
de `id,grupo,particao,origem,familia,cenario,variante,fonte`.

## Cobertura

O catálogo tem 40 famílias com cinco cenários cada: servidores, financeiro,
sistema operacional, impressoras, desempenho, instalação de software, rede
cabeada, recuperação de arquivos, Wi-Fi, e-mail, scanner, câmeras, ponto,
crachás, PDF, planilhas, editor de texto, apresentações, autenticação, acesso
remoto, compartilhamento, backup, banco de dados, telefonia, videoconferência,
segurança da estação, certificados, notas fiscais, estoque, RH/folha, energia,
internet/DNS, armazenamento, navegador, periféricos, áudio/vídeo, integrações,
portais, etiquetas e acessibilidade.

Cada cenário recebe as nove combinações abaixo. O alcance e o prazo são
explicitados no texto, em vez de inferir a prioridade pelo equipamento ou pelo
cargo. As variações combinam cinco descrições de alcance, quatro de prazo/
alternativa e três ordens de apresentação. O número de variantes é limitado
a essas 60 combinações de redação; acrescentar cópias não aumenta a cobertura.

| Impacto | Urgência | Prioridade | Registros |
| --- | --- | --- | ---: |
| BAIXO | BAIXO | BAIXA | 12.002 |
| BAIXO | MEDIO | BAIXA | 12.000 |
| BAIXO | ALTO | MEDIA | 12.000 |
| MEDIO | BAIXO | BAIXA | 12.000 |
| MEDIO | MEDIO | MEDIA | 12.002 |
| MEDIO | ALTO | ALTA | 12.002 |
| ALTO | BAIXO | MEDIA | 12.000 |
| ALTO | MEDIO | ALTA | 12.000 |
| ALTO | ALTO | CRITICA | 12.002 |

As combinações de dimensões são quase igualmente representadas. As prioridades
não têm frequências iguais, pois seguem a matriz existente: diferentes pares
podem levar à mesma prioridade.

## Separação e limites

Uma família inteira, seus cinco cenários e todas as variantes ficam em uma
única partição. São 24 famílias no treino, oito na validação e oito no teste,
com semente 42. As famílias dos oito exemplos do usuário ficam no treino,
preservando os rótulos fornecidos. A divisão final é:

- **Treino:** 64.808 linhas.
- **Validação:** 21.600 linhas.
- **Teste:** 21.600 linhas.

**108.008 linhas não são 108.008 situações independentes.** A base contém 200
cenários do catálogo e oito exemplos do usuário; o restante decorre das
combinações de contexto e redação. Os modelos de frase de alcance e prazo são
reutilizados entre partições. Embora famílias e textos idênticos não atravessem
partições, isso facilita a classificação e pode gerar métricas otimistas.
Termos relacionados também podem aparecer em famílias distintas.

Os rótulos dos oito exemplos vêm do usuário; os demais são determinados pelo
contexto sintético. A origem de todos é `SINTETICO`, não uma validação humana
de atendimento real. A base é útil para experimentar o pipeline, ampliar
cobertura explícita e testar a matriz; mantenha também uma avaliação externa
com chamados reais revisados. O modelo treinado com essa base continua sujeito
ao bloqueio de modelo sintético da API.

## Reproduzir

Na pasta `ia`, usando o ambiente Python já configurado:

```powershell
# Use outro diretório se a saída já existir.
.venv\Scripts\python generate_training_table.py --output data/processed/base-ampla-v1
.venv\Scripts\python -m pytest -q
```

O gerador usa somente a biblioteca padrão e as dependências já utilizadas
pelo projeto. O catálogo é versionado em `examples/catalogo-cenarios.json`;
os arquivos grandes podem ser regenerados e ficam fora do Git. A geração é
determinística e recusa sobrescrever os arquivos anteriores. Para uma amostra
menor, `--variantes 1` gera 1.808 linhas.

Quando desejar treinar com a nova tabela, os comandos são:

```powershell
.venv\Scripts\python train.py --dataset data/processed/base-ampla-v1/treinamento-amplo.csv --output models/priority-base-ampla-v1.joblib
.venv\Scripts\python evaluate.py --dataset data/processed/base-ampla-v1/treinamento-amplo.csv --model models/priority-base-ampla-v1.joblib --output reports/base-ampla-v1
```

Nesta etapa foi criada e validada a tabela; não houve retreinamento nem
substituição do modelo ativo. O validador conferiu o schema, as classes,
grupos, IDs, textos duplicados e a matriz em todas as 108.008 linhas. Os
**26 testes automatizados passaram**, incluindo geração reduzida, preservação
dos rótulos do usuário, divisão por família e integridade do Excel. O Excel
completo também foi conferido por leitura XML em fluxo, comparando cada linha
com o CSV técnico.
