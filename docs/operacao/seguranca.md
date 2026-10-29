# Segurança: checklist OWASP ASVS nível 1

Revisão de 29/10/2026 (Fase 6), contra o ASVS 4.0.3 nível 1. Cada item tem a evidência (teste, configuração ou código).
- ✅ atendido;
- ⚠ atendido com ressalva ou parte fica para a operação;
- n/a não se aplica ao LicitaOne.

## V1 Arquitetura
| Item | Situação | Evidência |
|---|---|---|
| Componentes e fluxos de dados documentados | ✅ | ADRs 0001–0015; mensageria em `docs/operacao/mensageria.md` |
| Autorização no servidor, nunca só no front | ✅ | `@PreAuthorize` nos endpoints e `CurrentUser.canEditOwnedBy` nos serviços; testes de permissão nos `*FlowTests` |

## V2 Autenticação
| Item | Situação | Evidência |
|---|---|---|
| 2.1.1–2.1.2 Senha de 12 a 128 caracteres | ✅ | `PasswordPolicy` (Fase 6; antes eram 10, sem máximo); `PasswordPolicyTest`, `AuthFlowTests.weakPasswordsAreRefusedWithTheReason` |
| 2.1.3–2.1.4 Sem truncar, qualquer caractere (Unicode, espaço) | ✅ | contagem por code point; frase-senha aceita |
| 2.1.5–2.1.6 Troca de senha pede a senha atual | ✅ | `UserAdmin.changeOwnPassword` |
| 2.1.7 Recusa senhas comuns/vazadas | ✅ | lista de senhas comuns (inclusive brasileiras), repetições e sequências; senha com o e-mail |
| 2.1.9 Sem regra de composição | ✅ | não há "uma maiúscula e um número" |
| 2.1.11 Colar e gerenciador de senha | ✅ | campos `type=password` com `autocomplete`, sem bloqueio de colar |
| 2.2.1 Proteção contra força bruta | ✅ | limite por IP (Bucket4j, `shema.auth.login-attempts-per-minute`) e bloqueio da conta depois de 5 erros por 15 min (`AuthFlowTests.locksAccountAfterRepeatedFailures`) |
| 2.2.2 Sem SMS/e-mail como fator fraco | n/a | só senha (MFA fica como evolução, nível 2) |
| 2.5.x Recuperação de senha | ⚠ | não há autoatendimento: o administrador define uma senha nova (`resetPassword`) e todas as sessões da pessoa caem |
| 2.10 Segredos de serviço fora do código | ✅ | `.env` / variáveis de ambiente; `.env` no `.gitignore` |

## V3 Sessão
| Item | Situação | Evidência |
|---|---|---|
| 3.1.1 Token nunca na URL | ✅ | access token no header `Authorization`; refresh token em cookie |
| 3.2.x Token novo a cada login, aleatório | ✅ | refresh token de 256 bits, guardado só o hash (`TokenHasher`) |
| 3.3.1 Logout invalida a sessão | ✅ | `/auth/logout` revoga o refresh token (`AuthFlowTests.refreshAfterLogout…`) |
| 3.3.2 Expiração | ✅ | access 15 min, refresh 7 dias com rotação e detecção de reuso (`refreshRotatesTokenAndDetectsReuse`) |
| 3.4.1–3.4.3 Cookie `Secure`, `HttpOnly`, `SameSite` | ✅ | `HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth`; `Secure` no perfil `prod` (`shema.auth.secure-cookie`) |
| 3.5 Tokens assinados e validados | ✅ | JWT RS256 com `kid`, emissor validado; **em `prod`, sem `SHEMA_JWT_PRIVATE_KEY` o backend não sobe** (`JwtKeyConfigTest`, Fase 6) |

## V4 Controle de acesso
| Item | Situação | Evidência |
|---|---|---|
| 4.1.1–4.1.3 Negado por padrão, regras no servidor | ✅ | `anyRequest().authenticated()`; perfis ADMIN/COMERCIAL/LEITURA |
| 4.2.1 Acesso a registro de outra pessoa (IDOR) | ✅ | COMERCIAL só altera o que é seu ou sem responsável; testes de permissão em licitações (`salesEditsOnlyOwnAndViewerNeverWrites`), contratos, negociações e tarefas (`DealFlowTests`), quadros e atas |
| 4.3.1 Telas de administração protegidas | ✅ | `/users`, `/admin/dead-letters`, `/alerts/run` só ADMIN; Actuator só na porta de gestão (8081), fora do proxy |

