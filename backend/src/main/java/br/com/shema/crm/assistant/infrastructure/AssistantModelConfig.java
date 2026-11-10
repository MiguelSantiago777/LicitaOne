package br.com.shema.crm.assistant.infrastructure;

import br.com.shema.crm.assistant.application.AssistantModel;
import br.com.shema.crm.assistant.application.AssistantProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class AssistantModelConfig {

    private static final Logger log = LoggerFactory.getLogger(AssistantModelConfig.class);

    @Bean
    AssistantModel assistantModel(AssistantProperties properties, Environment environment, JsonMapper json) {
        if (properties.demo() && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("O assistente de demonstração não pode ser ligado em produção");
        }
        if (properties.demo()) {
            log.warn("Assistente em DEMONSTRAÇÃO: responde por palavra-chave, sem IA (só para E2E)");
            return new DemoAssistantModel(json);
        }
        if (!properties.hasApiKey()) {
            log.info("Assistente desligado: {} não configurada", properties.apiKeyVariable());
            return new Disabled();
        }
        if (properties.gemini()) {
            log.info("Assistente ligado, Gemini, modelo {}", properties.geminiModel());
            return new GeminiAssistantModel(properties, json);
        }
        log.info("Assistente ligado, Claude, modelo {}", properties.model());
        return new ClaudeAssistantModel(properties);
    }

    static final class Disabled implements AssistantModel {

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public Reply next(String system, List<Entry> conversation, List<ToolSpec> tools) {
            throw new ModelFailed("O assistente não está configurado neste ambiente.", null);
        }
    }
}
