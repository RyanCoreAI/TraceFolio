package com.yanxai.tracefolio.qa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanxai.tracefolio.ai.DeepSeekChatClient;
import com.yanxai.tracefolio.config.TraceFolioProperties;
import com.yanxai.tracefolio.retrieval.RetrievedChunk;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AnswerGenerator {

    private final TraceFolioProperties properties;
    private final DeepSeekChatClient chatClient;
    private final ObjectMapper objectMapper;

    public AnswerGenerator(TraceFolioProperties properties, DeepSeekChatClient chatClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    public GroundedAnswer generate(String question, List<RetrievedChunk> evidence) {
        String prompt = buildPrompt(question, evidence);
        try {
            String content = chatClient.completeJson(prompt);
            return parse(content);
        } catch (Exception e) {
            return new GroundedAnswer(
                    "ABSTAINED",
                    "模型调用失败，无法生成可靠回答。",
                    List.of(),
                    List.of(),
                    "MODEL_CALL_FAILED: " + e.getMessage()
            );
        }
    }

    private GroundedAnswer parse(String content) {
        if (content == null || content.isBlank()) {
            return new GroundedAnswer("ABSTAINED", "模型返回空内容。", List.of(), List.of(), "EMPTY_MODEL_RESPONSE");
        }

        try {
            return objectMapper.readValue(content, GroundedAnswer.class);
        } catch (Exception e) {
            return new GroundedAnswer("ABSTAINED", "模型返回内容不是合法 JSON。", List.of(), List.of(), "INVALID_JSON_RESPONSE");
        }
    }

    private String buildPrompt(String question, List<RetrievedChunk> evidence) {
        String evidenceText = evidence.stream()
                .map(this::formatEvidence)
                .collect(Collectors.joining("\n\n"));
        int maxChars = properties.generation().maxEvidenceChars();
        if (evidenceText.length() > maxChars) {
            evidenceText = evidenceText.substring(0, maxChars);
        }

        return """
                你是 Ryan 的个人技术资产证据库助手。

                请严格输出 JSON。不要输出 Markdown。不要输出解释文字。

                规则：
                1. 只能基于给定 evidence 回答。
                2. 如果 evidence 不足，必须拒答。
                3. 不允许为了让 Ryan 看起来更强而夸大。
                4. 不允许把“参与”“实现”“尝试”改成“主导”“负责”“生产级落地”。
                5. 不允许把 demo、prototype、local-first 项目说成企业大规模采用。
                6. 不允许补充 evidence 里没有的技术栈、数据规模、实习经历、商业结果。
                7. 每个关键结论必须给 citation。
                8. citation.chunkId 必须来自 evidence 里的 chunk_id。
                9. citation.quote 必须逐字来自对应 chunk 的 content。
                10. 如果某个结论没有证据支持，放入 unsupportedClaims。
                11. 如果 unsupportedClaims 涉及核心结论，status 必须为 ABSTAINED。

                用户问题：
                %s

                Evidence:
                %s

                JSON 输出格式：
                {
                  "status": "ANSWERED",
                  "answer": "...",
                  "citations": [
                    {
                      "chunkId": "uuid",
                      "sourceType": "BLOG",
                      "title": "...",
                      "sourceUri": "...",
                      "headingPath": "...",
                      "quote": "必须来自原文的短引用"
                    }
                  ],
                  "unsupportedClaims": [],
                  "abstentionReason": null
                }
                """.formatted(question, evidenceText);
    }

    private String formatEvidence(RetrievedChunk chunk) {
        return """
                [chunk_id=%s]
                sourceType=%s
                sourceUri=%s
                title=%s
                headingPath=%s
                finalRank=%s
                rrfScore=%s
                content=%s
                """.formatted(
                chunk.id(),
                chunk.sourceType(),
                chunk.sourceUri(),
                chunk.title(),
                chunk.headingPath(),
                chunk.finalRank(),
                chunk.rrfScore(),
                chunk.content()
        );
    }
}
