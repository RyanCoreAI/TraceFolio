package com.yanxai.tracefolio.qa;

import com.yanxai.tracefolio.retrieval.RetrievalResult;
import com.yanxai.tracefolio.retrieval.RetrievalService;
import com.yanxai.tracefolio.retrieval.RetrievedChunk;
import com.yanxai.tracefolio.trace.RetrievalTraceService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QaService {

    private final RetrievalService retrievalService;
    private final AbstentionPolicy abstentionPolicy;
    private final AnswerGenerator answerGenerator;
    private final CitationValidator citationValidator;
    private final RetrievalTraceService traceService;

    public QaService(
            RetrievalService retrievalService,
            AbstentionPolicy abstentionPolicy,
            AnswerGenerator answerGenerator,
            CitationValidator citationValidator,
            RetrievalTraceService traceService
    ) {
        this.retrievalService = retrievalService;
        this.abstentionPolicy = abstentionPolicy;
        this.answerGenerator = answerGenerator;
        this.citationValidator = citationValidator;
        this.traceService = traceService;
    }

    public QaResponse answer(QaRequest request) {
        RetrievalResult retrieval = retrievalService.retrieve(request.question());
        List<RetrievedChunk> evidence = retrieval.chunks();

        Optional<String> earlyAbstention = abstentionPolicy.shouldAbstainBeforeGeneration(evidence);
        if (earlyAbstention.isPresent()) {
            UUID traceId = traceService.saveAbstained(
                    request.question(),
                    evidence,
                    retrieval.summary(),
                    earlyAbstention.get()
            );
            return new QaResponse(
                    traceId,
                    "ABSTAINED",
                    "当前知识库没有足够证据支持回答，不能确认该说法。",
                    List.of(),
                    List.of(),
                    earlyAbstention.get(),
                    retrieval.summary()
            );
        }

        GroundedAnswer answer = answerGenerator.generate(request.question(), evidence);
        CitationValidationResult validation = citationValidator.validate(answer, evidence);
        if (!validation.passed()) {
            UUID traceId = traceService.saveFailedValidation(
                    request.question(),
                    evidence,
                    retrieval.summary(),
                    answer,
                    validation.errors()
            );
            return new QaResponse(
                    traceId,
                    "FAILED_VALIDATION",
                    "模型生成的回答没有通过引用校验，本次不返回不可靠结论。",
                    List.of(),
                    answer.unsupportedClaimsOrEmpty(),
                    "CITATION_VALIDATION_FAILED",
                    retrieval.summary()
            );
        }

        Optional<String> lateAbstention = abstentionPolicy.shouldAbstainAfterGeneration(answer);
        if (lateAbstention.isPresent()) {
            UUID traceId = traceService.saveAbstained(
                    request.question(),
                    evidence,
                    retrieval.summary(),
                    lateAbstention.get()
            );
            return new QaResponse(
                    traceId,
                    "ABSTAINED",
                    answer.answer(),
                    answer.citationsOrEmpty(),
                    answer.unsupportedClaimsOrEmpty(),
                    lateAbstention.get(),
                    retrieval.summary()
            );
        }

        UUID traceId = traceService.saveAnswered(request.question(), evidence, retrieval.summary(), answer);
        return new QaResponse(
                traceId,
                "ANSWERED",
                answer.answer(),
                answer.citationsOrEmpty(),
                answer.unsupportedClaimsOrEmpty(),
                null,
                retrieval.summary()
        );
    }
}
