package com.yanxai.tracefolio.document;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.Connection;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class KnowledgeRepositoryTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final KnowledgeRepository repository = spy(new KnowledgeRepository(jdbc, new ObjectMapper()));
    private final UUID id = UUID.randomUUID();
    private final ChunkDraft chunk = new ChunkDraft(0, "Doc", "A", "body", "L1", Map.of());

    @Test
    void identicalReimportRetainsChunkIdsAndDependentClaimsAfterLocking() throws Exception {
        doReturn(id).when(repository).upsertDocument(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
        when(jdbc.queryForObject(contains("FOR UPDATE"), eq(UUID.class), eq(id))).thenReturn(id);
        when(jdbc.query(contains("SELECT chunk_index"), any(RowMapper.class), eq(id))).thenAnswer(invocation -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getInt("chunk_index")).thenReturn(0);
            when(rs.getString("content_hash")).thenReturn("hash");
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });
        repository.storeDocument("tenant", "MARKDOWN", "Doc", "uri", "doc-hash", Map.of(),
                List.of(chunk), List.of("hash"), List.of(new float[]{1}));
        verify(repository, never()).deleteChunks(any());
        verify(repository, never()).insertChunk(any(), anyString(), anyString(), anyString(), any(), anyString(), any());
        InOrder order = inOrder(jdbc);
        order.verify(jdbc).queryForObject(contains("FOR UPDATE"), eq(UUID.class), eq(id));
        order.verify(jdbc).query(contains("SELECT chunk_index"), any(RowMapper.class), eq(id));
    }

    @Test
    void replacementFailureEscapesTheTransactionalBoundary() throws Exception {
        doReturn(id).when(repository).upsertDocument(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
        when(jdbc.query(contains("SELECT chunk_index"), any(RowMapper.class), eq(id))).thenReturn(List.of());
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            throw new IllegalStateException("insert failed");
        }).when(repository).insertChunk(any(), anyString(), anyString(), anyString(), any(), anyString(), any());
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        ProxyFactory factory = new ProxyFactory(repository);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),
                new AnnotationTransactionAttributeSource()));
        KnowledgeRepository transactional = (KnowledgeRepository) factory.getProxy();
        assertThatThrownBy(() -> transactional.storeDocument("tenant", "MARKDOWN", "Doc", "uri", "doc-hash", Map.of(),
                List.of(chunk), List.of("hash"), List.of(new float[]{1}))).hasMessage("insert failed");
        verify(connection).rollback();
        verify(connection, never()).commit();
    }
}
