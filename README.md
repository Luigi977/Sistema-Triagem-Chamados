# Backend

API do Sistema de Triagem de Chamados, desenvolvida com Java, Spring Boot e Maven.

## Dependências

- JDK 21.
- Apache Maven 3.6.3 ou superior.
- PostgreSQL disponível localmente para executar a aplicação.

As dependências de bibliotecas são baixadas pelo Maven a partir do `pom.xml`:

- Spring Boot Starter Web, para a API HTTP.
- Spring Boot Starter Data JPA, para persistência com JPA/Hibernate.
- Driver JDBC do PostgreSQL, para conexão com o banco.
- Spring Boot Starter Test e H2, usados nos testes.

## Como instalar

1. Instale o JDK 21, o Maven e o PostgreSQL.
2. Confira as instalações:

   ```powershell
   java -version
   mvn -version
   ```

3. Crie o banco de dados usado por padrão pela aplicação:

   ```sql
   CREATE DATABASE triagem_chamados;
   ```

4. No diretório `backend`, baixe as dependências e compile o projeto:

   ```powershell
   cd backend
   mvn clean install
   ```

## Como configurar

A aplicação lê as propriedades em `src/main/resources/application.properties`. A conexão com o PostgreSQL pode ser configurada pelas variáveis de ambiente:

| Variável | Valor padrão |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/triagem_chamados` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Configure a senha do usuário do PostgreSQL |

O arquivo de exemplo com essas variáveis fica na raiz do repositório: `.env.example`. Se ainda não existir um `.env` na raiz, crie-o a partir do exemplo e ajuste os valores para o seu PostgreSQL. Não compartilhe nem versione credenciais locais.

Quando a aplicação é iniciada com o diretório de trabalho `backend`, ela importa opcionalmente `../.env` como arquivo de propriedades. Também é possível definir as variáveis de ambiente diretamente no terminal ou no ambiente de execução.

A porta HTTP padrão é `8080` e pode ser alterada pela propriedade `server.port`.

## Como executar

Com o PostgreSQL ativo, execute os comandos a partir do diretório `backend`:

```powershell
cd backend
mvn spring-boot:run
```

Para gerar e executar o JAR:

```powershell
mvn clean package
java -jar target/triagem-chamados-0.0.1-SNAPSHOT.jar
```

Com a aplicação iniciada, o endpoint de verificação de saúde responde em `http://localhost:8080/health` e retorna `OK`.

## API de chamados

Todas as rotas abaixo usam o prefixo `/api/chamados`. Os endpoints de listagem retornam páginas; use `page` (começando em zero), `size` e `sort` para controlar paginação e ordenação. A listagem geral aceita os filtros opcionais `status`, `telefone` (busca parcial), `prioridadeFinal` e `responsavel` (busca parcial).

| Método | Rota | Descrição |
| --- | --- | --- |
| `POST` | `/api/chamados` | Cria chamado com `telefoneCliente` obrigatório; aceita nome, prioridade sugerida, resumo, descrição, natureza, setor, canal e equipe opcionais. |
| `GET` | `/api/chamados` | Lista chamados com filtros e paginação. |
| `GET` | `/api/chamados/{id}` | Busca um chamado pelo ID. |
| `GET` | `/api/chamados/abertos` | Lista chamados nos status `ABERTO`, `EM_ATENDIMENTO` e `AGUARDANDO_USUARIO`. |
| `GET` | `/api/chamados/cliente/{telefone}` | Lista chamados do cliente com o telefone informado. |
| `PATCH` | `/api/chamados/{id}/status` | Altera para `ABERTO`, `EM_ATENDIMENTO` ou `AGUARDANDO_USUARIO`. |
| `PATCH` | `/api/chamados/{id}/prioridade` | Define a prioridade final em `prioridadeFinal`. |
| `PATCH` | `/api/chamados/{id}/responsavel` | Define o técnico em `responsavel`. |
| `PATCH` | `/api/chamados/{id}/detalhes` | Atualiza resumo, descrição, natureza, setor e canal informados; preserva campos omitidos/nulos. |
| `PATCH` | `/api/chamados/{id}/equipe` | Define `equipe` como `N1` ou `N2`. |
| `POST` | `/api/chamados/{id}/encerrar` | Encerra o chamado e exige `causaReal` e `solucaoAplicada`. |

Exemplos de corpos JSON:

```json
{ "telefoneCliente": "11999990000", "nomeCliente": "Cliente", "prioridadeSugerida": "ALTA" }
```

```json
{ "status": "EM_ATENDIMENTO" }
```

