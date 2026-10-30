# Testes da API no Postman

Esta coleção reúne os endpoints atuais em **95 requisições**, com exemplos e testes automáticos. Inclui autenticação, técnicos/equipes, saúde, chamados, mensagens, filtros, validações, deduplicação, autoria e encerramento.

## Importar e executar

1. Inicie o backend seguindo o README da raiz (`cd backend` e `mvn spring-boot:run`).
2. No Postman, use **Import** para importar os arquivos desta pasta:
   - `triagem-chamados.postman_collection.json` — coleção no formato v2.1.
   - `local.postman_environment.json` — ambiente **Triagem — Local**.
3. Selecione o ambiente **Triagem — Local**. Ajuste `baseUrl` se necessário; o padrão é `http://localhost:8080`, sem barra final e sem `/api` no final.
4. Preencha `adminLogin` e `adminPassword` somente no ambiente local com a conta administrativa criada conforme [acesso de técnicos](../acesso-tecnicos.md). Não exporte credenciais.
5. Abra a coleção e use **Run** para executar todas as pastas, na ordem, inicialmente com uma iteração. Não é necessário arquivo de dados.
6. Confira os testes de cada resposta e o resultado do Runner. Respostas `400`, `404` e `409` são esperadas nos cenários de rejeição e devem passar nos testes correspondentes.

