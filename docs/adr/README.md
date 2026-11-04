# Decisões de arquitetura (ADRs)

Cada decisão relevante tem um arquivo `NNNN-titulo.md` com contexto, decisão e consequências. Uma decisão revista ganha um ADR novo que substitui o antigo (o antigo fica com o status "Substituído por NNNN").

| ADR | Título | Status |
|---|---|---|
| [0001](0001-monolito-modular.md) | Monólito modular com Spring Modulith | Aceito |
| [0002](0002-stack-do-backend.md) | Stack do backend: Spring Boot 4.1, Maven, JPA com Specifications | Aceito |
| [0003](0003-eventos-e-outbox.md) | Eventos entre módulos e outbox transacional | Aceito (implementação na Fase 2) |
| [0004](0004-autenticacao.md) | Autenticação: JWT próprio (RS256) e refresh token em cookie HttpOnly | Aceito |
| [0005](0005-modelo-de-dominio.md) | Ajustes no modelo de domínio a partir das respostas do cliente | Proposto (pontos a confirmar) |
| [0006](0006-alertas-por-email.md) | Alertas internos por e-mail | Aceito |
| [0007](0007-armazenamento-s3.md) | Armazenamento S3 com SeaweedFS no lugar do MinIO | Aceito |
| [0008](0008-sistema-visual.md) | Sistema visual e componentes do front | Substituído por 0010 |
| [0009](0009-arquitetura-do-frontend.md) | Arquitetura do frontend: Signals, cliente gerado, sessão em memória | Aceito |
| [0010](0010-redesign-crm-moderno.md) | Redesign: visual de CRM moderno | Aceito |
| [0011](0011-licitacoes-fase-1.md) | Licitações: ciclo de vida, permissões e sessão | Aceito |
| [0012](0012-contratos-pedidos-e-importacao.md) | Contratos, pedidos, saldo, outbox e importação | Aceito |
| [0013](0013-funil-e-tarefas.md) | Funil de vendas (negociações) e tarefas | Aceito |
| [0014](0014-atas-caronas-e-documentos.md) | Atas, caronas e documentos | Aceito |
| [0015](0015-alertas-e-painel.md) | Alertas (job com ShedLock, filas, rascunhos) e painel | Aceito |
| [0016](0016-leitura-com-ia.md) | Leitura de atas e contratos com IA | Aceito |
| [0017](0017-pedido-com-ia.md) | Pedido a partir de PDF, foto ou mensagem com IA | Aceito |
| [0018](0018-follow-up-com-ia.md) | Registro de contato, resposta do órgão e follow-up com IA | Aceito |
| [0019](0019-kit-de-carona.md) | Kit de carona sob medida (apresentação, ofício e anuência) | Aceito |
