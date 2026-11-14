package com.yanxai.tracefolio.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sun.net.httpserver.HttpServer;
import com.yanxai.tracefolio.source.BlogArticleLoader;
import com.yanxai.tracefolio.repo.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

class IngestBoundaryTest {
    @TempDir Path directory;

    @Test
    void blogIngestRejectsPrivateAddressInsteadOfFetchingIt() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "<html><title>private service</title><article>synthetic private response</article></html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            assertThatThrownBy(() -> new BlogArticleLoader().load("http://127.0.0.1:" + server.getAddress().getPort() + "/"))
                    .isInstanceOf(ResponseStatusException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void localScanIsDisabledUntilServerOperatorAllowsRoots() throws Exception {
        Files.writeString(directory.resolve("README.md"), "Synthetic test evidence.");
        var analyzer = mock(GitHubRepoScanner.class);
        var scanner = new LocalRepoScanner(analyzer, mock(RepoScanRepository.class), mock(RepoEvidenceClaimWriter.class));
        assertThatThrownBy(() -> scanner.scan(new RepoScanRequest("LOCAL", "test", "repo", null, directory.toString(), true, 10)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(analyzer);
    }
}