O ambiente é opcional: a coleção também define `baseUrl`. Se um ambiente estiver selecionado, o valor dele tem precedência. O fluxo de execução e a passagem de dados entre requisições seguem o [Collection Runner do Postman](https://learning.postman.com/docs/tests-and-scripts/running-collections/intro-to-collection-runs).

**Use um banco de desenvolvimento ou teste:** cada iteração cria quatro chamados, seis mensagens e dois técnicos sintéticos e encerra o chamado principal. Os registros permanecem no banco porque ainda não há endpoint de exclusão. Nenhuma resposta é enviada ao WhatsApp.

## Ordem dos cenários

| Pasta | Verificações |
| --- | --- |
| 00 — Autenticação | Login administrativo, negativas, criação e login de técnicos N1/N2, identidade e equipes. |
| 01 — Saúde | `GET /health` e corpo `OK`. |
| 02 — Chamados | Criação completa e mínima, consulta por ID/telefone, paginação, abertos, status, prioridade, responsável, detalhes, equipe e combinação de filtros. |
| 03 — Validações de chamados | Campos obrigatórios, categorias inválidas, limites, recurso inexistente, rejeição de encerramento pelo status e preservação dos dados após falha. |
| 04 — Mensagens | Histórico vazio, três remetentes, consulta individual, conteúdo preservado, precisão da data, ordem cronológica e desempate, duplicatas no mesmo/outro chamado, data automática, isolamento e entradas inválidas. |
| 05 — Encerramento | Causa e solução obrigatórias, encerramento válido, bloqueio de alterações posteriores, preservação do histórico e novo chamado vinculado ao anterior. |

A pasta 06 valida acesso negado por equipe/perfil, atribuição por ID, preservação de dados, autoria, desativação e logout. Tokens e senhas sintéticas ficam em variáveis locais da execução (`pm.variables`); não são gravados no ambiente de exemplo. O administrador permanece ativo, mas sua sessão de teste é encerrada ao final.

Os testes verificam status HTTP e, nos fluxos de sucesso, campos de resposta, IDs, `Location`, contagens e regras de negócio. A coleção é sequencial; o teste de concorrência de mensagens continua na suíte Java.

## Variáveis e execução manual

| Variável | Uso |
| --- | --- |
| `baseUrl` | Endereço do backend. |
| `adminLogin`, `adminPassword` | Credenciais administrativas locais obrigatórias; nunca versionar valores. |
| `execucaoId`, `telefoneTeste`, `outroTelefoneTeste`, `identificadorExternoTeste` | Geradas antes da criação do chamado completo, com valores novos a cada iteração. |
| `chamadoId`, `outroChamadoId`, `novoChamadoId` | Capturadas das respostas de criação. |
| `mensagemClienteId`, `mensagemTecnicoId`, `mensagemSistemaId`, `mensagemSemIdExternoId` | Capturadas ao registrar mensagens. |
| `idInexistente` | `-1`, usado para os cenários de recurso ausente. |

Não crie cópias das variáveis geradas no ambiente; isso pode sobrepor os valores capturados pela coleção. Os textos acima do limite são gerados por scripts locais às requisições.

Para testar com **Send**, execute primeiro a pasta **00 — Autenticacao e tecnicos**, depois **Criar chamado completo e preparar execução** e **Criar chamado mínimo para isolamento**. Execute depois as requisições que produzem os dados usados pelo cenário desejado. Por exemplo, consultar uma mensagem exige registrá-la antes. Testes de contagem e estado pressupõem a sequência completa, sem inserções extras.

Depois de encerrar um chamado, suas alterações retornam `409`. Para repetir o fluxo, execute a coleção inteira novamente; a primeira criação limpa os IDs anteriores e gera novos dados. Se quiser usar com **Send** os IDs de uma execução do Runner, habilite a opção de manter os valores das variáveis nessa execução.

## Executar pelo terminal

Com Node.js/npm e o backend disponíveis, execute da raiz do projeto:

```powershell
npx --yes newman@6.2.1 run docs/postman/triagem-chamados.postman_collection.json -e docs/postman/local.postman_environment.json
```

Para trocar a porta e testar duas iterações:

```powershell
npx --yes newman@6.2.1 run docs/postman/triagem-chamados.postman_collection.json -e docs/postman/local.postman_environment.json --env-var "baseUrl=http://localhost:18081" --iteration-count 2
```

A primeira execução pode baixar o Newman. Ele executa os mesmos scripts da coleção, conforme a [documentação oficial](https://learning.postman.com/docs/collections/using-newman-cli/installing-running-newman). Falhas de requisição ou de asserção produzem código de saída diferente de zero.

## Atualizar junto de cada implementação

1. Adicionar ou ajustar a requisição na pasta correspondente, incluindo método, rota, parâmetros e corpo.
2. Atualizar os testes de sucesso e os erros relevantes ao contrato. Conferir também as consultas que demonstram persistência e preservação de dados.
3. Se houver dependências entre requisições, capturar os IDs e respeitar a ordem. Manter dados novos por execução.
4. Executar a coleção inteira contra um backend de teste. Para alterações na preparação dos dados, verificar também duas iterações consecutivas.
5. Se editar no Postman, exportar em Collection v2.1 para o mesmo arquivo. Antes de versionar, limpar valores de variáveis geradas e remover respostas salvas com dados locais; manter somente exemplos fictícios.
6. Atualizar este guia, o ambiente quando necessário e a documentação do contrato no mesmo incremento da implementação.

Essa manutenção está registrada também no `AGENTS.md` da raiz e no fluxo de implementação do projeto.

## Validação desta versão

Em 27/10/2026, a coleção foi executada com Newman 6.2.1 contra o backend Spring Boot em uma instância temporária com H2 em memória: **duas iterações, 130 requisições e 196 testes, sem falhas**. Os JSONs e a sintaxe dos scripts também foram validados, e todas as rotas dos controllers atuais têm uma requisição correspondente. A validação foi pelo Newman, sem execução manual na interface do Postman e sem uso do PostgreSQL.

## Contrato de segurança

Todas as requisições herdam Bearer do administrador, exceto cenários explicitamente anônimos ou executados como técnicos. A atribuição envia `responsavelId` capturado no cadastro. O encaminhamento para N2 remove o responsável anterior; a coleção atribui o técnico N2 antes de testar os filtros. Respostas 401/403 são esperadas nos cenários negativos. Para Newman, use uma cópia privada do ambiente com as credenciais; mantenha o arquivo fora do Git.
