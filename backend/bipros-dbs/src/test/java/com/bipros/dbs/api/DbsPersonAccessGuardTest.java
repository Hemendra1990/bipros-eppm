package com.bipros.dbs.api;

import com.bipros.common.security.DataScope;
import com.bipros.common.security.ScopeKeys;
import com.bipros.common.security.ScopeResolverPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Member-set rule for the DBS person pages (TEAM round, 2026-08-11): the guard trusts the
 * resolver's member set — OWN = self, TEAM = self + Team-tab downline (the transitive walk
 * itself is pinned in ScopeResolverServiceTest). PROJECT/ALL see any person, unchanged.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DbsPersonAccessGuard — resolver member-set rule")
class DbsPersonAccessGuardTest {

    @Mock private ScopeResolverPort scopeResolver;
    @InjectMocks private DbsPersonAccessGuard guard;

    private final UUID projectId = UUID.randomUUID();
    private final UUID me = UUID.randomUUID();
    private final UUID teammate = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private void own() {
        when(scopeResolver.resolveForProject(projectId))
                .thenReturn(new ScopeKeys(DataScope.OWN, me, Set.of("me")));
    }

    private void team() {
        when(scopeResolver.resolveForProject(projectId)).thenReturn(new ScopeKeys(
                DataScope.TEAM, me, Set.of("me"), Set.of(me, teammate), Set.of("me", "teammate")));
    }

    @Test
    @DisplayName("PROJECT scope sees any person (unchanged management behaviour)")
    void projectScopeSeesAnyone() {
        when(scopeResolver.resolveForProject(projectId))
                .thenReturn(new ScopeKeys(DataScope.PROJECT, me, Set.of("me")));
        assertThat(guard.canViewPerson(projectId, other)).isTrue();
    }

    @Test
    @DisplayName("OWN sees their own page and nobody else")
    void ownSeesSelfOnly() {
        own();
        assertThat(guard.canViewPerson(projectId, me)).isTrue();
        assertThat(guard.canViewPerson(projectId, other)).isFalse();
    }

    @Test
    @DisplayName("TEAM sees the resolver's member set, not outsiders")
    void teamSeesMemberSet() {
        team();
        assertThat(guard.canViewPerson(projectId, me)).isTrue();
        assertThat(guard.canViewPerson(projectId, teammate)).isTrue();
        assertThat(guard.canViewPerson(projectId, other)).isFalse();
    }

    @Test
    @DisplayName("null person param means 'no filter requested' and passes")
    void nullPersonPasses() {
        own();
        assertThat(guard.canViewPersonOrNull(projectId, null)).isTrue();
        assertThat(guard.canViewPersonOrNull(projectId, other)).isFalse();
    }

    @Test
    @DisplayName("roster row: id wins when present; legacy free-text name matches member aliases")
    void rosterIdElseName() {
        team();
        assertThat(guard.canViewRosterRow(projectId, teammate, "someone else")).isTrue();
        assertThat(guard.canViewRosterRow(projectId, other, "me")).isFalse();
        assertThat(guard.canViewRosterRow(projectId, null, " Teammate ")).isTrue();
        assertThat(guard.canViewRosterRow(projectId, null, "someone else")).isFalse();
    }

    @Test
    @DisplayName("person-scoped caller may never request the project-wide register (null cmUserId denies)")
    void registerRules() {
        own();
        assertThat(guard.canViewRegister(projectId, null)).isFalse();
        assertThat(guard.canViewRegister(projectId, other)).isFalse();
        assertThat(guard.canViewRegister(projectId, me)).isTrue();
    }

    @Test
    @DisplayName("PROJECT scope gets the project-wide register, with or without a CM filter")
    void registerProjectScope() {
        when(scopeResolver.resolveForProject(projectId))
                .thenReturn(new ScopeKeys(DataScope.PROJECT, me, Set.of("me")));
        assertThat(guard.canViewRegister(projectId, null)).isTrue();
        assertThat(guard.canViewRegister(projectId, other)).isTrue();
    }

    @Test
    @DisplayName("person-scoped caller may export only a member's supervisor sheet, never the PM workbook")
    void exportRules() {
        team();
        assertThat(guard.canExport(projectId, "SUPERVISOR", me)).isTrue();
        assertThat(guard.canExport(projectId, "SUPERVISOR", teammate)).isTrue();
        assertThat(guard.canExport(projectId, "SUPERVISOR", other)).isFalse();
        assertThat(guard.canExport(projectId, "PM", null)).isFalse();
        assertThat(guard.canExport(projectId, null, null)).isFalse();
    }
}
