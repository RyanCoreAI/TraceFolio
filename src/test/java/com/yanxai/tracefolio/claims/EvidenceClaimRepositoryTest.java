package com.yanxai.tracefolio.claims;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanxai.tracefolio.config.TraceFolioProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class EvidenceClaimRepositoryTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EvidenceClaimValidator validator = mock(EvidenceClaimValidator.class);
    private final EvidenceClaimRepository repository = new EvidenceClaimRepository(
            new TraceFolioProperties("tenant", null, null, null, null, null), jdbc, new ObjectMapper(), validator);

    @Test
    void reviewLookupIsTenantScopedAndNotLimitedToTheClaimRankingWindow() {
        UUID source = UUID.randomUUID();
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(source));
        assertThat(repository.blockedSourceChunks(List.of(source))).containsExactly(source);
        verify(jdbc).query(argThat(sql -> sql.contains("tenant_id = ?") && sql.contains("source_chunk_id IN (?)")
                        && sql.contains("validation_status IS NULL") && sql.contains("validation_status <> 'VALID'")
                        && !sql.contains("LIMIT")), any(org.springframework.jdbc.core.RowMapper.class), eq("tenant"), eq(source));
    }

    @Test
    void emptyReviewLookupDoesNotIssueAnInvalidInQuery() {
        assertThat(repository.blockedSourceChunks(List.of())).isEmpty();
        verifyNoInteractions(jdbc);
    }

    @Test
    void savedEvidenceLookupChecksChunkAndClaimIdsUnderTheSameTenant() {
        UUID reference = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(Object[].class))).thenReturn(true);
        assertThat(repository.hasBlockedEvidenceReferences(List.of(reference))).isTrue();
        verify(jdbc).queryForObject(argThat(sql -> sql.contains("tenant_id = ?")
                        && sql.contains("id IN (?) OR source_chunk_id IN (?)")
                        && sql.contains("validation_status <> 'VALID'")),
                eq(Boolean.class), eq("tenant"), eq(reference), eq(reference));
    }

    @Test
    void recognizesExistingChunkClaimWithNullScanAndTechnologyWithoutDeletingHistory() {
        EvidenceClaimDraft claim = draft("CHUNK", UUID.randomUUID(), null);
        when(validator.validate(claim)).thenReturn(new EvidenceClaimValidation("VALID", "UNKNOWN", List.of(), List.of()));
        when(jdbc.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), any(Object[].class))).thenReturn(true);
        assertThat(repository.insert(claim)).isFalse();
        InOrder order = inOrder(jdbc);
        order.verify(jdbc).queryForObject(contains("FROM rag_chunk"), eq(UUID.class), eq(claim.sourceChunkId()), eq("tenant"));
        order.verify(jdbc).queryForObject(argThat(sql -> sql.contains("source_chunk_id IS NOT DISTINCT FROM")
                        && sql.contains("repo_scan_id IS NOT DISTINCT FROM") && sql.contains("technology IS NOT DISTINCT FROM")),
                eq(Boolean.class), eq("tenant"), eq("CHUNK"), eq(claim.sourceChunkId()), isNull(),
                eq("capability"), isNull(), anyString());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void serializesRepoScanClaimsOnExistingParentBeforeInserting() {
        EvidenceClaimDraft claim = draft("REPO_SCAN", null, UUID.randomUUID());
        when(validator.validate(claim)).thenReturn(new EvidenceClaimValidation("VALID", "UNKNOWN", List.of(), List.of()));
        when(jdbc.update(contains("INSERT INTO rag_evidence_claim"), any(Object[].class))).thenReturn(1);
        assertThat(repository.insert(claim)).isTrue();
        InOrder order = inOrder(jdbc);
        order.verify(jdbc).queryForObject(contains("FROM rag_repo_scan"), eq(UUID.class), eq(claim.repoScanId()), eq("tenant"));
        order.verify(jdbc).queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), any(Object[].class));
        order.verify(jdbc).update(contains("INSERT INTO rag_evidence_claim"), any(Object[].class));
    }

    @Test
    void locksAndDuplicateReadShareANewReadCommittedTransaction() throws Exception {
        EvidenceClaimDraft claim = draft("CHUNK", UUID.randomUUID(), null);
        when(validator.validate(claim)).thenReturn(new EvidenceClaimValidation("VALID", "UNKNOWN", List.of(), List.of()));
        when(jdbc.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), any(Object[].class))).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                    .isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
            return true;
        });
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        ProxyFactory factory = new ProxyFactory(repository);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),
                new AnnotationTransactionAttributeSource()));
        EvidenceClaimRepository transactional = (EvidenceClaimRepository) factory.getProxy();
        assertThat(transactional.insert(claim)).isFalse();
        verify(connection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        verify(connection).commit();
    }

    private EvidenceClaimDraft draft(String type, UUID chunk, UUID scan) {
        return new EvidenceClaimDraft(type, chunk, scan, "file", "FACT", "subject", "capability", "action",
                null, "project", "result", "MARKDOWN", "Doc", "source://doc", "heading", "quote",
                "STRONG", "LOW", 0.9, "RULE", Map.of());
    }
}
