# Decisões e registro de ferramentas

## Skills instaladas (Passo 0) — 26/10/2026

Ambiente: Windows 11, Node.js 24.22.1, npm 11.19.1, git 2.56. Todos os comandos foram rodados na raiz do repositório (`shemacrm/`).

| Skill / recurso | Comando | Resultado | Local |
|---|---|---|---|
| Taste Skills (`design-taste-frontend`) | `npx skills add Leonxlnx/taste-skill --skill design-taste-frontend --agent claude-code -y` | OK. Avaliação: Gen *Safe*, Socket 1 alerta, Snyk *Med Risk* | `.claude/skills/design-taste-frontend` |
| Web Design Guidelines | `npx skills add vercel-labs/agent-skills --skill web-design-guidelines --agent claude-code -y` | OK. Gen *Safe*, Socket 0 alertas, Snyk *Low Risk*. Busca as regras online a cada auditoria. | `.claude/skills/web-design-guidelines` |
| Awesome Design (catálogo DESIGN.md) | `git clone --depth 1 https://github.com/VoltAgent/awesome-design-md .claude/design-refs/awesome-design-md` | OK (169 arquivos .md) | `.claude/design-refs/awesome-design-md` (fora do git, ver abaixo) |
| Playwright CLI | `npm install -g @playwright/cli@latest` | OK, `playwright-cli` no PATH | global npm |
| Skill do Playwright CLI | `npx skills add microsoft/playwright-cli --skill playwright-cli --agent claude-code -y` | OK. Gen *Safe*, Socket 1 alerta, Snyk *Med Risk* | `.claude/skills/playwright-cli` |
| Navegador | `npx playwright install chromium` | OK (Chromium headless shell) | `%LOCALAPPDATA%\ms-playwright` |

Nenhuma alternativa precisou ser usada.

### Revisão de segurança das skills
Como duas skills vieram com alerta "Med Risk", foi feita uma revisão manual antes do uso:
- `design-taste-frontend`: os scripts `.mjs` fazem `fetch` só para baixar imagens/vídeos de referência (`assets.mjs`) e usam `spawn` para abrir um Chromium via CDP para inspecionar páginas (`lib/cdp.mjs`). Não leem variáveis de ambiente nem segredos, e não enviam dados para fora.
- `playwright-cli`: só documentação (SKILL.md + `references/`).
- `web-design-guidelines`: só instruções; baixa as regras de `raw.githubusercontent.com/vercel-labs/web-interface-guidelines`.

### Versionamento
- `.claude/skills/` é commitado, para que todo mundo use a mesma versão das skills.
- `.claude/design-refs/` fica no `.gitignore` (é um clone de terceiro com `.git` próprio). Para recriar, use o comando da tabela.

## Ambiente de build
- JDK 21 (Microsoft OpenJDK 21.0.12) em `%USERPROFILE%\.jdks\ms-21.0.12.1`, **fora do PATH**. O backend usa o Maven Wrapper (`mvnw`), então não é preciso instalar Maven, só definir `JAVA_HOME`.
- Docker 29.9 disponível para o `docker compose`.

## Respostas às dúvidas de negócio — 27/10/2026

As dúvidas da seção 11(c) da [especificação](especificacao.md) foram enviadas ao cliente em 26/10 e respondidas em 27/10. Junto com as respostas veio a planilha `PLANILHA DE CONTROLE DE LICITAÇÕES 2025 - 2026.xlsx`.

| # | Pergunta | Resposta do cliente | Como entra no sistema |
|---|---|---|---|
| 1 | Uma licitação gera um contrato ou vários (empenhos, pedido sem contrato formal)? | "Uma licitação gera uma ata, e mais de um edital." | Licitação 1–1 **Ata** e Licitação 1–N **Contrato**. O contrato tem um tipo (`CONTRATO`, `EMPENHO`, `DISPENSA`) para cobrir pedido por empenho sem contrato formal. ⚠ Confirmar: "edital" aqui parece querer dizer contratos ou empenhos. |
| 2 | O saldo é por valor, por quantidade de cada item ou pelos dois? | "Pelos 2." | O saldo é calculado por **quantidade de cada item** e por **valor**: falta pedir = qtd contratada − qtd pedida, e valor restante = valor do contrato − valor pedido. |
| 3 | Pode mandar a planilha atual? | Enviada. | Mapeamento abaixo. Libera a Fase 2. |
| 4 | Qual a relação entre o funil e as licitações? | "Uma ata pode ser replicada pra vários municípios (em até 4 municípios, mas só pode replicar 200%)." Nome no sistema: **"pegar carona"**. | O funil é usado para **prospectar caronas** de atas vigentes. Uma negociação "Fechado ganho" ligada a uma ata registra uma **Carona** (município que aderiu à ata). Os limites (máx. 4 caronas, total ≤ 200% do quantitativo) ficam **configuráveis** e são sinalizados para validação humana, como manda a seção 11 da especificação. |
| 5 | (extra) | Colunas do kanban livres para os usuários editarem. | Quadros com colunas criadas, renomeadas, reordenadas e excluídas pelo usuário. Nada de colunas fixas no código. |
| 6 | (extra) | "Kanban de organização mensal e um kanban de licitações vencidas." | Modelo genérico de **Quadro** (board), com seed de dois quadros: *Organização mensal* e *Licitações vencidas*, além do *Funil de vendas* (seção 5.5). |
| 7 | (extra) | "Aviso por e-mail (tentar colocar o valor que falta em contrato)." | Os alertas internos (seção 5.4) também saem **por e-mail** para a equipe (SMTP; Mailpit no dev), com o **saldo a pedir** do contrato. O e-mail para o órgão continua só como rascunho, como diz a especificação. Ver ADR 0006. |
| 8 | (extra) | Cadastro de licitação: órgão, município, número do pregão, valor, data, hora, portal, tipo de material. | Novos campos `sessaoEm` (data e hora da sessão) e `portal` (ex.: Compras.gov.br, BLL, Licitanet). Como data, hora e portal são da disputa, a licitação pode ser cadastrada **antes do resultado**. Proposta: estados `EM_DISPUTA` → `VENCIDA` / `PERDIDA` antes do ciclo da seção 5.1 (ADR 0005). ⚠ Confirmar. |

