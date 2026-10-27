# Manutenção da API e da coleção Postman

- A cada implementação que crie ou altere endpoints, atualizar também `docs/postman/triagem-chamados.postman_collection.json` no mesmo incremento.
- Manter exemplos de corpo, parâmetros, variáveis e testes alinhados ao contrato real. Incluir fluxos de sucesso e erros relevantes, com verificações dos dados retornados e preservados.
- Preservar a execução sequencial da coleção: gerar dados fictícios únicos, capturar IDs automaticamente e evitar IDs fixos de registros existentes.
- Atualizar `docs/postman/README.md` e o ambiente de exemplo quando necessário. Não versionar credenciais, IDs de execuções locais ou respostas com dados reais.
- Validar JSON e scripts; quando possível, executar a coleção completa pelo Postman Runner ou Newman contra um backend de teste e registrar resultado e limitações.
