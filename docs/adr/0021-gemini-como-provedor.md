# 0021 — Gemini como provedor alternativo da IA

**Status:** Proposto · 09/11/2026 · complementa os ADRs [0016](0016-leitura-com-ia.md), [0017](0017-pedido-com-ia.md), [0018](0018-follow-up-com-ia.md), [0019](0019-kit-de-carona.md) e [0020](0020-assistente-de-perguntas.md)

## Contexto
A API da Anthropic é paga por uso e não tem plano gratuito. A pedido do usuário, a IA precisa poder rodar sem custo. A API do Gemini (Google AI Studio) tem plano gratuito para os modelos Flash e Flash-Lite, com limite por minuto e por dia definido por projeto, consultado no AI Studio. Segundo a página de preços em 09/11/2026, no plano gratuito o conteúdo enviado é "used to improve our products". No plano pago, não é.

## Decisões
- `SHEMA_AI_PROVIDER` escolhe o provedor (`claude`, o padrão, ou `gemini`) para a leitura com IA e para o assistente. Sem a chave do provedor escolhido (`ANTHROPIC_API_KEY` ou `GEMINI_API_KEY`), a IA fica desligada, como antes.
- O Gemini entra como mais uma implementação das interfaces que já existiam: `GeminiDocumentReader` (`DocumentReader`) e `GeminiAssistantModel` (`AssistantModel`). Os prompts, os esquemas `Raw*`, a conferência dos dados e o laço de consultas do assistente são os mesmos.
- A chamada é HTTP direto ao `generateContent` (`shared.ai.GeminiClient`), sem SDK. O PDF e as fotos vão em `inlineData`, e a saída estruturada usa `responseJsonSchema`, gerado dos registros `Raw*` com o victools, a mesma biblioteca que o SDK da Anthropic usa por dentro. No assistente, as consultas viram `functionDeclarations`, e o `content` do modelo volta inteiro na rodada seguinte, com as assinaturas de raciocínio.
- Erro 429 por minuto e erros 5xx são tentados de novo duas vezes, com espera curta. A cota do dia esgotada não é tentada de novo e aparece como "A cota gratuita da IA acabou por hoje".
- O modelo padrão é `gemini-3.8-flash`, que é gratuito no plano free.

## Consequências
- Sem custo no plano gratuito, dentro da cota diária. Se a cota não bastar, basta ativar o faturamento no projeto do Google, sem mudar código.
- ⚠ **Autorização do cliente:** a autorização de 04/11/2026 cobre o envio à Anthropic. Enviar atas, contratos, pedidos e mensagens ao Google, ainda mais no plano gratuito, em que o conteúdo pode ser usado pelo Google, precisa de nova autorização antes de ligar em produção.
- O Gemini não tem o fallback de modelo do servidor da Anthropic. Uma recusa por segurança termina a leitura com "A IA não aceitou ler este documento".
- Os campos `model` e os tokens gravados mostram o modelo do Gemini. A métrica de tokens continua valendo, mas no plano gratuito não vira custo.
- A qualidade da leitura de atas longas com o Gemini Flash ainda não foi medida com documentos reais. Convém comparar algumas atas com a leitura do Claude antes de trocar em produção.
