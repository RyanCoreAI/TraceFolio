package com.yanxai.tracefolio.ai;

import com.yanxai.tracefolio.config.TraceFolioProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OllamaEmbeddingClient {

    private final TraceFolioProperties properties;
    private final RestClient restClient;

    public OllamaEmbeddingClient(TraceFolioProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(120));
        this.restClient = RestClient.builder()
                .baseUrl(properties.embedding().baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public float[] embed(String input) {
        OllamaEmbedResponse response = restClient.post()
                .uri("/api/embed")
                .body(new OllamaEmbedRequest(properties.embedding().model(), input))
                .retrieve()
                .body(OllamaEmbedResponse.class);

        if (response == null) {
            throw new IllegalStateException("Ollama returned empty embedding response");
        }

        List<Double> vector = response.firstVector();
        if (vector.isEmpty()) {
            throw new IllegalStateException("Ollama returned no embeddings");
        }

        float[] result = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            result[i] = vector.get(i).floatValue();
        }
        return result;
    }

    private record OllamaEmbedRequest(String model, String input) {
    }

    private record OllamaEmbedResponse(List<List<Double>> embeddings, List<Double> embedding) {
        List<Double> firstVector() {
            if (embeddings != null && !embeddings.isEmpty()) {
                return embeddings.getFirst();
            }
            if (embedding != null) {
                return embedding;
            }
            return List.of();
        }
    }
}
