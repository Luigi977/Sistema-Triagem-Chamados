# Acesso de técnicos

Implementado na branch `feat/acesso-tecnicos`. A `main` local revisada não tinha autenticação, cadastro de técnicos ou vínculo de responsável por ID.

## Inicialização e login

Em um banco sem técnicos, configure `TRIAGEM_ADMIN_LOGIN` e `TRIAGEM_ADMIN_PASSWORD` e inicie a aplicação. A senha deve ter ao menos 12 caracteres e no máximo 72 bytes UTF-8. O primeiro administrador pertence à equipe N1. Não existe senha padrão nem cadastro público. Remova essas variáveis após a primeira inicialização; elas não redefinem uma conta existente. Outros técnicos e administradores são cadastrados por um administrador autenticado.

`POST /api/auth/login` recebe `{"login":"seu-login","senha":"sua-senha"}` e retorna `token`, `expiraEm` e `tecnico`. Use `Authorization: Bearer <token>` nas chamadas seguintes. Tokens aleatórios de 256 bits duram oito horas, são armazenados somente como hash SHA-256 no banco e revogados por `POST /api/auth/logout`. Senhas são armazenadas com BCrypt. Use HTTPS fora do ambiente local. A API não usa cookies de sessão, autenticação Basic ou formulário.

`GET /health` e `POST /api/auth/login` são públicos. Os demais endpoints exigem autenticação. Credenciais ausentes, inválidas, expiradas ou revogadas retornam `401`; uma operação sem permissão retorna `403`.

## Permissões

| Operação | Técnico | Administrador |
| --- | --- | --- |
| Consultar filas, chamados, mensagens, técnicos, equipes e histórico | Sim, inclusive outras equipes, para consulta da fila compartilhada | Sim |
| Criar chamado | Própria equipe; se omitida, usa a equipe do técnico | Qualquer equipe ou sem equipe |
| Alterar chamado ou registrar mensagem | Própria equipe, sem responsável ou atribuído a si | Qualquer chamado |
| Atribuir responsável | A si, na própria equipe, respeitando responsável existente | Técnico ativo da equipe do chamado |
| Encaminhar entre N1/N2 | Não | Sim; limpa responsável quando muda a equipe |
| Cadastrar ou desativar técnico | Não | Sim; não pode desativar a própria conta |

As regras existentes de validação e bloqueio de chamados encerrados continuam valendo. Mensagens podem registrar histórico em chamados encerrados, respeitando a autorização. `remetente` identifica a origem do conteúdo (CLIENTE/TECNICO/SISTEMA); `autorId` identifica sempre quem autenticou o registro, inclusive ao importar conteúdo de cliente ou sistema.

## Contrato

| Método e rota | Entrada / saída |
| --- | --- |
| `POST /api/auth/login` | Login e senha; retorna sessão e identidade, sem hash/senha |
| `GET /api/auth/me` | Identidade autenticada |
| `POST /api/auth/logout` | Revoga o token apresentado; `204` |
| `GET /api/equipes` | `["N1","N2"]` |
| `GET /api/tecnicos` | Lista de técnicos, incluindo inativos |
| `GET /api/tecnicos/{id}` | Técnico ou `404` |
| `POST /api/tecnicos` | `login`, `nome`, `senha`, `perfil` (ADMIN/TECNICO), `equipe` (N1/N2); `201` e Location |
| `POST /api/tecnicos/{id}/desativar` | Desativa conta e revoga suas sessões |
| `PATCH /api/chamados/{id}/responsavel` | `{"responsavelId":123}`; use o ID obtido no cadastro/listagem |
| `GET /api/chamados/{id}/operacoes` | Histórico ordenado por ID: `id`, `autorId`, `acao`, `dataOperacao` |

Login é único, normalizado para minúsculas, limitado a 100 caracteres e aceita letras ASCII, números, ponto, sublinhado, hífen e arroba. Nome aceita até 150 caracteres. Cadastro duplicado retorna `409`; entradas inválidas retornam `400`.

**Mudança de contrato:** a atribuição não aceita mais nome livre em `responsavel`; é obrigatório enviar `responsavelId`. A resposta mantém `responsavel` como nome de exibição e acrescenta `responsavelId`. Responsável inexistente retorna `404`; inativo ou de outra equipe retorna `400`.

Cada criação, alteração, atribuição, encaminhamento, encerramento e registro de mensagem gera uma operação com o autor obtido da autenticação. Campos de autoria enviados no JSON não substituem esse autor. Operações rejeitadas não alteram dados nem criam histórico. Gravação e histórico são transacionais; alterações do mesmo chamado usam bloqueio no banco para impedir decisões concorrentes com equipe/responsável desatualizados. Mensagens retornam também `autorId`.

## Migração e validação

Flyway V4 cria técnicos, sessões e operações, além dos vínculos de responsável e autor. Dados anteriores são preservados: nomes livres de responsáveis permanecem disponíveis, mas `responsavelId` e `autorId` antigos ficam nulos; não se atribui identidade retroativamente. A próxima atribuição deve escolher um técnico cadastrado.

Execute `mvn test` em `backend`. Os testes HTTP exercitam login real, acesso negado, expiração, logout, desativação, perfis, equipes, atribuição, preservação em falhas e autoria não forjável. Os testes de migração cobrem atualização de V3 e preservação de nomes legados. A coleção Postman também cobre o fluxo completo; veja [instruções](postman/README.md).

O ambiente automatizado usa H2 em modo PostgreSQL nos testes de acesso e migração. A execução em PostgreSQL de homologação permanece necessária. Rate limiting de login e recuperação/troca de senha não fazem parte desta etapa.
