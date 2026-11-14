package com.yanxai.tracefolio.document;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentQueryService documentQueryService;

    public DocumentController(DocumentQueryService documentQueryService) {
        this.documentQueryService = documentQueryService;
    }

    @GetMapping
    public List<DocumentSummary> listDocuments() {
        return documentQueryService.listDocuments();
    }
}
