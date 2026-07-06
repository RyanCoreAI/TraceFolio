package com.yanxai.tracefolio.jd;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/jd")
public class JdController {

    private final JdRequirementParser parser;
    private final JdEvidenceMatcher matcher;
    private final JdRunRepository repository;

    public JdController(JdRequirementParser parser, JdEvidenceMatcher matcher, JdRunRepository repository) {
        this.parser = parser;
        this.matcher = matcher;
        this.repository = repository;
    }

    @PostMapping("/compile")
    @ResponseStatus(HttpStatus.CREATED)
    public JdCompileResponse compile(@Valid @RequestBody JdCompileRequest request) {
        List<JdRequirement> requirements = parser.parse(request.jdText());
        UUID runId = repository.createRun(request.jdText(), requirements);
        return new JdCompileResponse(runId, requirements);
    }

    @PostMapping("/match")
    public JdMatchResponse match(@RequestBody JdMatchRequest request) {
        JdRunView run = request.runId() == null ? null : repository.find(request.runId());
        String jdText = firstNonBlank(request.jdText(), run == null ? null : run.jdText());
        List<JdRequirement> requirements = request.requirements() == null || request.requirements().isEmpty()
                ? run == null ? parser.parse(jdText) : run.requirements()
                : request.requirements();

        if (requirements == null || requirements.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JD 没有解析出有效岗位要求。");
        }

        UUID runId = request.runId() == null
                ? repository.createRun(jdText == null ? "" : jdText, requirements)
                : request.runId();

        List<EvidenceMatrixRow> matrix = matcher.match(requirements);
        repository.updateRequirements(runId, requirements);
        repository.updateEvidenceMatrix(runId, matrix);
        return new JdMatchResponse(runId, matcher.summarize(matrix), matrix);
    }

    @GetMapping("/runs/{runId}")
    public JdRunView run(@PathVariable UUID runId) {
        return repository.find(runId);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
