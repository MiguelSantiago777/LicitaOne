# 0020 — "Pergunte ao LicitaOne": assistente de perguntas sobre a carteira (Fase 11)

**Status:** Aceito · 04/11/2026 · origem: [proposta 0001](../propostas/0001-ia-no-licitaone.md), itens da fronteira · [plano](../fases/fase-11-plano.md)

## Contexto
A equipe quer perguntar em português coisas como "quais prefeituras têm mais de R$ 100 mil de saldo e não pedem há 60 dias?" e receber a lista para abrir. Responder isso exige cruzar licitações, contratos, pedidos, atas, tarefas e funil. Uma resposta errada (número inventado, item que não existe) é pior que nenhuma.

## Decisões

### Consultas fixas, não SQL
O modelo escolhe entre consultas de leitura com filtros tipados, executadas pelo backend **como o usuário logado**, pelas APIs públicas dos módulos:
- `buscar_orgaos`, `buscar_pessoas`;
- `buscar_licitacoes`;
- `contratos_abertos` (saldo mínimo, dias sem pedido, vigência, órgão);
- `detalhe_contrato`;
- `buscar_atas`, `municipios_sem_ata`;
- `buscar_tarefas`, `buscar_negociacoes`;
- `contatos_recentes`.

Cada consulta devolve no máximo 50 linhas e avisa quando corta. O modelo nunca escreve SQL nem chama endpoints. Parâmetro inválido (id que não é id, valor com "R$", data fora do formato) volta como erro para o modelo corrigir, e consulta desconhecida também.

Para isso, os módulos ganharam consultas públicas:
- `TenderSearch`, `AgencyDirectory.search`, `ContractOrderContext.allOpen`;
- `PriceRecordSearch`, `TaskSearch`, `DealSearch`;
- `UserDirectory.searchActive`.

O novo módulo `assistant` depende só delas.

### A lista clicável sai do banco, não do modelo
O modelo termina chamando `responder` com o texto e os **ids** dos itens. O servidor guarda, nesta pergunta, cada item que as consultas devolveram, com rótulo, dado-chave e rota da tela. Só esses viram link, com o rótulo do banco. Id que não veio das consultas fica fora da lista, com aviso na resposta. O texto é do modelo, mas o prompt manda usar só números das consultas e dizer "não encontrei" quando a consulta volta vazia. O botão "Ver as consultas feitas" mostra cada consulta, com os filtros em texto e quantas linhas voltaram, para a pessoa conferir.

### Em segundo plano, com a tela consultando o andamento
- A pergunta roda numa thread do backend, com o contexto de segurança de quem perguntou.
- A tela consulta `GET /assistant/questions/{id}` a cada segundo: vê os passos ("Consultando contratos e saldos…") e, no fim, a resposta.
- **Mudou em relação ao plano, que previa SSE.** A consulta a cada segundo não depende do proxy repassar a conexão aberta, não esbarra no tempo-limite de 60 s do nginx e passa pelo mesmo cliente HTTP do resto, com login e renovação de sessão. O custo é uma chamada por segundo enquanto a pergunta roda, o que é desprezível.
- Até 6 rodadas de consulta por pergunta. Depois disso, o modelo é instruído a responder com o que tem, e a resposta avisa.
- Até 2 perguntas ao mesmo tempo por pessoa (duas abas).

### Nada da conversa é guardado (decisão de 04/11/2026)
- A pergunta, os passos e a resposta ficam **só na memória** do backend, por 10 minutos, e só quem perguntou consulta.
- A conversa de seguimento é a tela que manda: as últimas 10 perguntas e respostas, em texto. Os links continuam vindo só das consultas da pergunta atual.
- Fechar o painel apaga a conversa.
- O banco guarda só a **contagem diária por pessoa** (`assistant_usage`, V19), para o limite de **50 perguntas por dia** (`SHEMA_ASSISTANT_DAILY_LIMIT`).
- Os logs não têm o texto. A pergunta recusada pelo limite não conta.

### Quem usa
Todos os perfis, Leitura incluído (decisão de 04/11/2026). O assistente só lê, e as consultas seguem as mesmas permissões das telas.

### Modelo
- `claude-opus-5-5` com raciocínio adaptativo e esforço médio, pelo SDK oficial. O laço de ferramentas é feito aqui, não pelo executor do SDK, para controlar o que roda e quais ids viram link. A mensagem do modelo volta inteira na rodada seguinte, com o raciocínio.
- Usa a mesma `ANTHROPIC_API_KEY` da leitura: sem a chave, o botão "Perguntar" some.
- Para o E2E há um modo de demonstração (`SHEMA_EXTRACTION_DEMO`), proibido no perfil `prod`. Ele escolhe a consulta por palavra-chave e responde com o total e os 5 primeiros itens. Passa pelo mesmo laço, pelas mesmas consultas e pela mesma conferência de ids.
- Prompt: data de hoje e dia da semana, nome de quem pergunta. Os dados das consultas são tratados como dados, não como instruções. Pedido para alterar algo recebe a indicação da tela. Pergunta fora da carteira recebe o que o assistente consegue responder.

### Métricas
- `shema.assistant.questions` (etiqueta `outcome`: `DONE` ou `FAILED`);
- `shema.assistant.tool.calls` (etiqueta `tool`);
- `shema.assistant.tokens` (etiqueta `type`: `input` ou `output`).

## Consequências
- A qualidade do texto só pode ser medida com a chave e perguntas reais. Como nada é guardado, a medição precisa de um roteiro de perguntas de teste.
- Com mais de uma instância do backend, a pergunta teria que voltar à mesma instância. Hoje há uma só. Se isso mudar, o andamento vai para o Redis ou para o banco.
- Um dado novo na pergunta (outra tela, outro filtro) exige uma consulta nova no código, não só no prompt. É o preço de não deixar o modelo montar consultas.
