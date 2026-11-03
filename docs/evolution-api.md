# Etapa 04 — Integração com Evolution API v2

O backend recebe mensagens, mantém os contatos por JID, associa cada mensagem a um chamado e permite respostas pelo técnico autenticado. A classificação por IA continua para uma próxima etapa.

## Configuração

Preencha localmente as variáveis do `.env.example`:

| Variável | Uso |
| --- | --- |
| `EVOLUTION_ENABLED` | `true` ativa a integração; padrão `false`. |
| `EVOLUTION_API_URL` | URL base da Evolution, incluindo eventual prefixo do proxy; padrão `http://localhost:8081`. |
| `EVOLUTION_INSTANCE` | Nome da instância (letras, números, `_` e `-`; até 80 caracteres). |
| `EVOLUTION_API_KEY` | Chave enviada pelo backend no header `apikey`. |
| `EVOLUTION_WEBHOOK_TOKEN` | Segredo independente, com pelo menos 32 caracteres, para o header `X-Webhook-Token`. |
| `EVOLUTION_CONNECT_TIMEOUT` / `EVOLUTION_READ_TIMEOUT` | Limites de conexão/leitura; padrões `3s` e `8s`. |
| `EVOLUTION_WORKER_DELAY_MS` | Intervalo entre lotes de entrada; padrão `1000` ms. |
| `EVOLUTION_WORKER_ENABLED` | Padrão `true`; `false` mantém a recepção durável com processamento pausado. |

URL, instância e segredos são validados ao iniciar com a integração habilitada. Nenhuma chave da Evolution deve ser configurada no frontend ou exportada para a coleção. No ambiente Docker gerenciado, o backend cria a instância e configura o webhook automaticamente. O celular é vinculado pela tela `/whatsapp.html`, com acesso administrativo. Veja [inicialização Docker](docker.md). Fora desse modo, configure uma instância existente.

Na configuração de webhook **da instância**, indique:

```json
{
  "webhook": {
    "enabled": true,
    "url": "https://SEU-BACKEND/api/webhooks/evolution",
    "byEvents": false,
    "base64": false,
    "headers": { "X-Webhook-Token": "SEU-SEGREDO-LOCAL-COM-32-OU-MAIS-CARACTERES" },
    "events": ["MESSAGES_UPSERT"]
  }
}
```

Use o Manager ou `POST /webhook/set/{instance}` com o formato suportado pela versão instalada. Há versões que expõem esses campos sem o envelope `webhook` e com nomes `webhookByEvents`/`webhookBase64`. Confirme que `headers` foi preservado em `GET /webhook/find/{instance}` e que uma entrega real inclui `X-Webhook-Token`. Se a versão não suportar headers customizados, configure um proxy autenticado entre a Evolution e o backend. A autenticação não é desativada para acomodar versões antigas. `byEvents` deve ficar desabilitado para usar a rota única desta implementação.

O endereço precisa ser acessível **a partir da Evolution**. Em contêineres, `localhost` aponta para o próprio contêiner. Use HTTPS quando os serviços estiverem em máquinas diferentes. Configure no proxy um limite de corpo apropriado (por exemplo, 256 KiB), sem base64 de anexos.

## Contrato HTTP

| Método/rota | Autenticação | Resposta |
| --- | --- | --- |
| `POST /api/webhooks/evolution` | `X-Webhook-Token` e instância configurada | `202` com eventos novos persistidos; `200` para duplicados/ignorados. |
| `POST /api/whatsapp/conectar` | Bearer de administrador | `200` com `instancia`, `estado` e `qrCode` PNG em data URL ou nulo; sem chaves do provedor. `Cache-Control: no-store`. |
| `GET /api/whatsapp/status` | Bearer do painel | `200` com `{"instancia":"suporte","estado":"open"}`. Estados: `open`, `close`, `connecting`. |
| `POST /api/chamados/{id}/responder` | Bearer e permissão de edição do chamado | `201`, `Location: /api/mensagens/{id}` e o DTO de mensagem, incluindo autoria. |

Webhook fictício:

