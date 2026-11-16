# Triagem TI — revisão de prioridade

React + TypeScript + Vite. Interface autenticada para abrir chamados, solicitar
recomendação de prioridade e aceitar/corrigir com justificativa e histórico.

## Executar

Com Node 24+ e o backend em execução:

```powershell
npm.cmd ci
npm.cmd run dev
```

Abra <http://localhost:5173>. O proxy `/api` aponta para
`http://127.0.0.1:8080`. Para outra porta, copie `.env.example` para `.env.local`,
altere `DEV_API_TARGET` e reinicie o Vite. Nenhuma senha deve ser colocada em
variáveis `VITE_*`.

Login usa a conta de técnico já cadastrada no backend. Na instalação Docker,
o administrador inicial usa as credenciais do `.env.docker` privado da raiz.
Bearer fica somente em memória; recarregar exige novo login. Logout tenta
revogar a sessão e informa quando só foi possível encerrar localmente.

## Dashboard de chamados

Após o login, os cartões mostram o total de chamados, ativos, em atendimento
e já atendidos (status `ENCERRADO`). Ativos incluem `ABERTO`, `EM_ATENDIMENTO`
e `AGUARDANDO_USUARIO`; portanto, em atendimento é um subconjunto dos ativos.
O panorama exibe o percentual de encerrados sobre o total de registros.

Clique em um cartão para filtrar a fila paginada. Os indicadores usam os totais
do backend para todo o histórico, independentemente da página selecionada.
**Atualizar fila** recarrega a lista e os indicadores; criar chamados e salvar
alterações no painel também atualiza os dados. As consultas usam os endpoints
existentes e a sessão autenticada.

## Prioridade assistida

Selecione um chamado, informe impacto/urgência ou deixe o modelo estimar e
clique em **Analisar prioridade**. Sem modelo disponível, informe ambas as
dimensões para usar a matriz. Cargo isolado não eleva prioridade. Criticidade
alta confirmada aplica piso ALTA, com regra e versão registradas.

O resultado mostra prioridade recomendada, origem, confiança, versões e regras.
Modelos sintéticos têm aviso explícito. **Aceitar recomendação** aplica a
sugestão; **Salvar correção** exige prioridade e justificativa. O histórico
preserva a sugestão original e autoria/data da decisão. Nova análise não altera
automaticamente a prioridade final.

O servidor verifica equipe, responsável, encerramento e concorrência. Em 409,
atualize e solicite nova análise. Falhas da IA ficam no histórico; o atendimento
manual continua disponível em **Definir prioridade sem IA**, usando o endpoint
existente de prioridade e sua auditoria. Escritas não são repetidas automaticamente.

Esta entrega cobre o painel de prioridade; atendimento por conversa e outras
telas do mapa de funcionalidades continuam como funcionalidades separadas.

Aceites, correções e mudanças manuais de prioridade alimentam o
[aprendizado contínuo](../ia/APRENDIZADO_CONTINUO.md). O histórico informa se
o feedback está pendente ou recebido pela IA; o treinamento é em lotes e a
ativação depende da validação. Quando a prioridade usa decisões aprendidas,
o painel mostra esse método, a confiança e o resultado separado da matriz.

## Validar

```powershell
npm.cmd run build
npm.cmd run lint
```

`dist/` contém o bundle. Uma publicação requer fallback de SPA e proxy de API;
o servidor Vite é usado aqui somente para desenvolvimento local.
Confira o [guia completo](../ia/README.md) e as [evidências de validação](../ia/VALIDACAO.md).
