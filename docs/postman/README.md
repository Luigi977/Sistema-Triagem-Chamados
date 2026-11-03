# Testes da API no Postman

A coleção v2.1 contém **122 requisições**, com autenticação, técnicos/equipes, chamados, mensagens, continuidade de atendimento e integração Evolution real. O ambiente principal é o [Docker da aplicação](../docker.md). Não existe mais provedor Postman simulado no repositório.

## Importar e executar

1. Importe `triagem-chamados.postman_collection.json` e `local.postman_environment.json`.
2. Defina `baseUrl` (padrão `http://localhost:8080`), `adminLogin` e `adminPassword` somente no seu ambiente privado. No Docker, as credenciais iniciais estão em `.env.docker`.
3. Para regressão completa, use um backend e banco **de teste**, pois a coleção cria técnicos, chamados e mensagens sintéticos. Execute as pastas em ordem. Não execute esse fluxo para conferir uma instalação com dados reais.
4. Respostas negativas 400/401/403/404/409 são esperadas e têm verificações próprias. IDs são capturados e os dados são únicos por execução; não use IDs de registros existentes.

Tokens e senhas dos técnicos sintéticos ficam em variáveis locais (`pm.variables`). Não versione ambientes exportados com credenciais, tokens, QR Codes, IDs de execução ou respostas reais.

## Pastas

| Pasta | Conteúdo |
| --- | --- |
| 00 — Autenticação | Login administrativo, negativas, criação e login de técnicos N1/N2. |
| 01 — Saúde | `GET /health`. |
| 02 — Chamados | Criação, consultas, filtros, status, atribuição, detalhes e equipe. |
| 03 — Validações | Entradas inválidas, recursos ausentes e preservação após erro. |
| 04 — Mensagens | Conteúdo, datas, autoria, ordem, isolamento e deduplicação. |
| 05 — Encerramento | Causa/solução obrigatórias, bloqueio de edição e continuidade. |
| 06 — Evolution (opcional) | Webhook, aliases/LID, envio e eco sem duplicação, encerramento e novo atendimento. |
| 06B — Conexão real (opcional) | Acesso administrativo, QR real, estado e ausência de chaves na resposta. |
| 07 — Acesso | Permissões, autoria, desativação e logout. |

As requisições herdam Bearer administrativo, exceto cenários explicitamente anônimos ou de técnicos. O webhook usa `noauth` mais `X-Webhook-Token`; ele não usa a sessão do painel. A chave da Evolution não existe como variável da coleção e permanece exclusivamente no backend.

## Instalação real, sem gerar dados sintéticos

Use a tela `/whatsapp.html` ou:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-stack.ps1
```

Esse script verifica backend/Evolution reais, prepara a instância, obtém QR quando necessário e consulta o estado e a contagem de chamados. Não cria chamados/mensagens nem envia texto ao WhatsApp.

A pasta **06B** é habilitada por `whatsappConnectionTestsEnabled=true`, com backend no modo gerenciado. Pode ser usada após obter as sessões da pasta 00, mas a pasta 00 cria técnicos de teste: para uma checagem operacional sem dados adicionais, prefira o script acima. `POST /api/whatsapp/conectar` retorna 200 com `instancia`, `estado` (`open`/`connecting`) e `qrCode` (PNG data URL ou nulo), sempre com `Cache-Control: no-store`. O endpoint exige administrador; técnico recebe 403 e anônimo 401. Desativada retorna 503; fora do modo gerenciado retorna 409; falha upstream retorna 502/504. Não salve a resposta QR como exemplo.

## Regressão Evolution isolada

A pasta **06** é habilitada somente por `evolutionTestsEnabled=true`. Configure `evolutionInstance` e `evolutionWebhookToken` para coincidir com o backend de teste. Ela usa mensagens e destinatários fictícios para verificar o contrato e **realiza envios pelo provedor configurado**; não ative contra o WhatsApp da empresa. Por padrão ambas as pastas opcionais ficam desabilitadas.

As consultas aguardam o worker por até cinco segundos. A pasta cria três chamados e seis mensagens adicionais por iteração. Testes de timeout, indisponibilidade, concorrência e eco antecipado são executados pela suíte Java com um servidor HTTP isolado, sem publicar um serviço simulado ou conectar um telefone.

Os controles `__control`, variáveis `evolutionMockEnabled`/`evolutionMockUrl`, pastas de mocks/simulações e antigo script que injetava mensagens foram removidos. Esses artefatos não participam da aplicação real.

## Validar arquivos e executar regressão

```powershell
node scripts/validate-postman.cjs
cd backend
mvn test
cd ..
# Somente contra um banco de teste, com credenciais em ambiente privado:
npx --yes newman@6.2.1 run docs/postman/triagem-chamados.postman_collection.json -e CAMINHO-PRIVADO/ambiente-teste.json --iteration-count 2
```

O validador confere os JSONs, corpos de exemplos e sintaxe dos scripts. A coleção conserva a execução sequencial e os exemplos sintéticos conforme `AGENTS.md`; a operação Docker começa sem chamados/mensagens de demonstração. Não se apagam registros reais ou bancos legados para remover dados simulados.

## Evidência histórica

Antes da implantação Docker, a versão anterior passou em 52 testes Java e duas iterações Newman (250 requisições e 380 verificações) com PostgreSQL isolado e Evolution simulada. Esses números se referem ao contrato anterior. A implantação atual usa a Evolution real; seus resultados e a necessidade de vincular o celular estão descritos no guia Docker.

Na implantação real de 30/10/2026, três requisições operacionais da pasta 06B passaram em cinco verificações Newman, sem falhas e sem criação de chamados/mensagens. A negação para técnicos foi validada na suíte Java, que passou em 54 testes. Os JSONs e 129 scripts passaram na validação de sintaxe. O estado real foi `connecting`, com QR disponível; o celular ainda precisa ser vinculado. Não houve execução da coleção completa na base operacional.
