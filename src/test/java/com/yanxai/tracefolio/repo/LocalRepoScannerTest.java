package com.yanxai.tracefolio.repo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LocalRepoScannerTest {

    @Test
    void rejectsLocalScanWithoutOwnershipConfirmation() {
        LocalRepoScanner scanner = new LocalRepoScanner(null, null, null);

        assertThatThrownBy(() -> scanner.scan(new RepoScanRequest(
                "LOCAL",
                "RyanCoreAI",
                "TraceFolio",
                null,
                "D:\\TraceFolio",
                false,
                10
        )))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("confirmOwnership=true");
    }
}
