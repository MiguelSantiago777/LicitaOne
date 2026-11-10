package br.com.shema.crm.extraction.infrastructure;

import br.com.shema.crm.extraction.application.DocumentReader;
import br.com.shema.crm.extraction.application.ExtractionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escolhe o leitor: o de demonstração se pedido ({@code SHEMA_EXTRACTION_DEMO}), o Claude ou o Gemini
 * ({@code SHEMA_AI_PROVIDER}) quando há chave da API, senão um leitor desligado (o botão some no front).
 */
@Configuration(proxyBeanMethods = false)
class DocumentReaderConfig {

    private static final Logger log = LoggerFactory.getLogger(DocumentReaderConfig.class);

    @Bean
    DocumentReader documentReader(ExtractionProperties properties, Environment environment, JsonMapper json) {
        if (properties.demo() && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("SHEMA_EXTRACTION_DEMO não pode ser ligado em produção");
        }
        if (properties.demo()) {
            log.warn("Leitura com IA em DEMONSTRAÇÃO: devolve sempre a mesma ata, sem ler o PDF (só para E2E)");
            return new DemoDocumentReader();
        }
        if (!properties.hasApiKey()) {
            log.info("Leitura com IA desligada: {} não configurada", properties.apiKeyVariable());
            return new DisabledReader();
        }
        if (properties.gemini()) {
            log.info("Leitura com IA ligada, Gemini, modelo {}", properties.geminiModel());
            return new GeminiDocumentReader(properties, json);
        }
        log.info("Leitura com IA ligada, Claude, modelo {}", properties.model());
        return new ClaudeDocumentReader(properties);
    }

    static final class DisabledReader implements DocumentReader {

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public Reading read(ReadRequest request) {
            throw new ReadingFailed("A leitura com IA não está configurada neste ambiente.");
        }
    }
}
