package br.com.shema.crm.shared.ai;

import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Esquema JSON de um registro (os {@code Raw*} da leitura com IA) para a saída estruturada do Gemini
 * ({@code responseJsonSchema}): descrições de {@code @JsonPropertyDescription}, todos os campos obrigatórios e tudo
 * em linha, sem {@code $ref}.
 */
public final class GeminiSchemas {

    private static final SchemaGenerator GENERATOR;
    private static final Map<Class<?>, String> CACHE = new ConcurrentHashMap<>();

    static {
        SchemaGeneratorConfigBuilder builder = new SchemaGeneratorConfigBuilder(
                        SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .with(new JacksonModule())
                .with(Option.INLINE_ALL_SCHEMAS)
                .without(Option.SCHEMA_VERSION_INDICATOR);
        builder.forFields().withRequiredCheck(field -> true);
        GENERATOR = new SchemaGenerator(builder.build());
    }

    private GeminiSchemas() {}

    public static ObjectNode of(Class<?> type, JsonMapper json) {
        String schema =
                CACHE.computeIfAbsent(type, t -> GENERATOR.generateSchema(t).toString());
        return (ObjectNode) json.readTree(schema);
    }
}
