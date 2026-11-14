package com.yanxai.tracefolio.source;

import com.yanxai.tracefolio.document.KnowledgeIngestService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingest")
public class IngestController {

    private final BlogArticleLoader blogArticleLoader;
    private final GitHubContentLoader gitHubContentLoader;
    private final KnowledgeIngestService ingestService;

    public IngestController(
            BlogArticleLoader blogArticleLoader,
            GitHubContentLoader gitHubContentLoader,
            KnowledgeIngestService ingestService
    ) {
        this.blogArticleLoader = blogArticleLoader;
        this.gitHubContentLoader = gitHubContentLoader;
        this.ingestService = ingestService;
    }

    @PostMapping("/blog")
    public IngestResponse ingestBlog(@Valid @RequestBody BlogIngestRequest request) {
        RawDocument document = blogArticleLoader.load(request.url());
        return ingestService.ingest(document);
    }

    @PostMapping("/github")
    public IngestResponse ingestGitHub(@Valid @RequestBody GitHubIngestRequest request) {
        String path = request.pathOrReadme();
        RawDocument document = gitHubContentLoader.loadFile(
                request.owner(),
                request.repo(),
                path,
                path.equalsIgnoreCase("README.md") ? "GITHUB_README" : "GITHUB_DOC"
        );
        return ingestService.ingest(document);
    }
}
