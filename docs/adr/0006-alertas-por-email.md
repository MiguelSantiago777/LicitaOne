# 0006 — Alertas internos por e-mail

**Status:** Aceito · 27/10/2026

## Contexto
A especificação (5.4) diz para não enviar e-mail automaticamente no MVP e gerar só rascunhos. O cliente pediu depois "aviso por e-mail, tentando colocar o valor que falta em contrato".

## Decisão
- **Alertas para a equipe** (contrato sem pedido há 30 dias, ata vencendo, licitação vencida sem contrato) passam a ser **enviados por e-mail** aos destinatários configurados e mostram o **saldo a pedir** do contrato.
- **E-mails para o órgão continuam só como rascunho** (copiar ou abrir no cliente de e-mail), como manda a especificação. Nenhuma mensagem sai do sistema para fora da empresa sem uma pessoa revisar.
- SMTP configurável por variável de ambiente; no dev, **Mailpit** (`http://localhost:18025`) captura tudo.
- O envio é feito por um consumidor da fila de alertas (ADR 0003), com idempotência por contrato + regra + período, para que um alerta nunca chegue duas vezes.

## Pendência
⚠ Quem recebe cada alerta: o responsável pela licitação, todos ou uma lista configurável?