```json
{ "prioridadeFinal": "MEDIA" }
```

```json
{ "responsavel": "Técnico 1" }
```

```json
{ "causaReal": "Fonte de alimentação queimada", "solucaoAplicada": "Substituição da fonte e teste de funcionamento" }
```

Exemplo de abertura completa:

```json
{
  "telefoneCliente": "11999990000",
  "nomeCliente": "Cliente de teste",
  "resumo": "Computador não liga",
  "descricao": "Equipamento sem sinal de energia após testar outra tomada",
  "natureza": "INCIDENTE",
  "setor": "Financeiro",
  "canal": "WHATSAPP",
  "equipe": "N1",
  "prioridadeSugerida": "CRITICA"
}
```

Categorias da API: natureza `INCIDENTE`, `REQUISICAO` ou `DUVIDA`; prioridade `BAIXA`, `MEDIA`, `ALTA` ou `CRITICA`; canal `MANUAL` ou `WHATSAPP`; equipe `N1` ou `N2`. São normalizadas para maiúsculas e rejeitam valores desconhecidos. `MANUAL` é o canal padrão se omitido. Informar `WHATSAPP` registra o canal; a integração com o provedor WhatsApp será implementada em outra etapa.

Limites: resumo 200 caracteres, setor e nome 150, descrição/causa/solução 10.000. A edição de detalhes preserva campos nulos/omitidos e rejeita textos vazios; não permite limpar um campo nesta versão. Dados opcionais antigos podem permanecer nulos.

**Mudança de contrato:** encerramento agora exige também `solucaoAplicada`. Clientes que enviavam apenas `causaReal` devem ser atualizados. Chamados encerrados continuam bloqueados para edição.

A prioridade sugerida é persistida separadamente da prioridade final. Chamados encerrados não podem ser alterados; um novo chamado do mesmo telefone recebe o ID do chamado anterior quando o registro mais recente do cliente está encerrado. A causa real e a data de encerramento do chamado anterior permanecem preservadas.

## API de mensagens

| Método | Rota | Descrição |
| --- | --- | --- |
| `POST` | `/api/chamados/{id}/mensagens` | Registra mensagem no chamado existente; retorna `201` e `Location` da mensagem. |
| `GET` | `/api/chamados/{id}/mensagens` | Retorna uma lista em ordem crescente de `dataEnvio`, com desempate por `id`; retorna `[]` quando o chamado não tem mensagens. |
| `GET` | `/api/mensagens/{id}` | Consulta uma mensagem específica. |

Exemplo de registro:

```json
{
  "remetente": "CLIENTE",
  "conteudo": "Meu computador não liga.",
  "tipo": "TEXTO",
  "dataEnvio": "2026-10-27T10:15:00",
  "identificadorExterno": "wamid.exemplo"
}
```

`remetente`, `conteudo` e `tipo` são obrigatórios. Remetentes permitidos: `CLIENTE`, `TECNICO` e `SISTEMA`. Remetente e tipo são normalizados para maiúsculas e sem espaços nas extremidades. O tipo é uma classificação textual livre de até 30 caracteres, como `TEXTO`, `IMAGEM` ou `AUDIO`; esta etapa armazena o conteúdo informado, sem processar anexos. O conteúdo preserva espaços e quebras de linha, deve conter texto e aceita até 10.000 caracteres.

`dataEnvio` aceita data/hora ISO 8601 sem fuso, seguindo o padrão dos chamados; se nula ou omitida, usa a hora local do servidor. A precisão é truncada para microssegundos antes de persistir, mantendo registro e consulta consistentes. Integrações devem converter horários para o mesmo fuso antes do registro. `identificadorExterno` é opcional, tem limite de 255 caracteres, preserva maiúsculas/minúsculas e remove espaços nas extremidades; vazio equivale a ausente. O identificador tem unicidade global no banco, inclusive entre chamados diferentes e em requisições simultâneas. Uma repetição retorna `409 Conflict` e preserva a mensagem original; mensagens sem identificador podem ser registradas várias vezes.

A resposta contém `id`, `chamadoId` e todos os campos do exemplo. Chamado ou mensagem inexistente retorna `404`; campos inválidos retornam `400`. O registro também permite associar histórico a chamados encerrados, sem alterar seus dados. A API persiste mensagens; recebimento automático por webhook, envio real de respostas pelo WhatsApp e identidade/autenticação do técnico ainda estão pendentes.

