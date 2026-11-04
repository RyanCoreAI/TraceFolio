package com.yanxai.tracefolio.portfolio;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioExportService exportService;

    public PortfolioController(PortfolioExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping("/export")
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioExportResponse export(@RequestBody(required = false) PortfolioExportRequest request) {
        return exportService.export(request);
    }

    @GetMapping("/exports")
    public List<PortfolioExportResponse> list(@RequestParam(required = false) Integer limit) {
        return exportService.list(limit);
    }

    @GetMapping("/exports/{exportId}")
    public PortfolioExportResponse find(@PathVariable UUID exportId) {
        return exportService.find(exportId);
    }

    @GetMapping("/exports/{exportId}/content")
    public ResponseEntity<String> content(@PathVariable UUID exportId) {
        PortfolioExportResponse export = exportService.find(exportId);
        MediaType mediaType = "HTML".equals(export.format())
                ? MediaType.TEXT_HTML
                : new MediaType("text", "markdown", StandardCharsets.UTF_8);
        String extension = "HTML".equals(export.format()) ? "html" : "md";
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; img-src data:; base-uri 'none'; form-action 'none'; sandbox")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("tracefolio-evidence-pack.%s".formatted(extension), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(export.content());
    }
}
