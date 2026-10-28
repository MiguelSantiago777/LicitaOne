# Quadros kanban (adiantamento da Fase 4)

**Branch:** `fase-4-quadros` (a partir de `fase-2`) · **Data:** 28/10/2026 · **Decisão:** `docs/decisoes.md`, "Quadros kanban"

## O que ficou pronto
- **Página Quadros** no menu Comercial, com os dois quadros pedidos pelo cliente: *Organização mensal* e *Licitações vencidas*.
  - Os dois começam vazios: a equipe monta as colunas do jeito dela.
  - A equipe também pode criar outros quadros.
- **Colunas livres**: criar, renomear, mover para os lados e excluir. Coluna com card não pode ser excluída.
- **Cards livres**:
  - criação rápida no fim da coluna;
  - arrastar e soltar entre colunas;
  - diálogo com observações, responsável, prazo (atrasado fica em destaque) e licitação ligada (mostra órgão e valor, e a coluna soma os valores);
  - o campo "Coluna" do diálogo move o card sem arrastar, para quem usa teclado ou celular.
- **Permissões**: ADMIN e COMERCIAL alteram; LEITURA só consulta.
- **Concorrência**: a versão do quadro protege as colunas, e a do card protege o conteúdo. Mover card trava o quadro só durante a renumeração.
- **Exclusões pedem confirmação** na própria linha (card, coluna e quadro).

O *Funil de vendas* (Negociações) continua na Fase 4, porque depende das negociações reais.

## Testes
| Suíte | Resultado |
|---|---|
| Backend `./mvnw verify` | 87 testes, verde. Novos: regras do quadro sem Spring e fluxo pela API com Postgres real (colunas, cards, mover, versão, permissão de leitura) |
| Frontend lint / build de produção / testes | verde |
| E2E (1440 e 390) contra o compose | 22 testes, verde. Novo: monta quadro, coluna e card, liga licitação, move (arrastando no desktop, pelo diálogo no celular), checa soma e atraso e limpa no fim. Uma falha isolada na primeira rodada logo depois de reconstruir o container não se repetiu em 4 rodadas completas e 5 isoladas |

## Bugs encontrados e corrigidos
1. **Erro 500 ao abrir quadro com card sem responsável**: `Map.of()` não aceita `get(null)`. A correção foi na origem: os diretórios de usuários e de licitações agora devolvem um mapa vazio que aceita chave nula.
2. **Página alargando no celular**: o texto "(atrasado)" para leitor de tela é `position: absolute` e escapava da rolagem do quadro quando o card estava numa coluna fora da tela. Cards, tabelas e abas agora contêm esse texto.
3. **Rodapé do painel saindo da tela**: com conteúdo longo, o corpo do painel empurrava o rodapé (`min-height: 0` no corpo; o rodapé agora quebra linha). Afeta todos os painéis, não só o de card.
4. **Foco perdido**: depois de mover um card pelo diálogo, o foco ia para o início da página. Agora volta para o card.
5. **Quadro do cliente apagável com um clique**: agora pede confirmação.