```json
{
  "event": "messages.upsert",
  "instance": "suporte",
  "data": {
    "key": {
      "id": "EXEMPLO-UNICO-001",
      "remoteJid": "5511999990000@s.whatsapp.net",
      "remoteJidAlt": "123456789012345@lid",
      "fromMe": false
    },
    "pushName": "Cliente fictício",
    "message": { "conversation": "Meu computador não liga." },
    "messageTimestamp": "1793358000"
  }
}
```

`data` também pode ser uma lista de 1 a 100 mensagens. O lote inteiro é validado antes das inserções; se houver falha de banco durante as inserções, uma reentrega pode ser feita com segurança. Eventos `messages.upsert` e `MESSAGES_UPSERT` são aceitos. Outros eventos, grupos, broadcasts/status, newsletters, reações e mensagens de protocolo são ignorados. Falta de chave, ID, JID ou `fromMe`, texto vazio, conteúdo acima de 10.000 caracteres e lote inválido retornam `400`. JSON malformado retorna `400`; tipo de conteúdo diferente de JSON retorna `415`. Origem sem segredo correto retorna `401`; instância divergente retorna `403`.

O recebimento responde `{"aceitas":1,"duplicadas":0,"ignoradas":0}` depois do commit na inbox. O chamado e o histórico são atualizados pelo worker, portanto a consulta subsequente pode exigir espera. Nunca se confirma um evento novo apenas colocado em memória. Falhas de banco não recebem confirmação de sucesso.

Resposta do técnico:

```json
{ "texto": "Vamos verificar a alimentação do equipamento." }
```

O texto é obrigatório, aceita até 10.000 caracteres e preserva espaços/quebras de linha. O destinatário é obtido do chamado; campos adicionais não substituem esse destinatário. Chamado ausente retorna `404`, equipe sem permissão retorna `403`, chamado encerrado ou sem destinatário válido retorna `409`. Chamados manuais precisam de telefone numérico com DDI; não há inferência de país nem normalização automática de números legados.

Integração desativada retorna `503`. Falha HTTP, desconexão ou resposta inválida da Evolution retorna `502`; timeout retorna `504`. Corpos e credenciais do provedor não são repassados ao painel. `201` significa que a Evolution aceitou o envio, sem afirmar entrega/leitura pelo WhatsApp. A resposta remota válida precisa conter `key.id` e `key.fromMe=true`.

## Identidade, continuidade e persistência

- `@lid` é uma identidade opaca. O backend armazena o JID completo e deixa `telefoneCliente=null` enquanto não houver um JID telefônico confirmado. Não converte os dígitos do LID em telefone.
- `remoteJidAlt` relaciona o JID telefônico ao LID quando ambos aparecem no evento autenticado. O mapeamento persiste e funciona nas duas direções; o nome do contato nunca é usado para identificar a conversa. Identidades conflitantes permanecem na inbox em falha para revisão, sem mesclar clientes automaticamente.
- O chamado ativo mais recente (`ABERTO`, `EM_ATENDIMENTO`, `AGUARDANDO_USUARIO`) é reutilizado pelo contato ou pelo telefone exato de um chamado manual ainda não vinculado. Havendo múltiplos ativos legados, usa-se o mais recente, sem mesclar históricos.
- Sem chamado ativo, uma mensagem do cliente abre um chamado `WHATSAPP`, equipe `N1`, com resumo/descrição iniciais. Se houver histórico, `chamadoAnteriorId` aponta para o atendimento mais recente. O chamado encerrado permanece preservado.
- Mensagens `fromMe` sem chamado ativo são ignoradas e nunca abrem atendimento. As demais são registradas como `TECNICO`, sem inventar autoria do painel. Respostas pelo endpoint usam o ID do técnico autenticado e geram auditoria `RESPOSTA_WHATSAPP`.
- Texto simples, texto estendido e wrappers efêmeros/view-once são tratados. Para imagem, áudio, vídeo, documento e sticker, armazena-se o tipo e a legenda ou um marcador. Não são baixados arquivos nem persistidos URLs privados, chaves de mídia ou base64; transcrição/análise de anexos fica para outra etapa.
- Timestamps Unix em segundos são convertidos para o fuso local do servidor, seguindo o contrato existente de `LocalDateTime`. Configure o mesmo fuso em todas as réplicas. Na ausência do timestamp, usa-se o horário de recebimento.

