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
| `POST` | `/api/chamados` | Cria chamado com `telefoneCliente` obrigatório e `nomeCliente` e `prioridadeSugerida` opcionais. |
| `GET` | `/api/chamados` | Lista chamados com filtros e paginação. |
| `GET` | `/api/chamados/{id}` | Busca um chamado pelo ID. |
| `GET` | `/api/chamados/abertos` | Lista chamados nos status `ABERTO`, `EM_ATENDIMENTO` e `AGUARDANDO_USUARIO`. |
| `GET` | `/api/chamados/cliente/{telefone}` | Lista chamados do cliente com o telefone informado. |
| `PATCH` | `/api/chamados/{id}/status` | Altera para `ABERTO`, `EM_ATENDIMENTO` ou `AGUARDANDO_USUARIO`. |
| `PATCH` | `/api/chamados/{id}/prioridade` | Define a prioridade final em `prioridadeFinal`. |
| `PATCH` | `/api/chamados/{id}/responsavel` | Define o técnico em `responsavel`. |
| `POST` | `/api/chamados/{id}/encerrar` | Encerra o chamado e exige `causaReal`. |

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
{ "causaReal": "Falha no equipamento substituído" }
```

A prioridade sugerida é persistida separadamente da prioridade final. Chamados encerrados não podem ser alterados; um novo chamado do mesmo telefone recebe o ID do chamado anterior quando o registro mais recente do cliente está encerrado. A causa real e a data de encerramento do chamado anterior permanecem preservadas.

## Como testar

Execute a suíte de testes a partir do diretório `backend`:

```powershell
cd backend
mvn test
```

O teste de contexto usa um banco H2 em memória, portanto não precisa de uma instância do PostgreSQL para ser executado.
