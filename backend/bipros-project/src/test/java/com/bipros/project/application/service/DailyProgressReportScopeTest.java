package com.bipros.project.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.security.DataScope;
import com.bipros.common.security.ScopeKeys;
import com.bipros.common.security.ScopeResolverPort;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gate-3 verification for DPR (access-control round, 2026-08-11): the OWN involvement
 * predicate is pushed into the repository queries, and a foreign record 404s at the
 * {@code find} choke point. "Correct-looking but wrong" coverage: these paths decide what a
 * supervisor can and cannot see.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DailyProgressReportService — gate-3 scoping")
class DailyProgressReportScopeTest {

    @Mock private DailyProgressReportRepository dprRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ScopeResolverPort scopeResolver;
    @Mock private EntityManager em;
    @Mock private Query nativeQuery;
    @InjectMocks private DailyProgressReportService service;

    private final UUID projectId = UUID.randomUUID();
    private final UUID me = UUID.randomUUID();
    private final UUID myActivity = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "em", em);
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(em.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of(myActivity));
    }

    private void scope(DataScope s) {
        // Stub resolveForProject directly: on a mock the interface default (which would
        // delegate to resolveForCurrentUser) is not executed.
        when(scopeResolver.resolveForProject(projectId))
                .thenReturn(new ScopeKeys(s, me, Set.of("k.barman", "K. Barman")));
    }

    private final UUID teammate = UUID.randomUUID();

    private void teamScope() {
        when(scopeResolver.resolveForProject(projectId)).thenReturn(new ScopeKeys(
                DataScope.TEAM, me, Set.of("EMP-210"),
                Set.of(me, teammate), Set.of("EMP-210", "k.barman", "K. Barman")));
    }

    @Test
    @DisplayName("OWN listPaged pushes the involvement keys into the dates query")
    void ownListPagedPushesScopeIntoQuery() {
        scope(DataScope.OWN);
        when(dprRepository.findDistinctReportDatesDesc(any(), any(), any(), any(), any(), any(),
                any(), any(), anyBoolean(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        service.listPaged(projectId, null, null, null, null, 14, null, null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> aliases = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> activityIds = ArgumentCaptor.forClass(List.class);
        verify(dprRepository).findDistinctReportDatesDesc(eq(projectId), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(true), eq(Set.of(me)), aliases.capture(), activityIds.capture(),
                any(Pageable.class));
        assertThat(aliases.getValue()).containsExactlyInAnyOrder("k.barman", "k. barman");
        assertThat(activityIds.getValue()).containsExactly(myActivity);
    }

    @Test
    @DisplayName("TEAM listPaged pushes the WHOLE member set into the dates query")
    void teamListPagedPushesMemberSet() {
        teamScope();
        when(dprRepository.findDistinctReportDatesDesc(any(), any(), any(), any(), any(), any(),
                any(), any(), anyBoolean(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        service.listPaged(projectId, null, null, null, null, 14, null, null, null);

        verify(dprRepository).findDistinctReportDatesDesc(eq(projectId), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(true), eq(Set.of(me, teammate)), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("PROJECT listPaged passes scoped=false with the no-op sentinels")
    void projectListPagedIsUnscoped() {
        scope(DataScope.PROJECT);
        when(dprRepository.findDistinctReportDatesDesc(any(), any(), any(), any(), any(), any(),
                any(), any(), anyBoolean(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        service.listPaged(projectId, null, null, null, null, 14, null, null, null);

        verify(dprRepository).findDistinctReportDatesDesc(eq(projectId), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(false), eq(List.of(new UUID(0L, 0L))), eq(List.of("")),
                eq(List.of(new UUID(0L, 0L))), any(Pageable.class));
    }

    @Test
    @DisplayName("OWN list() routes to the scoped query instead of the project-wide branch")
    void ownListRoutesToScopedQuery() {
        scope(DataScope.OWN);
        when(dprRepository.findScopedList(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.list(projectId, null, null, null);

        verify(dprRepository).findScopedList(eq(projectId), isNull(), isNull(), isNull(),
                eq(Set.of(me)), any(), eq(List.of(myActivity)));
    }

    @Test
    @DisplayName("OWN caller opening a foreign DPR by URL gets 404, not the record")
    void ownForeignRecordIs404() {
        scope(DataScope.OWN);
        DailyProgressReport foreign = new DailyProgressReport();
        ReflectionTestUtils.setField(foreign, "id", UUID.randomUUID());
        foreign.setProjectId(projectId);
        foreign.setSupervisorUserId(UUID.randomUUID());     // someone else's
        foreign.setSupervisorName("Someone Else");
        foreign.setActivityId(UUID.randomUUID());           // not my activity
        when(dprRepository.findById(any())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.get(projectId, foreign.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("legacy free-text row (null supervisor id) matches by name alias")
    void legacyNameAliasRowIsVisible() {
        scope(DataScope.OWN);
        DailyProgressReport legacy = new DailyProgressReport();
        ReflectionTestUtils.setField(legacy, "id", UUID.randomUUID());
        legacy.setProjectId(projectId);
        legacy.setSupervisorUserId(null);                   // imported row: free text only
        legacy.setSupervisorName(" K. Barman ");
        when(dprRepository.findById(any())).thenReturn(Optional.of(legacy));

        // find() must NOT throw — reaching response hydration proves the row was visible.
        assertThatThrownBy(() -> service.get(projectId, legacy.getId()))
                .isNotInstanceOf(ResourceNotFoundException.class);
    }
}
