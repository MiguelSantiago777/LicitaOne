package br.com.shema.crm.shared.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(title = "ShemaCRM API", version = "v1", description = "Gestão de licitações públicas"),
        security = @SecurityRequirement(name = "bearer"))
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    /**
     * Convenção: todo campo de um record *Response está sempre presente (um valor ausente vem como null
     * explícito, marcado com nullable). Assim o cliente gerado no front não trata tudo como opcional.
     */
    @Bean
    OpenApiCustomizer responsesHaveRequiredFields() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            for (Map.Entry<String, Schema> entry :
                    openApi.getComponents().getSchemas().entrySet()) {
                Schema<?> schema = entry.getValue();
                if (entry.getKey().endsWith("Response") && schema.getProperties() != null) {
                    schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
                }
            }
        };
    }
}