A migração Flyway V5 adiciona contatos, aliases e inbox, permite telefone nulo e preserva os registros existentes. A criação manual continua exigindo telefone. A aplicação continua com Hibernate `validate`.

A deduplicação é persistente e usa `evolution:<instancia>:<key.id>` (IDs de até 150 caracteres). A unicidade da inbox e de mensagens protege reentregas inclusive após reinício. Um lock de coordenação no banco serializa processamento e envio entre réplicas; o eco recebido durante o envio fica na inbox até o envio ser registrado. Esse desenho atende ao volume inicial: operações de envio mantêm o lock durante a comunicação, limitada por timeout. A recepção HTTP não adquire esse lock.

O worker processa até 20 eventos por ciclo. Mensagem, chamado e confirmação do processamento são gravados na mesma transação. Falhas têm nova tentativa após 30 segundos, até 10 falhas. O payload persistido contém apenas campos normalizados; nunca o corpo bruto com eventual `apikey`. Para acompanhar pendências, use acesso administrativo ao banco:

```sql
SELECT id, estado, tentativas, recebido_em, proxima_tentativa
FROM evolution_webhook_inbox
WHERE estado IN ('PENDENTE', 'FALHA') ORDER BY id;
```

Após corrigir a causa, um operador pode reprogramar os IDs específicos com `estado='PENDENTE', tentativas=0, proxima_tentativa=CURRENT_TIMESTAMP`. Os logs identificam somente o ID da inbox, sem conteúdo ou segredo. Defina retenção da inbox conforme a política de dados do ambiente.

Não há repetição automática de envios. Timeout ou interrupção entre a aceitação na Evolution e o commit local deixa resultado incerto: confira o histórico/eco antes de reenviar. A API upstream não fornece, neste contrato, uma chave de idempotência de envio; não há garantia de envio exatamente uma vez nesse intervalo. O eco posterior pode recuperar a mensagem no chamado ativo.

## Operação com a Evolution real

O ambiente principal usa a Evolution em Docker, sem provedor simulado. Siga [o guia Docker](docker.md) para iniciar os serviços e vincular o celular. A configuração usa `EVOLUTION_MANAGED=true` e `EVOLUTION_WEBHOOK_URL`; fora do Docker esses valores precisam ser definidos para habilitar o endpoint administrativo de conexão. O modo gerenciado exige uma chave global da Evolution com permissão para criar/configurar a instância.

`POST /api/whatsapp/conectar` é exclusivo de administradores: técnico recebe 403, anônimo 401, integração desativada 503 e modo não gerenciado 409. Ele prepara a instância, atualiza o webhook e devolve o QR real quando necessário. Se já estiver conectada, retorna `estado=open` e `qrCode=null`. Durante a geração, o QR pode ser nulo; consulte novamente após alguns segundos. Erros remotos continuam 502/504. O navegador recebe somente o estado e QR, nunca hash, api-key ou segredo do webhook.

O provisionamento repete a configuração a cada 30 segundos, preservando a instância e a sessão existentes. O caminho de criação é idempotente por nome e tolera disputa entre réplicas. A tela não assume conexão constante: apresenta desconexão ou indisponibilidade reais e permite novo pareamento.

Verifique sem gerar chamados ou mensagens:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-stack.ps1
node scripts/validate-postman.cjs
cd backend
mvn test
```

Dados sintéticos e respostas controladas continuam restritos à suíte Java e aos exemplos de regressão Postman, em banco de teste. As pastas de mocks/simulações Postman e o antigo script que injetava mensagens foram removidos. Não há carga automática de demonstração no ambiente Docker. Resultados anteriores com simulação são evidência histórica; o estado da implantação atual está documentado em [Docker](docker.md).

O vínculo do celular, entrega/leitura no WhatsApp e recebimento de mensagem real exigem ler o QR Code e testar com o aparelho. Nenhuma mensagem real é enviada automaticamente pelo teste operacional.

Referências: [OpenAPI v2 oficial](https://github.com/evolution-foundation/docs-evolution/blob/main/openapi/openapi-v2.json) e [implementação de webhook da versão Docker](https://github.com/evolution-foundation/evolution-api/blob/2.3.7/src/api/integrations/event/webhook/webhook.controller.ts).
