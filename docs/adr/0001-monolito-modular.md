# 0001 — Monólito modular com Spring Modulith

**Status:** Aceito · 27/10/2026

## Contexto
A especificação pede um único deployable dividido por contexto de negócio, com limites verificados por teste. A equipe é pequena e o volume (dezenas de contratos, alguns usuários) não justifica microsserviços.

## Decisão
- Um app Spring Boot com um módulo por pacote de primeiro nível em `br.com.shema.crm`. O código é em inglês; o mapa para o domínio é:

  | Módulo (código) | Contexto | Fase |
  |---|---|---|
  | `identity` | Identidade: usuários, perfis, login | 0 |
  | `tenders` | Licitações e ciclo de vida | 1 |
  | `contracts` | Contratos, itens, pedidos, saldo | 2 |
  | `imports` | Importação de planilhas | 2 |
  | `pricerecords` | Atas, caronas e documentos | 3 |
  | `deals` | Negociações e quadros kanban | 4 |
  | `tasks` | Tarefas | 4 |
  | `notifications` | Alertas, e-mails, rascunhos | 5 |
  | `shared` | Erros da API, correlationId, auditoria (módulo aberto) | 0 |

- **Spring Modulith** (`ApplicationModules.verify()` em `ModularityTests`) quebra o build se um módulo acessar subpacotes internos de outro ou se houver ciclo. A API pública de um módulo é o pacote raiz dele. Outros módulos não podem acessar `domain`, `application` nem `infrastructure` de um módulo.
- Dentro de cada módulo: `domain`, `application`, `infrastructure`, `api`. As entidades JPA ficam em `domain` (DDD pragmático: a regra mora na entidade, testada sem Spring). Não criamos port/adapter para CRUD simples.
- DDD completo (agregados, value objects, máquina de estados explícita) entra onde há regra de verdade: ciclo de vida da licitação (Fase 1) e saldo de contrato (Fase 2). Cada caso ganha um ADR próprio.

## Consequências
- Um deploy, uma transação e um banco: simples de operar.
- A fronteira dos módulos é verificada pelo build, não por disciplina.
- Extrair um módulo para um serviço próprio no futuro é viável, porque os módulos já conversam só por API pública e por eventos (ADR 0003).
- O starter `spring-modulith-starter-insight` foi retirado: ele envolve os beans em proxies CGLIB e quebrou os filtros servlet (`GenericFilterBean.init` é final). O tracing entra com OpenTelemetry.
