package br.com.shema.crm.extraction.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Leitura com IA ({@code shema.extraction.*}, ADRs 0016 e 0021). Sem a chave do provedor escolhido, a funcionalidade
 * fica desligada.
 *
 * @param apiKey chave da API da Anthropic ({@code ANTHROPIC_API_KEY}); nunca no repositório
 * @param model modelo do Claude
 * @param effort quanto o modelo pensa: low, medium, high, xhigh ou max
 * @param maxTokens teto da resposta (inclui o raciocínio); ata com centenas de itens precisa de folga
 * @param fallbacks se o servidor da Anthropic tenta outro modelo quando o principal recusa o documento
 * @param companyName empresa dona do sistema, para o modelo achar os itens dela e para avisar se o fornecedor é outro
 * @param maxFileMb maior PDF aceito: a requisição tem limite de 32 MB, e o PDF vai em base64 (cerca de 33% maior)
 * @param demo leitor de demonstração, sem IA (sempre a mesma leitura): só para E2E e para ver a tela sem chave
 * @param provider {@code claude} ou {@code gemini} ({@code SHEMA_AI_PROVIDER})
 * @param geminiApiKey chave da API do Gemini ({@code GEMINI_API_KEY}); nunca no repositório
 * @param geminiModel modelo do Gemini
 */
@ConfigurationProperties("shema.extraction")
public record ExtractionProperties(
        String apiKey,
        @DefaultValue("claude-opus-5-5") String model,
        @DefaultValue("medium") String effort,
        @DefaultValue("32000") long maxTokens,
        @DefaultValue("5") int timeoutMinutes,
        @DefaultValue("true") boolean fallbacks,
        @DefaultValue("Shema Suprimentos e Serviços") String companyName,
        @DefaultValue("20") int maxFileMb,
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

    public long maxFileBytes() {
        return maxFileMb * 1024L * 1024L;
    }
}
