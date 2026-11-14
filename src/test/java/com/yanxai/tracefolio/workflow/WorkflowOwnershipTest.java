package com.yanxai.tracefolio.workflow;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanxai.tracefolio.config.TraceFolioProperties;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class WorkflowOwnershipTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final WorkflowRepository repository = new WorkflowRepository(
            new TraceFolioProperties("tenant-a", null, null, null, null, null), jdbc, new ObjectMapper());
    private final UUID foreignRun = UUID.randomUUID();

    @Test
    void cannotRecordDecisionBeforeCheckingRunTenant() {
        assertThatThrownBy(() -> repository.recordDecision(foreignRun,
                new WorkflowDecisionRequest("step", "APPROVED", "review", Map.of())))
                .isInstanceOf(ResponseStatusException.class);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void cannotAppendStepBeforeCheckingRunTenant() {
        assertThatThrownBy(() -> repository.addStep(foreignRun, 1, "step", "COMPLETED", Map.of(), Map.of(), null))
                .isInstanceOf(ResponseStatusException.class);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void cannotFinishRunOutsideConfiguredTenant() {
        assertThatThrownBy(() -> repository.complete(foreignRun, Map.of())).isInstanceOf(ResponseStatusException.class);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void ownedRunCanStillAppendStep() {
        when(jdbc.queryForObject(anyString(), eq(UUID.class), eq("tenant-a"), eq(foreignRun))).thenReturn(foreignRun);
        repository.addStep(foreignRun, 1, "step", "COMPLETED", Map.of(), Map.of(), null);
        verify(jdbc, times(2)).update(anyString(), any(Object[].class));
    }
}
