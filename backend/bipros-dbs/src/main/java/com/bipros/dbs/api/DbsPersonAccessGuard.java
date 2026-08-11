package com.bipros.dbs.api;

import com.bipros.common.security.ScopeKeys;
import com.bipros.common.security.ScopeResolverPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Row-guard for the DBS person surfaces. Round 3: the member set comes from the scope
 * resolver — OWN = self, TEAM = self + the project's Team-tab downline (transitive), so the
 * DBS follows the same configurable rule as every other person-scoped surface. PROJECT/ALL
 * see any person, unchanged.
 */
@Component("dbsPersonAccess")
@RequiredArgsConstructor
public class DbsPersonAccessGuard {

    private final ScopeResolverPort scopeResolver;

    /** Person pages + person-id query params: anyone in the caller's member set. */
    public boolean canViewPerson(UUID projectId, UUID targetUserId) {
        ScopeKeys keys = scopeResolver.resolveForProject(projectId);
        if (!keys.personScoped()) {
            return true;
        }
        return targetUserId != null && keys.memberIds().contains(targetUserId);
    }

    /** Nullable-person variant for optional query params: null = no person filter requested. */
    public boolean canViewPersonOrNull(UUID projectId, UUID targetUserId) {
        return targetUserId == null || canViewPerson(projectId, targetUserId);
    }

    /** Roster / comparison rows: id-when-present, else the member set's name aliases. */
    public boolean canViewRosterRow(UUID projectId, UUID rowUserId, String rowName) {
        ScopeKeys keys = scopeResolver.resolveForProject(projectId);
        if (!keys.personScoped()) {
            return true;
        }
        if (rowUserId != null) {
            return keys.memberIds().contains(rowUserId);
        }
        return rowName != null && keys.memberAliases().stream()
                .anyMatch(a -> a.equalsIgnoreCase(rowName.trim()));
    }

    /**
     * Exports: the PM-level workbook contains every person's sheets, so person-scoped callers
     * may never build it; the SUPERVISOR-level sheet follows the member-set rule.
     */
    public boolean canExport(UUID projectId, String level, UUID supervisorUserId) {
        ScopeKeys keys = scopeResolver.resolveForProject(projectId);
        if (!keys.personScoped()) {
            return true;
        }
        if (level != null && "SUPERVISOR".equalsIgnoreCase(level.trim())) {
            return supervisorUserId != null && keys.memberIds().contains(supervisorUserId);
        }
        return false;
    }
}
