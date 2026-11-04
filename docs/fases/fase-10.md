# Fase 10 — Kit de carona sob medida

**Branch:** `fase-10` (a partir de `fase-9`) · **Data:** 04/11/2026 · **Plano:** [fase-10-plano.md](fase-10-plano.md) · **Decisão:** [ADR 0019](../adr/0019-kit-de-carona.md)

## O que ficou pronto

### Kit de carona
- **"Kit de carona"** na ata (aba Documentos da licitação) e no cartão da negociação em andamento. A negociação precisa ter órgão e licitação; ela abre o kit com o município já escolhido.
- A pessoa escolhe o município e vê, **por item, quanto ele ainda pode pedir**: até 50% do registrado por município, sem passar dos 200% da ata, descontando o que ele já aderiu. As quantidades vêm preenchidas com o máximo e podem ser trocadas.
- **Avisos antes de gerar:**
  - vigência curta (menos de 30 dias);
  - município que já aderiu;
  - limite de órgãos atingido;
  - itens sem saldo;
  - gerenciador municipal (conferir a lei local);
  - dados da empresa faltando.
- **Recusa:** ata vencida, ata sem adesão e o próprio gerenciador como candidato.
- **"Gerar com IA"** ou **"Gerar sem IA"**. O kit tem três abas, cada uma com o texto editável:
  1. **Carta de apresentação**, com assunto e destinatários (e-mails dos contatos do município). É escrita pela IA ou pelo modelo fixo.
  2. **Modelo de ofício de solicitação**, para o município mandar ao gerenciador. A base legal é fixa, a justificativa vem da IA, e os campos do município ficam entre colchetes.
  3. **Ofício de anuência**, sempre o modelo fixo, com os dados da empresa e as quantidades.
- Em cada aba: **Copiar**, **Baixar .docx**, **Salvar nos documentos** (para quem pode alterar a licitação) e, na carta, **Abrir no e-mail**. O sistema não envia nada.

### Limite de 50% por município
- Novo limite configurável `shema.caronas.max-per-agency-percent` (padrão 50).
- Ao registrar uma carona, passar dele avisa e pede confirmação, como os outros limites. O aviso do diálogo de carona agora cita os três limites.

### No backend
- `CaronaOffers` (API pública de `pricerecords`): quanto oferecer e os avisos. `PriceRecord.allowances` e `valueAllowance` fazem a conta no domínio.
- `CaronaLetters` (domínio de `extraction`):
  - modelos fixos;
  - montagem do kit;
  - aviso quando a IA cita lei ou artigo;
  - corte em 6.000 caracteres;
  - campos da empresa em branco entre colchetes.
- `CaronaKitAssistant` e os endpoints `GET /price-records/{id}/carona-offer`, `POST /price-records/{id}/carona-kit` e `POST /price-records/{id}/carona-kit/docx`. Nenhum grava nada.
- O .docx sai pelo Apache POI (`PoiLetterDocuments`): A4, Calibri 11 e cabeçalho da empresa, menos na solicitação, que é do município.
- Dados da empresa em `SHEMA_COMPANY_*`, com exemplos no `.env.example` e repassados pelo compose.
- `AgencyDirectory.isMunicipal`, para o aviso do gerenciador municipal.
- Leitor de demonstração: carta e justificativa fixas.

## Testes
| Suíte | Resultado |
|---|---|
| Backend `./mvnw verify` | 176 testes, verde. Novos: `PriceRecordTest` (50% por órgão, quanto cada um ainda pode pedir, ata sem itens), `CaronaLettersTest` (kit sem IA, com IA, aviso de lei citada, dados da empresa em branco, ata sem itens), `ClaudeDocumentReaderTest` (prompt e esquema do kit) e `CaronaKitFlowTests` (máximo por município descontando o que já aderiu, avisos, kit sem e com IA, .docx, recusa do gerenciador, 403 para Leitura). O `PriceRecordFlowTests` roda com o limite por órgão em 200%, para continuar conferindo o limite total |
| Frontend lint, formatação, testes, build | Verde (22 testes; novo `carona-kit-dialog.spec`, nome do arquivo no Content-Disposition) |
| E2E (1440 e 390), compose com o leitor de demonstração | 50 testes, verde. Novo: `carona-kit.spec` (município, quanto pode pedir, kit sem IA com os três textos, .docx da anuência, kit com IA, axe). O `atas.spec` foi ajustado ao limite de 50%: a primeira carona agora é de 50 kits, e a segunda passa do limite por órgão |

## Checklist Web Design Guidelines (telas novas)
- [x] **Estados:**
  - IA desligada (só "Gerar sem IA");
  - conferindo a ata;
  - gerando (status anunciado e botão `aria-busy`);
  - avisos;
  - recusa (mensagem);
  - município sem e-mail (explica onde cadastrar).
- [x] **Acessibilidade:**
  - abas com `tablist`, `tab` e `tabpanel` ligados por id;
  - campos com rótulo;
  - quantidades com o item e o máximo no rótulo;
  - axe sem violações no kit gerado, em 1440 e 390.
- [x] **Celular:** as linhas de item quebram, e as abas e os botões quebram linha.
- [x] **Texto:** a tela diz que o LicitaOne não envia nada, e cada aba diz de quem é o texto: da Shema, ou do município, para ele adaptar.

## Diferenças em relação ao plano
- **O botão da ata se chama "Kit de carona"**, não "Oferecer carona": a página de atas já tinha um "Oferecer carona", que abre o funil.
- **Uma chamada à IA, não duas:** a carta e a justificativa saem juntas e ficam coerentes entre si.
- **O kit também funciona sem IA**, com os modelos fixos. Assim ele serve mesmo sem a chave da API.
- **Nota na negociação e sugestão de mudar de etapa: ficaram fora.** A negociação não tem histórico próprio, e o campo de observações tem 2.000 caracteres. O registro fica nos documentos salvos na licitação e no histórico da ata ("documento enviado").
- **"Já tem ata própria para o mesmo objeto" ficou fora:** não há como comparar objetos com segurança. O aviso de "já aderiu" e a lista de municípios sem ata cobrem a maior parte.
- **A IA não recebe as observações da negociação:** o módulo `extraction` não depende de `deals`, e a pessoa revisa a carta antes de mandar.

## Pendências
- **O jurídico do cliente revisar** os modelos fixos do ofício de solicitação e da anuência.
- **Trocar os dados de exemplo** da empresa (`SHEMA_COMPANY_*`) no deploy.
- Próximos itens da [proposta 0001](../propostas/0001-ia-no-licitaone.md): os da fronteira ("Pergunte ao LicitaOne", resumo do dia).
