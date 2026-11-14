package com.yanxai.tracefolio.repo;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos")
public class RepoScanController {

    private final GitHubRepoScanner scanner;
    private final LocalRepoScanner localScanner;

    public RepoScanController(GitHubRepoScanner scanner, LocalRepoScanner localScanner) {
        this.scanner = scanner;
        this.localScanner = localScanner;
    }

    @PostMapping("/scan")
    @ResponseStatus(HttpStatus.CREATED)
    public RepoScanResponse scan(@Valid @RequestBody RepoScanRequest request) {
        if ("LOCAL".equals(request.safeSource())) {
            return localScanner.scan(request);
        }
        return scanner.scan(request);
    }
}