A tabela `mensagens`, a chave estrangeira para `chamados`, a restrição de unicidade e o índice do histórico são criados pela configuração atual `spring.jpa.hibernate.ddl-auto=update`. Migrações versionadas seguem como etapa pendente.

## Como testar

Para testar a API no Postman, importe a [coleção de testes](docs/postman/triagem-chamados.postman_collection.json) e o [ambiente local](docs/postman/local.postman_environment.json). A coleção cobre os endpoints atuais, captura IDs automaticamente e inclui cenários de sucesso e erro. Veja a [ordem de execução e instruções](docs/postman/README.md). Ela deve ser atualizada junto de cada implementação que altere a API.

Execute a suíte de testes a partir do diretório `backend`:

```powershell
cd backend
mvn test
```

Os testes usam bancos H2 em memória, portanto não precisam de uma instância do PostgreSQL. A integração de mensagens cobre registro e consulta por HTTP, histórico cronológico, isolamento entre chamados, entradas inválidas, recursos ausentes e duplicatas sequenciais/concorrentes.

## Mapa de funcionalidades

Acompanhe as etapas, critérios de conclusão e branches no [fluxo de implementação e commits](docs/fluxo-de-implementacao.md).

Consulte o [mapa de funcionalidades](docs/mapa-de-funcionalidades.md) para ver os requisitos das telas e diagramas, a situação atual e a ordem sugerida de implementação.

## Arquivos que devem ficar fora do GitHub

Revisão do checkout atual e das regras de `.gitignore`. O código da IA deve ser versionado; dados privados, credenciais e artefatos gerados precisam de tratamento diferente. Não há motivo para ignorar a pasta `ia/` inteira.

### Achados atuais

- `dataset/original/`, `ia/data/raw/` e `ia/models/` já são ignorados, preservando `.gitkeep`.
- `dataset/anonymized/`, `dataset/labeled/`, `ia/data/processed/` e `ia/data/test/` são ignoradas integralmente. Seus arquivos `.gitkeep` foram removidos do versionamento; arquivos locais são preservados.
- `.env` e `.env.*` já são ignorados; `.env.example` é permitido e deve conter apenas nomes e exemplos sem segredo.
- `backend/target/`, dependências/build do frontend, ambientes virtuais Python e alguns caches já são ignorados.
- Os scripts de IA e `requirements.txt` estão rastreados e vazios. Isso é estrutura de código, não vazamento de modelos ou datasets.
- No inventário atual não há datasets ou pesos de modelos rastreados. Os artefatos Java encontrados em `backend/target/` são ignorados.
- Esta revisão de caminhos e configuração não equivale a uma varredura completa de segredos nem à auditoria do histórico Git. `.gitignore` não detecta segredos escritos dentro de código/documentos.

### Não publicar com dados reais ou segredos

| Caminho / tipo | Motivo e tratamento |
| --- | --- |
| `.env`, `.env.local`, `.env.production` e equivalentes | Podem conter senhas, tokens e URLs privadas; usar configuração segura do ambiente |
| Chaves privadas, arquivos de credenciais, keystores com chave privada | Acesso a serviços; manter em gerenciador de segredos, nunca em exemplos ou documentação |
| Sessões, tokens, QR codes de pareamento e armazenamento de autenticação do WhatsApp/Evolution API | Podem permitir acesso à conta; usar volumes privados fora do checkout |
| `dataset/original/**` e `ia/data/raw/**` | Conversas, nomes, telefones e detalhes internos; armazenamento privado com acesso controlado |
| `dataset/labeled/**` | Rotular não remove dados pessoais; tratar como dados privados por padrão |
| `dataset/anonymized/**` | O nome da pasta não comprova anonimização; revisar risco de reidentificação e autorização antes de qualquer publicação |
| `ia/data/processed/**` e `ia/data/test/**` | Transformação ou divisão em teste não elimina dados sensíveis |
| Dumps PostgreSQL, backups, exports CSV/JSON, anexos de chamados e capturas com dados reais | Podem reproduzir dados de clientes; manter fora do Git e aplicar controles de armazenamento |
| Logs de aplicação/IA, traces e respostas de LLM contendo conversas | Podem registrar dados pessoais e tokens; reduzir conteúdo e armazenar fora do checkout |
| Mapeamentos usados na anonimização | Permitem reidentificação; guardar separadamente dos datasets e fora do Git |

### Artefatos gerados: manter fora do Git comum

Estes itens não são necessariamente secretos, mas não devem acompanhar o código neste projeto. Se houver necessidade de distribuir um modelo público, revisar licença e conteúdo e usar um registro de modelos ou mecanismo adequado de artefatos.

