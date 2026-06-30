package com.yanxai.tracefolio.runtime;

import com.yanxai.tracefolio.document.KnowledgeStats;

public record RuntimeStatus(
        String appName,
        boolean readyForAnsweredQa,
        DatabaseStatus database,
        EmbeddingStatus embedding,
        ChatStatus chat,
        KnowledgeStats knowledge
) {
    public record DatabaseStatus(
            boolean connected,
            boolean vectorExtensionEnabled,
            int ragTableCount,
            String version,
            String error
    ) {
    }

    public record EmbeddingStatus(
            String model,
            String baseUrl,
            int expectedDimensions,
            boolean ollamaReachable,
            boolean modelInstalled,
            Integer actualDimensions,
            String error
    ) {
    }

    public record ChatStatus(
            String provider,
            String model,
            boolean apiKeyConfigured,
            String baseUrl
    ) {
    }
}