### O que a planilha atual mostra
- A aba GERAL tem uma linha por licitação vencida, com órgão, produto, quantidade, tipo (Contrato ou Dispensa) e valor total.
- O status é marcado pela cor da linha: azul = Encerrado, verde = Em contrato, amarelo = Falta pedir.
- Cada órgão tem uma aba com itens (descrição, qtd, V.U., V.T.), pedidos (qtd, V.U., V.T.), *falta pedir* por item e valor restante (ex.: SEFAZ `K6 = 237000 - J33`). Os itens podem ter **lote** (aba SEGOV Uniformes).
- Várias abas de órgão estão vazias (Macabu, Magé, São João da Barra, Cantagalo, Paraquedista, Mesquita, SEGOV Material Gráfico).
- A importação da Fase 2 vai ter que lidar com cabeçalho na linha 5, título mesclado, colunas de pedido ao lado das de item e totais soltos fora da tabela.

### Para confirmar com o cliente
1. Na resposta 1, "mais de um edital" quer dizer mais de um contrato ou empenho?
2. Existem os estados `EM_DISPUTA` e `PERDIDA`, para cadastrar a licitação antes do resultado?
3. O quadro *Organização mensal* organiza o quê: as licitações do mês (sessões marcadas), as tarefas ou as entregas?
4. O limite de 200% vale por item ou pelo valor total da ata?
5. Quem recebe os e-mails de alerta: o responsável, todos ou uma lista configurável?
6. (Fase 2, 28/10/2026) A aba GERAL da planilha não tem UASG, número do pregão nem data da vitória e da assinatura. Dá para incluir essas colunas na planilha, para importar as licitações de uma vez? Sem elas, cada licitação é cadastrada na tela e só os itens e pedidos (abas de cada órgão) são importados.
7. (Fase 2) Pedido acima do saldo do contrato é recusado. Existe aditivo (por exemplo, até 25% a mais) que precise ser registrado no sistema?

### Respostas assumidas para começar a Fase 1 (27/10/2026)
O usuário autorizou seguir sem esperar o cliente. Assumimos o seguinte; tudo pode mudar quando o cliente confirmar:
1. Uma licitação pode ter vários contratos ou empenhos (entra como entidade na Fase 2).
2. Existem os estados *Em disputa* e *Perdida*: a licitação pode ser cadastrada antes do resultado.
3. O quadro de organização mensal organiza as sessões de licitação do mês (Fase 4).
4. Limite de carona configurável, começando em 200% por item (Fase 3).
5. Alertas por e-mail para o responsável pela licitação (Fase 5).

### Quadros kanban — decisão do usuário (28/10/2026)
- O quadro *Organização mensal* **não tem estrutura fixa**: a equipe cria as colunas e os cards. O sistema entrega a página e o modelo genérico de quadro. Isso responde a dúvida 3.
- Os quadros *Organização mensal* e *Licitações vencidas* entram já, adiantando parte da Fase 4. Os dois usam o mesmo modelo: colunas criadas, renomeadas, reordenadas e excluídas pelo usuário.
- Cada card é livre (título, observações, responsável, prazo) e pode apontar para uma licitação. Nesse caso, mostra órgão, identificação e valor, e a coluna soma esses valores.
- O *Funil de vendas* (Negociações) continua na Fase 4, porque depende das negociações reais.
- A Fase 3 (atas e documentos) fica para depois dos quadros.

### Nome do produto (28/10/2026)
- O sistema passa a se chamar **LicitaOne** (pedido do usuário). Muda tudo o que aparece: telas, títulos das abas do navegador, login, documentação da API e README.
- Os identificadores internos continuam `shema`/`shemacrm`: pacote Java `br.com.shema.crm`, banco, filas e exchanges `shemacrm.*`, nomes dos containers e da pasta. Trocar esses nomes exigiria migrar banco e mensageria, sem ganho visível. Fica para quando houver ambiente de produção, se for desejado.
- "Shema Suprimentos" continua abaixo do logo: é o nome da empresa cliente.

### Envio à IA autorizado (04/11/2026)
- O usuário autorizou enviar à Anthropic atas, contratos, pedidos (PDF e foto) e mensagens coladas, para a leitura com IA (ADRs 0016 e 0017).
- Mensagens coladas podem ter dado pessoal de servidor: o texto fica no banco só até a leitura terminar e nunca vai para os logs.
- Um ofício ou e-mail que pede itens de mais de um contrato é exceção. Segundo a pesquisa registrada no [plano da Fase 8](fases/fase-8-plano.md), empenho e AF saem de uma ata ou contrato só. Cada leitura serve um contrato, e o que não é dele fica de fora, com aviso.
