package com.yanxai.tracefolio.ai;

import com.yanxai.tracefolio.config.TraceFolioProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DeepSeekChatClient {

    private final TraceFolioProperties properties;
    private final RestClient restClient;

    public DeepSeekChatClient(TraceFolioProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(120));
        this.restClient = RestClient.builder()
                .baseUrl(properties.deepseek().baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + nullToBlank(properties.deepseek().apiKey()))
                .requestFactory(requestFactory)
                .build();
    }

    public String completeJson(String prompt) {
        if (properties.deepseek().apiKey() == null || properties.deepseek().apiKey().isBlank()) {
            throw new IllegalStateException("DEEPSEEK_API_KEY is not configured");
        }

        DeepSeekRequest request = new DeepSeekRequest(
                properties.deepseek().model(),
                List.of(new Message("user", prompt)),
                Map.of("type", "json_object"),
                properties.deepseek().temperature()
        );

        DeepSeekResponse response = restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(DeepSeekResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("DeepSeek returned no choices");
        }
        Message message = response.choices().getFirst().message();
        return message == null ? "" : message.content();
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private record DeepSeekRequest(
            String model,
            List<Message> messages,
            Map<String, String> response_format,
            double temperature
    ) {
    }

    private record DeepSeekResponse(List<Choice> choices) {
    }

    private record Choice(Message message) {
    }

    private record Message(String role, String content) {
    }
}
