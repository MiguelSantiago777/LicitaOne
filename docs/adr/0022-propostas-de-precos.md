# 0022 — Propostas de preços (Fase 12)

**Status:** Proposto · 13/11/2026 · origem: pedido do usuário de 12/11/2026 ([plano](../fases/fase-12-plano.md), [decisões](../decisoes.md#propostas-de-preços-12-e-13112026))

## Contexto
A Shema monta propostas de preços fora do sistema. O pedido é uma aba que já gere o número da proposta e tenha:
- os dados do cliente;
- produto ou serviço;
- os itens;
- as condições (validade, pagamento e prazo de entrega);
- o responsável, com cargo e CPF.

O documento sai sem assinatura: quem baixa assina no assinador. Os dados do cliente são sempre digitados, sem vínculo com licitação nem órgão.

## Decisões

### Módulo próprio, sem depender de licitações
- Módulo `proposals`. Ele lê da API pública de `identity` só o cargo e o CPF de quem assina.
- Os dados da empresa para o cabeçalho (`shema.company.*`) passaram de `extraction` para `shared.company`, porque agora servem ao kit de carona e às propostas.

### Número fixo, por ano, sem buraco nem repetição
- O formato é `2026/0001`, recomeçando a cada ano. O número é dado quando a pessoa clica em "Nova proposta", e a proposta já é gravada nesse momento.
- A tabela `proposal_counters` guarda o último número de cada ano. A criação faz um upsert (`insert … on conflict do update … returning`) na mesma transação. A linha do ano fica travada até o commit: duas criações ao mesmo tempo saem com números seguidos. O teste cria 8 em paralelo.
- O número nunca muda (`updatable = false`, único por ano). Editar mantém o número. **Duplicar** cria outra proposta com número novo.
- A proposta não é apagada, só **cancelada**: continua na lista (com o filtro "Mostrar canceladas"), não aceita alteração nem gera PDF. O cancelamento pode ser desfeito. Assim nenhum número some.

### Salva enquanto digita; a conferência fica para o PDF
- A tela manda a proposta inteira (cabeçalho e itens) 800 ms depois da última alteração, com a versão (optimistic locking). Se outra pessoa salvou antes, a resposta é 409 e a tela pede para recarregar.
- Quase tudo pode ficar em branco, inclusive o item pela metade. CNPJ e CPF são conferidos (dígitos verificadores) quando preenchidos. O CNPJ já aceita o formato alfanumérico da Receita, em vigor desde julho de 2026.
- `missing()` lista o que falta para o PDF:
  - cliente e CNPJ;
  - pelo menos um item, todos com descrição, quantidade e valor unitário;
  - validade, forma de pagamento e prazo de entrega;
  - nome, cargo e CPF do responsável.

  O endpoint do PDF responde 422 com essa lista, e a tela mostra o que falta e só habilita "Baixar PDF" quando a proposta está completa e salva.
- O total da proposta fica gravado (`total`) e é recalculado a cada alteração. Assim a lista mostra o valor sem ler os itens. O total de cada linha arredonda para centavos, meio para cima, como nos contratos.
- Endereço, e-mail, telefones, unidade e marca são opcionais. As colunas de unidade e marca só aparecem no PDF se algum item tiver.

### Quem assina
- Os usuários ganharam **cargo** e **CPF** (opcionais), editados em "Usuários" pelo administrador ou pelo próprio usuário, no botão "Usar como meu padrão" da proposta.
- A proposta nova já vem com o nome, o cargo e o CPF de quem criou, e esses campos podem ser trocados na proposta.
- O CPF é dado pessoal. Ele fica na proposta e no cadastro e não vai para log.

### PDF sem assinatura, com OpenPDF
- O PDF é A4, gerado no backend com **OpenPDF** (LGPL/MPL; o `poi-ooxml` só gera .docx). PDF porque é o formato que o assinador gov.br e os de certificado ICP-Brasil aceitam. O arquivo leva o número no nome: `Proposta-2026-0001.pdf`.
- Conteúdo:
  - cabeçalho da empresa;
  - título com o número, e "Fornecimento de produtos" ou "Prestação de serviços" logo abaixo;
  - cliente;
  - itens e total, com o total também por extenso;
  - condições;
  - cidade e data por extenso;
  - **espaço em branco e linha** para a assinatura, com nome, cargo, CPF e razão social logo abaixo.
- O bloco da assinatura não se divide entre páginas, e cada página tem no rodapé o número da proposta e o da página.
- Se faltarem dados da empresa no ambiente, o cabeçalho mostra o campo entre colchetes, como no kit de carona, e a tela avisa.

### Permissões
- **Administrador e Comercial** criam e alteram **qualquer** proposta: a equipe é pequena, e uma pessoa termina a proposta da outra. **Leitura** vê e baixa o PDF.

## Consequências
- Não há envio por e-mail nem assinatura dentro do sistema. Quem baixa assina fora e manda.
- Sem timbrado por enquanto: o cabeçalho é texto. Se vier um logo, entra no `OpenPdfProposalDocuments`.
- A numeração não reaproveita números. A proposta aberta e abandonada fica na lista como "Em preenchimento", e pode ser cancelada.
