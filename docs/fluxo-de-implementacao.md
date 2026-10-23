# Fluxo de implementação e commits

Entregar incrementos funcionais, começando pelo domínio que sustenta as telas. A referência de escopo é [mapa-de-funcionalidades.md](mapa-de-funcionalidades.md).

## Sequência de entregas

| Ordem | Branch / commit sugerido | Entrega e critério de conclusão | Estado |
| --- | --- | --- | --- |
| 1 | `feat/base-dados-chamados` — `feat(chamados): completa dados e encerramento` | Resumo, descrição, natureza, setor, canal, equipe, prioridades padronizadas e solução aplicada; criação, edição e encerramento testados por HTTP com persistência | Implementado e validado nesta branch |
| 2 | `feat/migracoes-banco` — `feat(database): versiona schema de chamados` | Migrações versionadas; testar banco vazio e atualização de banco existente sem apagar registros; substituir atualização automática do Hibernate por validação | Pendente |
| 3 | `feat/acesso-tecnicos` — `feat(auth): autentica tecnicos e restringe acesso` | Identidade de técnicos, equipes e autorização; testar acesso negado, atribuição e autor das operações | Pendente |
| 4 | `feat/mensagens-whatsapp` — commits de mensagens, webhook e envio | Persistir conversa, autenticar webhook Evolution, deduplicar eventos, associar chamado e controlar falhas de envio | Pendente |
| 5 | `feat/frontend-atendimento` — commits de estrutura React e atendimento | Lista/detalhe de chamados e conversa integrada; estados vazio, carregamento, erro e envio; validar fluxo no navegador | Pendente |
| 6 | `feat/triagem-humana` — commits de triagem e histórico | Separar sugestão de decisão final; aceitar/corrigir com autor e histórico; atender manualmente sem IA | Pendente |
| 7 | `feat/servico-ia` — commits de contrato Python e integração Java | Classificar natureza, recomendar prioridade e causas; versão do modelo/prompt, timeout e falhas testados; datasets e pesos fora do Git | Pendente |
| 8 | `feat/frontend-triagem` — `feat(frontend): revisa recomendacoes de triagem` | Tela de pendências e aceite/correção, vinculada à versão da recomendação | Pendente |
| 9 | `feat/encerramento-avaliacao` — commits de avaliação e telas | Encerrar com causa e solução; avaliar sugestões após encerramento sem modificar o chamado; testar histórico e indicadores | Pendente |
| 10 | `feat/dashboard` — commits de agregações e painel | Contagens, distribuição e fila por severidade; definir fuso/período, prioridade efetiva e desempate; conferir números com registros | Pendente |

Antes da etapa 4, definir a regra de associação de nova mensagem a chamado aberto. Antes da etapa 7, definir a matriz impacto × urgência, provedor LLM e contrato da inferência. Antes da etapa 9, definir avaliação por hipótese ou conjunto e classificação de “parcialmente útil”. Não depender de serviços reais nos testes automatizados: usar contratos e respostas controladas, além de validação separada da integração real quando configurada.

## Fluxo de cada entrega

1. Atualizar a referência de `main` e criar uma branch no checkout existente. Não é necessário criar worktree.
2. Escolher uma entrega da tabela e explicitar contrato HTTP, regras e compatibilidade.
3. Implementar a funcionalidade junto dos testes que demonstram seu comportamento, incluindo erros e persistência.
4. Executar `mvn verify` no backend com JDK 21; adicionar validações específicas de frontend/IA quando esses componentes existirem.
5. Atualizar README e mapa com contratos e limitações reais. Conferir `git diff --check`, arquivos staged e ausência de dados/segredos.
6. Criar commits pequenos e completos: `feat`, `fix`, `test` ou `docs`, com escopo. Evitar um commit separado de testes que deixe a funcionalidade anterior sem validação.
7. Enviar a branch e abrir revisão contra `main`, informando problema, mudança, testes e compatibilidade. Integrar após revisão e checks; não usar force push em `main`.
8. Marcar entrega concluída e iniciar a próxima dependência.

## Primeiro incremento: contrato e limites

- Os novos dados de abertura são opcionais para manter clientes que enviam somente telefone/nome/prioridade funcionando.
- `natureza`: `INCIDENTE`, `REQUISICAO`, `DUVIDA`; `equipe`: `N1`, `N2`; `canal`: `WHATSAPP`, `MANUAL` (padrão). São categorias iniciais, não um cadastro de equipes ou usuários.
- Prioridades: `BAIXA`, `MEDIA`, `ALTA`, `CRITICA`. Entrada textual é normalizada para maiúsculas sem remover acentos; os códigos da API não usam acentos.
- `PATCH /api/chamados/{id}/detalhes` atualiza campos informados, preserva campos omitidos/nulos e rejeita texto vazio; não limpa campos nesta versão.
- `PATCH /api/chamados/{id}/equipe` encaminha entre N1/N2. Responsável continua sendo texto até a etapa de identidade.
- Encerramento passa a exigir `solucaoAplicada` junto de `causaReal`: mudança intencional no contrato, alinhada à tela 4. Clientes antigos precisam enviar os dois campos.
- Registros antigos continuam legíveis com campos novos nulos. A etapa de migrações ainda é necessária antes de disponibilização em produção.
- Natureza atual é informada manualmente. Sugestões de natureza, decisões e histórico da IA serão entidades/contratos próprios; não sobrescrever a sugestão original.

## Validação do primeiro incremento

- `mvn verify`: 15 testes executados, sem falhas, erros ou testes ignorados; inclui 12 casos de integração HTTP com H2.
- API executada com PostgreSQL local: criação completa, edição, encaminhamento N2, rejeição de encerramento incompleto, persistência de causa/solução e bloqueio após encerramento aprovados.
- Compilação e empacotamento do JAR concluídos. Migrações e integrações externas seguem pendentes conforme a sequência acima.
