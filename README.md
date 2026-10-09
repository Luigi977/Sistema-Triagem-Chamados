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

## Como testar

Execute a suíte de testes a partir do diretório `backend`:

```powershell
cd backend
mvn test
```

O teste de contexto usa um banco H2 em memória, portanto não precisa de uma instância do PostgreSQL para ser executado.
