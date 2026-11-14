package com.yanxai.tracefolio.claims;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final EvidenceClaimExtractor extractor;
    private final EvidenceClaimRepository repository;

    public ClaimController(EvidenceClaimExtractor extractor, EvidenceClaimRepository repository) {
        this.extractor = extractor;
        this.repository = repository;
    }

    @PostMapping("/extract")
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimExtractResponse extract(@RequestBody(required = false) ClaimExtractRequest request) {
        return extractor.extract(request);
    }

    @GetMapping
    public List<EvidenceClaim> list(
            @RequestParam(required = false) String project,
            @RequestParam(required = false) String capability,
            @RequestParam(required = false) String technology,
            @RequestParam(required = false) String strength,
            @RequestParam(required = false) String risk,
            @RequestParam(required = false) String validationStatus,
            @RequestParam(required = false) String sourceRefType,
            @RequestParam(required = false) String freshness,
            @RequestParam(required = false) Integer limit
    ) {
        return repository.list(project, capability, technology, strength, risk, validationStatus, sourceRefType, freshness, limit);
    }
}