## V5 Validação e codificação
| Item | Situação | Evidência |
|---|---|---|
| 5.1.x Validação de entrada | ✅ | Bean Validation nos DTOs (records) e regras no domínio; erros RFC 7807 com códigos estáveis |
| 5.1.2 Mass assignment | ✅ | DTOs próprios por operação; entidades nunca saem nem entram pela API |
| 5.2.x HTML não confiável | ✅ | Angular escapa tudo; nenhum `innerHTML`/`bypassSecurityTrust*` no código |
| 5.3.4 SQL injection | ✅ | JPA Criteria e consultas com parâmetro nomeado; `LIKE` com escape (`TenderSpecifications.escape`) |
| 5.3.x Cabeçalhos e nomes de arquivo | ✅ | download com `Content-Disposition: attachment` e nome limpo (`FileInspection.cleanName`) |

## V7 Erros e logs
| Item | Situação | Evidência |
|---|---|---|
| 7.1.1–7.1.2 Sem segredo nem senha nos logs | ✅ | logs só com o id do usuário; logs JSON (ECS) com `correlationId` |
| 7.4.1 Erro genérico para o usuário | ✅ | 500 vira "Algo deu errado. Tente de novo em instantes." e o detalhe fica só no log (`GlobalExceptionHandler`) |
| Eventos de segurança registrados | ✅ | login recusado, conta bloqueada, reuso de refresh token |

## V8 Proteção de dados
| Item | Situação | Evidência |
|---|---|---|
| 8.1.1 Sem cache de dados sensíveis | ✅ | respostas da API com `Cache-Control: no-store` (Spring Security) |
| 8.2.x Dados no navegador | ✅ | access token só em memória; nada é guardado em `localStorage` |
| 8.3.x LGPD: dados pessoais mínimos | ✅ | contatos de órgão com nome, cargo, e-mail e telefone apenas; política de retenção no runbook |

## V9 Comunicação
| Item | Situação | Evidência |
|---|---|---|
| 9.1.1 TLS em tudo | ⚠ | o compose é HTTP local; em produção o TLS é obrigatório no proxy da frente (runbook). O nginx já manda `Strict-Transport-Security`, que o navegador só aplica sob HTTPS |

## V10 Código malicioso e V14 Configuração
| Item | Situação | Evidência |
|---|---|---|
| 10.3.2 / 14.2.1 Dependências sem vulnerabilidade conhecida | ✅ | Trivy no CI: dependências (backend e frontend) e, desde a Fase 6, as **imagens** (Alpine + jars). Em 29/10/2026: 0 CRITICAL/HIGH depois de subir o `amqp-client` (5.30.0 → 5.37.0) e rodar `apk upgrade` nas duas imagens |
| 14.1.x Build reproduzível, imagens enxutas, sem root | ✅ | multi-stage; backend com usuário `shema`; nginx sem root (porta 8080) |
| 14.3.2 Sem modo debug em produção | ✅ | perfil `prod`: Swagger e `/v3/api-docs` desligados, seed fora do Flyway |
| 14.4.x Cabeçalhos de segurança | ✅ | CSP (`default-src 'self'`, `frame-ancestors 'none'`), `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Permissions-Policy`, HSTS (Fase 6) |
| 14.5.3 CORS restrito | ✅ | só as origens de `SHEMA_CORS_ORIGINS`; o front e a API saem pela mesma origem no nginx |

## V12 Arquivos
| Item | Situação | Evidência |
|---|---|---|
| 12.1.1 Tamanho máximo | ✅ | 25 MB por arquivo (`FileInspection`, multipart e `client_max_body_size`) |
| 12.2.1 Tipo pelo conteúdo | ✅ | assinatura dos primeiros bytes tem que bater com a extensão (`FileInspectionTest`) |
| 12.3.x Nome do arquivo não vira caminho | ✅ | chave no S3 vem de ids (`documents/<licitação>/<documento>/v<n>`), nunca do nome enviado |
| 12.4.x Antivírus | ⚠ | fica como evolução, como diz a especificação (ClamAV no pós-processamento) |

## V13 API
| Item | Situação | Evidência |
|---|---|---|
| 13.1.x Mesma autorização em todos os verbos | ✅ | regras por método e por recurso; `PUT`/`DELETE` com versão (409 em conflito) |
| 13.2.x JSON validado, tipos estritos | ✅ | Jackson com records; requisição mal formada vira 400 `MALFORMED_REQUEST` |
| Idempotência nos POSTs críticos | ✅ | `Idempotency-Key` em pedidos e importação |

## O que fica para a operação de produção
- **TLS** no proxy da frente (ou certificado no nginx).
- **Chave JWT** própria (`SHEMA_JWT_PRIVATE_KEY`) e senhas fortes no `.env`.
- **Troca da senha inicial** do administrador no primeiro acesso: o log avisa se ela não segue a política.
- **SMTP** com TLS e credenciais.
- **Backup** cifrado e fora do servidor.

Os passos estão em [`runbook.md`](runbook.md).
