# Fase 11 — Plano de trabalho ("Pergunte ao LicitaOne")

**Branch:** `fase-11` (a partir de `fase-10`) · **Plano de:** 04/11/2026 · **Origem:** [proposta 0001](../propostas/0001-ia-no-licitaone.md), itens da fronteira · **Base técnica:** ADRs [0016](../adr/0016-leitura-com-ia.md) e [0018](../adr/0018-follow-up-com-ia.md)

**Status:** validado e concluído em 04/11/2026 ([relatório](fase-11.md), [ADR 0020](../adr/0020-assistente-de-perguntas.md)).

## Decisões de 04/11/2026
O usuário pediu para seguir as recomendações:
1. **Conversas não são guardadas:** somem ao fechar o painel; o servidor registra só métricas, sem o texto.
2. **Perfil Leitura usa o assistente.**
3. **Limite diário:** 50 perguntas por pessoa, configurável.
4. **Exemplos:** as perguntas deste plano.

## O que é
Um assistente de perguntas em português sobre a carteira, aberto por um botão fixo no topo de qualquer tela. A resposta vem em texto curto e com uma **lista clicável** de licitações, contratos, atas, órgãos, tarefas ou negociações. Exemplos:
- "Quais prefeituras têm mais de R$ 100 mil de saldo e não pedem há 60 dias?"
- "Que atas vencem até março e ainda aceitam carona?"
- "O que está atrasado do Edwin?"
- "Quanto a Secretaria de Fazenda já pediu do contrato 12/2026?"
- "Quais municípios do RJ ainda não têm ata nossa?"

**Só lê.** O assistente não cria, altera nem apaga nada. Para agir, a pessoa clica no item da lista e usa a tela de sempre.

## Como funciona (técnico)
- **Ferramentas de consulta, não SQL.** O modelo recebe um conjunto fixo de ferramentas de leitura, cada uma com filtros tipados. O backend executa cada ferramenta **como o usuário logado**, pelas APIs públicas dos módulos, com as mesmas permissões das telas. O modelo nunca escreve SQL nem chama endpoints por conta própria.
- **Ferramentas previstas** (cada uma devolve no máximo 50 linhas, já resumidas):
  1. `buscar_licitacoes`: situação, órgão, UF, responsável, período da vitória, valor mínimo.
  2. `contratos_com_saldo`: saldo mínimo, dias sem pedido, vigência até, órgão.
  3. `detalhe_contrato`: itens, saldo por item, pedidos e último pedido.
  4. `buscar_atas`: vigentes, vencendo até uma data, que aceitam carona, quanto já foi usado.
  5. `municipios_sem_ata`: UF.
  6. `buscar_tarefas`: responsável, atrasadas, vencendo no período, situação.
  7. `buscar_negociacoes`: etapa, situação, responsável, órgão.
  8. `contatos_recentes`: os últimos contatos registrados com um órgão (Fase 9).
  9. `buscar_orgaos` e `buscar_usuarios`: para o modelo achar o órgão ou a pessoa pelo nome ("Edwin", "Magé").
- **Lista clicável montada pelo backend.** O modelo só aponta os ids que vieram das ferramentas. O servidor confere cada id contra o que as ferramentas devolveram nesta pergunta e monta o link. Assim, um id inventado não vira link.
- **Números saem das ferramentas.** O prompt manda citar só valores devolvidos por elas e dizer "não encontrei" quando a consulta volta vazia. As datas relativas ("há 60 dias", "até março") são calculadas a partir da data de hoje, que vai no contexto.
- **Até 6 rodadas de ferramenta por pergunta.** Passou disso, o assistente responde com o que tem e avisa.
- **Resposta em tempo real (SSE).** A tela mostra o passo atual ("consultando contratos…") e depois a resposta. Uma pergunta com várias consultas pode levar 10 a 30 s e passaria do tempo-limite de 60 s do proxy numa chamada só. O nginx ganha uma rota sem buffer para isso.
- **Conversa curta.** A pessoa pode fazer perguntas de seguimento ("e desses, quais são do RJ?"). A conversa vive enquanto o painel está aberto, com até 10 perguntas; depois disso, recomeça.
- **Novo módulo `assistant`**, que depende só das APIs públicas dos outros módulos. Onde ainda não há consulta pública, ela é criada no módulo dono do dado, como `ContractOrderContext` na Fase 9.
- **Mesmo cliente do Claude** (`claude-opus-5-5`, esforço médio), com o leitor de demonstração para o E2E: respostas fixas para três perguntas de exemplo.
- **Limite de uso:** perguntas por pessoa por dia, configurável (`shema.assistant.daily-limit`).
- **Métricas:** `shema.assistant.*`, com perguntas, rodadas de ferramenta, tokens e falhas.

## Tela
- Botão **"Perguntar"** (ícone de IA) no topo, ao lado do sino. Abre um painel lateral; no celular, ocupa a tela.
- O painel tem:
  - campo de pergunta;
  - sugestões de exemplo quando está vazio;
  - histórico da conversa aberta;
  - o passo atual enquanto pensa;
  - a resposta com a lista clicável (tipo, nome, um dado-chave como saldo ou vencimento) e o botão **"Ver as consultas feitas"**, que mostra cada ferramenta e os filtros usados, para a pessoa conferir de onde veio a resposta.
- Sem IA configurada, o botão não aparece.

## Etapas
1. ✅ Consultas públicas que faltam nos módulos (contratos com saldo, atas, tarefas, negociações, busca de órgão e de usuário), com testes.
2. ✅ Módulo `assistant`: ferramentas, laço com o modelo, conferência dos ids, limite diário e métricas.
3. ✅ Endpoints (com consulta do andamento a cada segundo, no lugar do SSE; ver o relatório) e modo de demonstração.
4. ✅ Painel no front: conversa, passos, lista clicável e "ver as consultas".
5. ✅ E2E (1440 e 390) com o leitor de demonstração, axe, ADR 0020 e relatório `docs/fases/fase-11.md`.

## Fora desta fase
- O assistente fazer coisas (criar tarefa, registrar contato). Pode vir depois, sempre com confirmação na tela.
- Perguntas sobre o conteúdo dos PDFs, como o texto do edital ou da ata.
- Ajuda sobre como usar o sistema.
- O **resumo do dia**, que fica para a próxima fase e pode reaproveitar as mesmas ferramentas.

## Dúvidas
1. **Guardar as conversas?**
   - (a) **Não guardar** (recomendo): somem ao fechar o painel; o servidor registra só a métrica, sem o texto.
   - (b) Guardar por 30 dias para cada pessoa, para ela rever e para medirmos a qualidade das respostas.
2. **Perfil Leitura** pode usar o assistente? Ele já vê tudo nas telas; a diferença é o custo. Recomendo que sim.
3. **Limite diário:** 50 perguntas por pessoa serve para começar?
4. **Alguma pergunta que vocês fazem toda semana** e que precisa estar entre os exemplos? Sem real ainda, uso as de cima.
