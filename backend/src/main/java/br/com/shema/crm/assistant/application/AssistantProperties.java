package br.com.shema.crm.assistant.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Assistente de perguntas ({@code shema.assistant.*}, ADRs 0020 e 0021). Usa o mesmo provedor e a mesma chave da
 * leitura com IA; sem a chave, o botão "Perguntar" some.
 *
 * @param dailyLimit perguntas por pessoa por dia
 * @param maxRounds rodadas de consulta por pergunta; passou disso, responde com o que tem
 * @param maxHistory perguntas anteriores da conversa que voltam como contexto
 * @param demo assistente de demonstração, sem IA (respostas por palavra-chave): só para E2E
 * @param provider {@code claude} ou {@code gemini} ({@code SHEMA_AI_PROVIDER})
 * @param geminiApiKey chave da API do Gemini ({@code GEMINI_API_KEY})
 * @param geminiModel modelo do Gemini
 */
@ConfigurationProperties("shema.assistant")
public record AssistantProperties(
        String apiKey,
        @DefaultValue("claude-opus-5-5") String model,
        @DefaultValue("medium") String effort,
        @DefaultValue("8000") long maxTokens,
        @DefaultValue("50") int dailyLimit,
        @DefaultValue("6") int maxRounds,
        @DefaultValue("10") int maxHistory,
        @DefaultValue("false") boolean demo,
        @DefaultValue("claude") String provider,
        String geminiApiKey,
        @DefaultValue("gemini-3.8-flash") String geminiModel) {

    public boolean gemini() {
        return "gemini".equalsIgnoreCase(provider);
    }

    /** Se há chave para o provedor escolhido. */
    public boolean hasApiKey() {
        String key = gemini() ? geminiApiKey : apiKey;
        return key != null && !key.isBlank();
    }

    /** Variável de ambiente da chave do provedor escolhido, para o log. */
    public String apiKeyVariable() {
        return gemini() ? "GEMINI_API_KEY" : "ANTHROPIC_API_KEY";
    }
}
