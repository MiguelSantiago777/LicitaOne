package br.com.shema.crm.extraction.infrastructure;

import br.com.shema.crm.extraction.application.DocumentReader;
import br.com.shema.crm.extraction.application.ExtractionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Escolhe o leitor: o de demonstração se pedido ({@code SHEMA_EXTRACTION_DEMO}), o Claude quando há chave da API, senão
 * um leitor desligado (o botão some no front).
 */
@Configuration(proxyBeanMethods = false)
class DocumentReaderConfig {

    private static final Logger log = LoggerFactory.getLogger(DocumentReaderConfig.class);

    @Bean
    DocumentReader documentReader(ExtractionProperties properties, Environment environment) {
        if (properties.demo() && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("SHEMA_EXTRACTION_DEMO não pode ser ligado em produção");
        }
        if (properties.demo()) {
            log.warn("Leitura com IA em DEMONSTRAÇÃO: devolve sempre a mesma ata, sem ler o PDF (só para E2E)");
            return new DemoDocumentReader();
        }
        if (!properties.hasApiKey()) {
            log.info("Leitura com IA desligada: ANTHROPIC_API_KEY não configurada");
            return new DisabledReader();
        }
        log.info("Leitura com IA ligada, modelo {}", properties.model());
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
