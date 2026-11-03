# Aplicação e Evolution API em Docker

O Compose executa o backend Java, a Evolution API v2.3.7, Redis e dois PostgreSQL separados: um da aplicação e outro da Evolution. A integração fica habilitada nesse ambiente, sem provedor simulado ou carga de chamados/mensagens de demonstração. O frontend principal ainda não foi implementado; a tela administrativa de conexão é servida pelo backend.

## Iniciar

Com Docker Desktop ativo, execute na raiz:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-stack.ps1
```

O parâmetro de política vale somente para esse processo PowerShell, sem mudar a política da máquina. O script gera `.env.docker` na primeira execução com senhas/chaves aleatórias; mantém os mesmos valores nas execuções seguintes. Esse arquivo é ignorado pelo Git e não substitui o `.env` usado pelo backend fora de Docker.

Abra **http://localhost:8080/whatsapp.html**. Entre com `DOCKER_ADMIN_LOGIN` e `DOCKER_ADMIN_PASSWORD`, disponíveis apenas no `.env.docker` local. Clique em **Gerar QR Code** e leia o código no celular do suporte em **WhatsApp → Aparelhos conectados → Conectar aparelho**. A tela consulta o estado real a cada cinco segundos. Não guarda tokens no armazenamento do navegador nem recebe a chave da Evolution.

O backend prepara automaticamente a instância `suporte` e o webhook interno `http://backend:8080/api/webhooks/evolution`, usando um segredo próprio no header. Se a Evolution ainda estiver iniciando, tenta novamente a cada 30 segundos. O botão de conexão também prepara/configura a instância antes de obter o QR Code. Não é necessário inserir uma chave no navegador ou configurar o webhook manualmente.

Após o vínculo, mande uma mensagem de outro celular para o número do suporte. Ela será associada a um chamado ativo ou abrirá um atendimento N1. Somente mensagens novas são processadas; o provisionamento não importa o histórico antigo do aparelho.

## Persistência e operação

- Volumes `triagem_dados`, `evolution_dados`, `evolution_cache` e `evolution_sessoes` conservam chamados, dados e sessão após reinício dos contêineres.
- `restart: unless-stopped` recupera os serviços quando o Docker reinicia, desde que não tenham sido parados manualmente.
- O Docker e o computador precisam permanecer ligados. A sessão do WhatsApp pode expirar/ser revogada; nesse caso a tela mostra a desconexão e permite novo QR. Não se simula estado conectado em caso de falha.
- Apenas o backend é publicado em `127.0.0.1:8080`. Evolution, Redis e bancos ficam na rede interna Docker. O PostgreSQL já instalado no Windows e seu banco existente não são alterados. Este ambiente começa com um banco novo e somente o administrador inicial; não migra automaticamente dados do banco local anterior.
- Não exclua os volumes para uma atualização normal. `down` mantém os dados; `down -v` remove dados e sessões e não faz parte do fluxo de atualização.

Comandos na raiz:

```powershell
docker compose --env-file .env.docker ps
docker compose --env-file .env.docker logs --tail 100 backend evolution
docker compose --env-file .env.docker restart
docker compose --env-file .env.docker stop
# Atualizar a imagem do backend sem apagar dados:
docker compose --env-file .env.docker up -d --build
```

Senhas de banco, chave da Evolution e segredo do webhook ficam no arquivo privado e nos ambientes dos respectivos contêineres. Não publique saídas de `docker compose config` ou `docker inspect` que incluam variáveis. O script usa `config --quiet`, que valida sem exibir os valores.

## Hospedagem

Esta implantação hospeda os serviços na máquina que executa Docker. Para acesso público em servidor/VPS, transfira código e configuração privada, configure domínio/HTTPS por proxy reverso e mantenha os bancos e Evolution internos. `TRIAGEM_BIND_ADDRESS` e `TRIAGEM_HTTP_PORT` controlam a publicação do backend; o padrão local evita expor os serviços na rede da empresa sem configuração. Não foi contratado servidor nem configurado domínio externo por esse incremento.

## Verificar sem criar dados de teste

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-stack.ps1
```

O teste acessa a aplicação e a Evolution **reais**: verifica saúde, autentica o administrador local, prepara/conecta a instância, consulta seu estado e a contagem de chamados, depois revoga a sessão de teste. Não envia mensagem ao WhatsApp, não injeta webhook e não cria chamados. QR Code e segredos não são impressos ou salvos no relatório.

A suíte Java (`cd backend; mvn test`) continua usando dados isolados e um servidor HTTP de teste para reproduzir timeout, indisponibilidade e concorrência. Esses testes nunca são executados pelo contêiner de produção. As antigas ferramentas Postman de simulação foram removidas. A coleção preserva exemplos sintéticos conforme o `AGENTS.md`; rode o fluxo completo somente em banco de teste. Para a instalação real, use a tela ou `test-stack.ps1`.

O primeiro vínculo e o teste de recebimento de uma mensagem real dependem da leitura do QR no celular da empresa. A aplicação não pode realizar essa autorização pelo usuário.

## Verificação desta instalação

Em 30/10/2026, os cinco serviços foram iniciados e ficaram saudáveis no Docker Desktop. A porta 8080 já estava ocupada; esta instalação usa **http://localhost:8085/whatsapp.html**, conforme `TRIAGEM_HTTP_PORT=8085` no arquivo privado. O backend criou a instância `suporte`, persistiu o webhook com `MESSAGES_UPSERT` e `X-Webhook-Token` e recebeu um QR Code real da Evolution. A conexão está em `connecting`, aguardando o vínculo pelo celular.

O teste operacional passou com zero chamados e zero mensagens no banco. Newman executou três requisições reais da pasta 06B com cinco verificações sem falhas, incluindo negação anônima, preparação/QR e status. A suíte Java passou em 54 testes; o caso 404 de instância inexistente, observado na Evolution real, foi incluído e validado novamente. JSONs e 129 scripts Postman foram validados. HTML e JavaScript foram servidos com HTTP 200; não havia navegador conectado à automação para inspeção visual da tela.

A coleção completa desta versão não foi repetida contra esta instalação; ela cria registros sintéticos e é destinada ao ambiente de regressão isolado. A recepção/envio com um número real e a persistência de uma sessão já pareada após reinício dependem do vínculo do celular e ainda não foram verificados.

Referências: [Compose oficial da Evolution](https://github.com/evolution-foundation/evolution-api/blob/2.3.7/docker-compose.yaml), [configuração da versão utilizada](https://github.com/evolution-foundation/evolution-api/blob/2.3.7/.env.example), [rotas de instância](https://github.com/evolution-foundation/evolution-api/blob/2.3.7/src/api/routes/instance.router.ts).
