# Contrato da dependência Evolution API

`evolution-v2.json` documenta os endpoints de envio/status, extraídos em 30/10/2026 do [OpenAPI v2 oficial](https://github.com/evolution-foundation/docs-evolution/blob/main/openapi/openapi-v2.json). Mantém os métodos, parâmetros, autenticação `apikey`, corpos e exemplos publicados. O envio usa os IDs retornados em `key.id`, conforme o schema de resposta.

Este contrato pertence ao provedor. A coleção que testa os endpoints deste backend permanece em `docs/postman/triagem-chamados.postman_collection.json`. O gerenciamento usa adicionalmente `GET /instance/fetchInstances?instanceName=...`, `POST /instance/create`, `POST /webhook/set/{instance}` e `GET /instance/connect/{instance}`, conferidos no código oficial da versão 2.3.7. O webhook usa o envelope `webhook`, com `url`, `enabled`, `headers`, `byEvents`, `base64` e `events`. Não há mais handler de simulação no repositório.
