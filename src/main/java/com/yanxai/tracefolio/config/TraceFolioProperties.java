package com.yanxai.tracefolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracefolio")
public record TraceFolioProperties(
        String tenantId,
        Embedding embedding,
        DeepSeek deepseek,
        GitHub github,
        Retrieval retrieval,
        Generation generation
) {
    public String tenantId() {
        return tenantId == null || tenantId.isBlank() ? "ryan" : tenantId;
    }

    public Embedding embedding() {
        return embedding == null ? new Embedding("bge-m3", 1024, "http://localhost:11434") : embedding;
    }

    public DeepSeek deepseek() {
        return deepseek == null ? new DeepSeek("", "https://api.deepseek.com", "deepseek-v4-flash", 0.1) : deepseek;
    }

    public GitHub github() {
        return github == null ? new GitHub("") : github;
    }

    public Retrieval retrieval() {
        return retrieval == null ? new Retrieval(12, 12, 8, 60, 2) : retrieval;
    }

    public Generation generation() {
        return generation == null ? new Generation(12000) : generation;
    }

    public record Embedding(String model, int dimensions, String baseUrl) {
        public String model() {
            return model == null || model.isBlank() ? "bge-m3" : model;
        }

        public int dimensions() {
            return dimensions <= 0 ? 1024 : dimensions;
        }

        public String baseUrl() {
            return baseUrl == null || baseUrl.isBlank() ? "http://localhost:11434" : baseUrl;
        }
    }

    public record DeepSeek(String apiKey, String baseUrl, String model, double temperature) {
        public String baseUrl() {
            return baseUrl == null || baseUrl.isBlank() ? "https://api.deepseek.com" : baseUrl;
        }

        public String model() {
            return model == null || model.isBlank() ? "deepseek-v4-flash" : model;
        }
    }

    public record GitHub(String token) {
    }

    public record Retrieval(int vectorTopK, int ftsTopK, int finalTopK, int rrfK, int minSelectedChunks) {
        public int vectorTopK() {
            return vectorTopK <= 0 ? 12 : vectorTopK;
        }

        public int ftsTopK() {
            return ftsTopK <= 0 ? 12 : ftsTopK;
        }

        public int finalTopK() {
            return finalTopK <= 0 ? 8 : finalTopK;
        }

        public int rrfK() {
            return rrfK <= 0 ? 60 : rrfK;
        }

        public int minSelectedChunks() {
            return minSelectedChunks <= 0 ? 2 : minSelectedChunks;
        }
    }

    public record Generation(int maxEvidenceChars) {
        public int maxEvidenceChars() {
            return maxEvidenceChars <= 0 ? 12000 : maxEvidenceChars;
        }
    }
}