| Caminho / exemplo | Tratamento |
| --- | --- |
| `ia/models/**` | Pesos, checkpoints, tokenizadores treinados e vetorizadores: `.pkl`, `.pickle`, `.joblib`, `.pt`, `.pth`, `.ckpt`, `.h5`, `.keras`, `.onnx`, `.safetensors`, `.bin` |
| `ia/checkpoints/`, `ia/runs/`, `ia/outputs/`, `ia/mlruns/`, `ia/wandb/` | Treinamentos, rastreamento de experimentos e saídas brutas; podem também conter dados privados |
| `ia/.cache/`, caches de Hugging Face/Transformers/Torch | Downloads e caches reconstituíveis |
| `.venv/`, `venv/`, `__pycache__/`, `.pytest_cache/`, `.mypy_cache/`, `.ruff_cache/`, `.ipynb_checkpoints/` | Dependências, caches e cópias automáticas |
| `backend/target/`, `*.class`, `frontend/node_modules/`, `frontend/dist/`, `frontend/.vite/` | Compilações e dependências já protegidas pelas regras atuais |
| Arquivos de cobertura, logs e temporários locais | Gerar em CI ou localmente; publicar relatórios sanitizados somente quando houver propósito |
| Diretórios locais de banco, uploads, backups e sessões de integração | Definir caminhos explícitos e ignorá-los antes de usá-los |

Os caminhos de experimentos, sessões e uploads acima são exemplos futuros; não foram encontrados como implementação atual. Prefira ignorar diretórios de saída definidos em vez de todas as extensões `.json`, `.csv`, `.sql` ou `.bin` do repositório.

### Arquivos que devem ir para o GitHub

- `ia/train.py`, `ia/predict.py`, `ia/src/**` e testes da IA.
- `ia/requirements.txt`, futuros manifests e lockfiles para reproduzir dependências.
- Regras de classificação, matriz impacto × urgência, templates de prompt sem dados reais e contratos de entrada/saída.
- Código Java/React, `pom.xml`, manifests, lockfiles e testes.
- `database/migrations/**` e `database/schema/**` com estrutura e migrações, sem dumps de dados reais.
- `.env.example` sem valores secretos e `application.properties` usando referências a variáveis.
- `.gitkeep`, documentação, diagramas e telas com dados fictícios. As telas atuais indicam “Dados fictícios”; não assumir que futuras capturas também sejam fictícias.
- Pequenas fixtures sintéticas, anonimizadas e autorizadas após revisão; preferir diretório explícito de fixtures para não abrir exceção ampla em datasets privados.
- Relatórios de avaliação agregados, sem amostras privadas, e notebooks revisados com saídas sensíveis removidas.

### Complemento recomendado ao `.gitignore`

As quatro regras de datasets abaixo já foram aplicadas. As demais regras são recomendações para futuras saídas e caches e devem acompanhar os caminhos efetivamente escolhidos na implementação.

```gitignore
# Dados locais de IA: pastas integralmente fora do versionamento
dataset/anonymized/
dataset/labeled/
ia/data/processed/
ia/data/test/

# Saídas de experimentos e caches
ia/checkpoints/
ia/runs/
ia/outputs/
ia/mlruns/
ia/wandb/
ia/.cache/
**/.mypy_cache/
**/.ruff_cache/
**/.ipynb_checkpoints/

# Saídas locais
*.log
.coverage
htmlcov/
frontend/coverage/
```

Não ignorar globalmente `*.sql`: isso ocultaria migrações legítimas. Não ignorar globalmente `*.json`: isso ocultaria manifests e contratos. Certificados públicos também não são automaticamente segredos; chaves privadas são.

### Verificação antes de um commit

1. Conferir `git status --short` e `git diff --cached --stat`.
2. Revisar o conteúdo de `git diff --cached`, especialmente exemplos, notebooks e configurações.
3. Usar `git check-ignore -v caminho/do/arquivo` para confirmar a regra aplicada.
4. Usar `git ls-files caminho/` para identificar dados já rastreados: adicionar regra ao `.gitignore` não os remove do índice nem do histórico.
5. Se um segredo já tiver sido publicado, revogar/rotacionar a credencial. Remover o arquivo no próximo commit não desfaz a exposição anterior; limpeza de histórico exige ação separada e coordenada.

Quando houver configuração local de ambiente em `/workspace/.cloud-setup`, ela fica fora da raiz deste repositório. Banco, caches e logs locais não devem ser copiados em bloco para o projeto.
