package com.yanxai.tracefolio.trace;

import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/traces")
public class RetrievalTraceController {

    private final RetrievalTraceService traceService;

    public RetrievalTraceController(RetrievalTraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping("/{traceId}")
    public QueryTraceView find(@PathVariable UUID traceId) {
        try {
            return traceService.find(traceId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace not found: " + traceId, e);
        }
    }
}
