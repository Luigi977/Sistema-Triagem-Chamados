# Base pública de chamados

| Arquivo | Conteúdo |
| --- | --- |
| `relatos-generalizados.csv` | 83 relatos reescritos, com rótulos e partições |
| `treinamento-publico.csv` | 108.091 registros: 108.008 sintéticos e 83 generalizados |
| `tabela-publica.xlsx` | Base completa, instruções e aba dos 83 generalizados |
| `resumo.json` | Quantidades, limitações e hashes somente dos arquivos públicos |

Os 83 relatos foram reescritos por IA para descrever apenas a necessidade
técnica. Não são transcrições de conversas. Foram retirados nomes, contatos,
marcas e modelos específicos, locais, datas, cargos, estilo de escrita e
referências internas. Justificativas e outros campos livres da fonte privada
não foram exportados. As frases generalizadas foram conferidas individualmente
contra os relatos disponíveis durante a preparação.

A ordem foi embaralhada e os IDs públicos são novos. Não há IDs da planilha,
hashes dos contatos, hashes dos arquivos privados ou tabela de correspondência
nesta pasta. O agrupamento dos relatos generalizados é apenas por partição;
não fornece o vínculo entre mensagens de um mesmo contato. As partições
originais foram mantidas: 53 em treino, 16 em validação e 14 em teste.

Os rótulos de impacto e urgência e a confiança qualitativa foram preservados;
a prioridade segue a matriz do projeto. `origem=REAL_ROTULADO_IA` indica a
procedência do cenário, enquanto `fonte=RELATO_GENERALIZADO_IA` identifica a
reescrita. A revisão humana continua `PENDENTE`. A generalização reduz detalhes
e pode alterar sinais usados pelo modelo; esta versão não substitui avaliação
com mensagens reais independentes nem comprova qualidade operacional.

Não foi feita certificação de anonimato ou análise de autorização de uso da
fonte. A transformação descrita é uma redução de dados identificáveis, com
verificação automática e revisão por IA. A publicação deve respeitar a
autorização aplicável à base original.

## Reprodução

Na raiz do projeto, com os arquivos privados disponíveis localmente:

```powershell
python ia/export_public_dataset.py --source ia/data/processed/base-ampla-com-reais-v1/reais-rotulados-ia.csv --rewrites ia/data/processed/reescritas-publicacao.json --synthetic ia/data/processed/base-ampla-v1/treinamento-amplo.csv --output dataset/publico-nova-versao
```

O JSON de reescritas é privado e vinculado ao hash da fonte; não é publicado.
A rotina exige um destino novo, exporta uma lista restrita de campos e valida
integridade, classes e ausência de vazamento de textos/grupos entre partições.
A ordem aleatória faz os IDs e hashes variarem entre novas exportações.

Para treinar com esta versão:

```powershell
cd ia
python train.py --dataset ../dataset/publico/treinamento-publico.csv --output models/priority-publico.joblib
```

## Verificação da exportação

Em 16/11/2026, foram conferidos os 108.091 registros, a preservação dos
rótulos e das partições dos 83 generalizados, a igualdade de todas as células
de dados entre CSV e Excel e os hashes dos arquivos. A busca automática não
encontrou e-mails, telefones, URLs, IPs ou referências ao caminho pessoal nos
textos exportados; os IDs privados e os hashes de contato também foram
excluídos. Essa busca cobre padrões reconhecíveis, não identificação indireta.

Os cinco testes de `ia/tests/test_public_export.py` passaram. Eles verificam
a exclusão de campos privados, a preservação da origem e da revisão pendente,
a integridade do Excel e a rejeição de fonte alterada, reescrita incompleta,
cópia literal ou metadados indevidos. Os arquivos locais `.env`, os dados
intermediários e os modelos tiveram sua exclusão do Git confirmada.
