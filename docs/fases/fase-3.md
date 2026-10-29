# Fase 3 — Atas, caronas e documentos

**Branch:** `fase-3` (a partir de `fase-4`) · **Data:** 29/10/2026 · **Plano:** `docs/fases/fase-3-plano.md` · **Decisões:** ADR 0014

## O que ficou pronto (gravando no banco e no S3)
- **Documentos** na aba *Documentos* do detalhe da licitação:
  - enviar com tipo, título e data;
  - versão nova sem perder a anterior;
  - baixar qualquer versão, renomear e apagar (apagar é lógico: os arquivos ficam).
  - Os arquivos vão para o S3 (SeaweedFS no compose). O tipo é conferido pelo conteúdo, até 25 MB.
- **Ata** na mesma aba:
  - registrar (número, assinatura, vigência, "permite adesão", arquivo da ata, observações);
  - itens registrados, com "copiar do contrato";
  - situação (Vigente, "Vence em N dias", Vencida);
  - quanto as caronas já usaram, no total e por item, com barra de progresso.
- **Caronas**:
  - registrar com órgão, data, quantidade por item (ou valor, se a ata não tem itens) e negociação de origem;
  - o diálogo confere os limites enquanto a pessoa digita. Passou de 4 órgãos, 200% de um item ou da vigência: mostra o motivo e só registra marcando "Registrar mesmo assim";
  - a carona fica marcada e o histórico registra quem confirmou;
  - apagar carona lançada por engano.
- **Destaque de ata vigente**:
  - no cabeçalho da licitação ("Ata válida até …: oportunidade de replicar", com "Ver ata");
  - na aba *Encerrados* da lista de licitações ("Ata válida até 04/11/2028").
- **Página Atas** (saiu dos dados de exemplo):
  - abas Oportunidades, Vigentes, Vencendo, Vencidas e Todas, com contadores, ordenadas pelo vencimento;
  - busca na URL;
  - "Oferecer carona" abre uma negociação já ligada à ata;
  - aba **Municípios sem ata**: prefeituras sem licitação vencida e sem carona, por UF, com "Nova negociação" já com o órgão.
- **Funil**: negociação ganha com licitação de origem mostra "Registrar carona na ata". O link abre a licitação com o diálogo preenchido.
- **Histórico** da licitação junta ata, caronas e documentos aos eventos da licitação e dos contratos.
- **Seed**: 6 atas, cobrindo:
  - vigente com itens e carona (SEFAZ);
  - encerrada com ata até 2028 (SEIJES);
  - vencendo, com a carona de Cantagalo ligada à negociação ganha;
  - sem adesão;
  - vencida.
- **Limpeza**: os dados de exemplo e o selo "dados de exemplo" saíram do frontend; todas as telas usam a API.

## Testes
| Suíte | Resultado |
|---|---|
| Backend `./mvnw verify` | 109 testes, verde. Novos: `FileInspectionTest` (tipo pelo conteúdo, nome limpo), `PriceRecordTest` (valor pelos itens, limite por item e por valor, quinto órgão, ata vencida, mesmo órgão, recusas firmes, item com carona), `S3FileStorageTest` (SeaweedFS real) e `PriceRecordFlowTests` (documento com duas versões e download, ata só para vencida, carona dentro e fora do limite com confirmação, órgão da própria ata recusado, destaques, municípios sem ata, apagar lógico, histórico e permissões). Limites dos módulos verificados |
| Frontend lint / testes / build de produção | verde |
| E2E (1440 e 390) contra o compose com S3 | 30 testes, verde, duas rodadas seguidas |

Fluxos E2E novos (`atas.spec.ts`):
- **Documento, ata e caronas:**
  1. Arquivo falso é recusado; o PDF é enviado e baixado.
  2. A ata é registrada e o destaque leva de volta a ela.
  3. Os itens são cadastrados, com o total ao vivo.
  4. Uma carona dentro do limite é registrada.
  5. Uma carona acima de 200% só passa com confirmação.
  6. O histórico mostra tudo, e o teste limpa no fim.
- **Atas e municípios:** oportunidade da SEIJES até 2028, ata vencendo, municípios sem ata e destaque na lista de encerrados.

Screenshots novos em `e2e/screenshots/{desktop,phone}/`: `04-atas` (agora com dados reais), `29-licitacao-ata`, `30-registrar-carona` e `31-municipios-sem-ata`.

## Bugs encontrados e corrigidos
1. **Esquema OpenAPI misturado**: dois DTOs `ItemRequest` (contratos e atas) viraram um só no OpenAPI, e o de contratos perdeu as validações no cliente gerado. Os de atas foram renomeados. Não sobrou outro nome repetido com formato diferente.
2. **Total dos itens parado**: o total do editor de itens não mudava enquanto se digitava (`computed` sobre objetos alterados pelo `ngModel`).
3. **Exclusão de ata dependia da ordem da cascata** no banco: a linha da carona apontava para o item sem cascata (V13).
4. **Id duplicado no seletor de órgão**: o `id` estático ia para o componente e para o campo. Corrigido também no formulário de licitação da Fase 1, que tinha o mesmo problema, e os rótulos agora apontam para o campo de busca.
5. **Tabela de itens cortada no celular**: escondia justamente o uso pelas caronas. No celular, sai o valor unitário.
6. **Percentual com ponto** ("41.5%") passou a "41,5%".
7. **Testes E2E em paralelo** mexiam na mesma licitação. Agora cada projeto usa a sua, e as caronas de teste usam órgãos estaduais, que não entram em "municípios sem ata".
8. O teste de screenshots passou do tempo limite no celular depois das telas novas e foi dividido em dois.

## Checklist Web Design Guidelines (telas novas)
| Item | Situação |
|---|---|
| Rótulos de formulário | ✅ `label for` em todos os campos; nos editores de linhas, `aria-label` com o número do item; seletor de arquivo com rótulo |
| Foco e teclado | ✅ painéis prendem o foco e fecham com Esc; versões abrem com botão `aria-expanded`; "Versão nova" é um campo de arquivo focável, com contorno de foco |
| Erros anunciados | ✅ erros em `role="alert"`; avisos de limite e valor da carona em `aria-live`; "Negociação criada" em `role="status"` |
| Ação arriscada | ✅ registrar além do limite exige marcar a confirmação (o botão fica desabilitado até lá); exclusões confirmam na própria tela |
| Barras de progresso | ✅ uso da ata com `role="progressbar"` e rótulo ("41,5% do valor registrado usado em caronas"); as barras por item são decorativas, porque o número está ao lado |
| Estados vazio, carregando e erro | ✅ "Esta licitação ainda não tem ata" (com o porquê e a ação), "Nenhum documento", "Nenhum órgão aderiu", "Carregando…", erro com "Tentar de novo" |
| Números e datas pt-BR | ✅ R$ com 2 casas, preço unitário com até 4, quantidades "10.000", percentuais "41,5%", datas dd/MM/yyyy |
| Responsivo (390 px) | ✅ fatos da ata em duas colunas; itens sem a coluna de preço; linhas de carona quebram; sem rolagem horizontal da página |

## Pendências
- **Dúvida 4 do cliente**: o limite de 200% vale por item ou pelo total? Está "por item" (com "por valor" para ata sem itens) e configurável.
- **Antivírus** dos documentos (ClamAV) fica como evolução, como diz a especificação.
- **Alerta de ata vencendo** (90/60/30 dias) é da Fase 5. A vigência e o índice já estão prontos.
- O provedor S3 de produção (e o backup dos arquivos) fica para a Fase 6, junto com o restante da operação.
