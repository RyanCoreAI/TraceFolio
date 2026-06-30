package com.yanxai.tracefolio.runtime;

import com.yanxai.tracefolio.config.TraceFolioProperties;
import com.yanxai.tracefolio.document.DocumentQueryService;
import com.yanxai.tracefolio.document.KnowledgeStats;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class RuntimeStatusService {

    private final TraceFolioProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final DocumentQueryService documentQueryService;
    private final RestClient ollamaClient;

    public RuntimeStatusService(
            TraceFolioProperties properties,
            JdbcTemplate jdbcTemplate,
            DocumentQueryService documentQueryService
    ) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.documentQueryService = documentQueryService;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.ollamaClient = RestClient.builder()
                .baseUrl(properties.embedding().baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public RuntimeStatus getStatus() {
        RuntimeStatus.DatabaseStatus database = databaseStatus();
        RuntimeStatus.EmbeddingStatus embedding = embeddingStatus();
        RuntimeStatus.ChatStatus chat = chatStatus();
        KnowledgeStats stats = safeStats();
        boolean ready = database.connected()
                && database.vectorExtensionEnabled()
                && embedding.modelInstalled()
                && dimensionsMatch(embedding)
                && chat.apiKeyConfigured()
                && stats.chunks() > 0;

        return new RuntimeStatus("TraceFolio", ready, database, embedding, chat, stats);
    }

    private RuntimeStatus.DatabaseStatus databaseStatus() {
        try {
            String version = jdbcTemplate.queryForObject("SELECT version()", String.class);
            Boolean vectorEnabled = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')",
                    Boolean.class
            );
            Integer tableCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name LIKE 'rag_%'
                    """,
                    Integer.class
            );
            return new RuntimeStatus.DatabaseStatus(
                    true,
                    Boolean.TRUE.equals(vectorEnabled),
                    tableCount == null ? 0 : tableCount,
                    version,
                    null
            );
        } catch (Exception e) {
            return new RuntimeStatus.DatabaseStatus(false, false, 0, null, e.getMessage());
        }
    }

    private RuntimeStatus.EmbeddingStatus embeddingStatus() {
        try {
            Map<String, Object> response = ollamaClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            ModelInfo model = findModel(response);
            return new RuntimeStatus.EmbeddingStatus(
                    properties.embedding().model(),
                    properties.embedding().baseUrl(),
                    properties.embedding().dimensions(),
                    true,
                    model.installed(),
                    model.embeddingLength(),
                    null
            );
        } catch (Exception e) {
            return new RuntimeStatus.EmbeddingStatus(
                    properties.embedding().model(),
                    properties.embedding().baseUrl(),
                    properties.embedding().dimensions(),
                    false,
                    false,
                    null,
                    e.getMessage()
            );
        }
    }

    @SuppressWarnings("unchecked")
    private ModelInfo findModel(Map<String, Object> response) {
        Object modelsObject = response == null ? null : response.get("models");
        if (!(modelsObject instanceof List<?> models)) {
            return new ModelInfo(false, null);
        }

        String expected = properties.embedding().model();
        for (Object modelObject : models) {
            if (!(modelObject instanceof Map<?, ?> modelMap)) {
                continue;
            }
            String name = stringValue(modelMap.get("name"));
            String model = stringValue(modelMap.get("model"));
            if (!matchesModel(expected, name) && !matchesModel(expected, model)) {
                continue;
            }

            Object detailsObject = modelMap.get("details");
            if (detailsObject instanceof Map<?, ?> details) {
                return new ModelInfo(true, intValue(details.get("embedding_length")));
            }
            return new ModelInfo(true, null);
        }
        return new ModelInfo(false, null);
    }

    private RuntimeStatus.ChatStatus chatStatus() {
        String apiKey = properties.deepseek().apiKey();
        return new RuntimeStatus.ChatStatus(
                "DeepSeek",
                properties.deepseek().model(),
                apiKey != null && !apiKey.isBlank(),
                properties.deepseek().baseUrl()
        );
    }

    private KnowledgeStats safeStats() {
        try {
            return documentQueryService.stats();
        } catch (Exception e) {
            return new KnowledgeStats(0, 0, 0, 0);
        }
    }

    private boolean dimensionsMatch(RuntimeStatus.EmbeddingStatus embedding) {
        return embedding.actualDimensions() == null
                || embedding.actualDimensions() == embedding.expectedDimensions();
    }

    private static boolean matchesModel(String expected, String actual) {
        return expected != null
                && actual != null
                && (actual.equals(expected) || actual.equals(expected + ":latest"));
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record ModelInfo(boolean installed, Integer embeddingLength) {
    }
}
