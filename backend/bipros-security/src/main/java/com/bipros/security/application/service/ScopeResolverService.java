package com.bipros.security.application.service;

import com.bipros.common.security.DataScope;
import com.bipros.common.security.ScopeKeys;
import com.bipros.common.security.ScopeResolverPort;
import com.bipros.security.domain.model.Profile;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Gate-3 scope resolution (access-control round, 2026-08-11). One in-memory read of the
 * already-loaded user per call — no extra queries beyond the profile row the permission
 * path loads anyway.
 *
 * <p>Honours the sacred bypass contract: system context and ADMIN are {@link ScopeKeys#all()},
 * mirroring {@code ProjectAccessService}'s null-sentinel semantics.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ScopeResolverService implements ScopeResolverPort {

    private final CurrentUserService currentUserService;
    private final ProfileRepository profileRepository;
    private final com.bipros.project.domain.repository.ProjectTeamRepository projectTeamRepository;
    private final com.bipros.security.domain.repository.UserRepository userRepository;

    @Override
    public ScopeKeys resolveForCurrentUser() {
        if (currentUserService.isSystemContext() || currentUserService.isAdmin()) {
            return ScopeKeys.all();
        }
        Optional<User> current = currentUserService.getCurrentUser();
        if (current.isEmpty()) {
            // Anonymous/unknown principal: endpoints require authentication anyway; PROJECT with
            // no user id means an OWN predicate can never match — fail-closed, not fail-open.
            return new ScopeKeys(DataScope.PROJECT, null, Set.of());
        }
        User user = current.get();
        return new ScopeKeys(scopeOf(user), user.getId(), aliasesOf(user));
    }

    /**
     * Project-aware resolution (TEAM scope, review round 3): the member set is the caller plus
     * everyone below them on THIS project's Team tab, walked transitively down the reports-to
     * edges — role-agnostic, so any future hierarchy tier works by configuration alone. One
     * team query + one users query per call; OWN/PROJECT/ALL take the cheap path.
     */
    @Override
    public ScopeKeys resolveForProject(java.util.UUID projectId) {
        ScopeKeys base = resolveForCurrentUser();
        if (base.scope() != DataScope.TEAM || projectId == null || base.userId() == null) {
            return base;
        }
        // BFS DOWN the reports-to edges: child -> parent rows, so build parent -> children.
        java.util.Map<java.util.UUID, Set<java.util.UUID>> children = new java.util.HashMap<>();
        for (var member : projectTeamRepository.findByProjectId(projectId)) {
            if (member.getUserId() != null && member.getReportsToUserId() != null) {
                children.computeIfAbsent(member.getReportsToUserId(), k -> new java.util.HashSet<>())
                        .add(member.getUserId());
            }
        }
        Set<java.util.UUID> memberIds = new java.util.HashSet<>();
        java.util.Deque<java.util.UUID> queue = new java.util.ArrayDeque<>();
        queue.add(base.userId());
        while (!queue.isEmpty()) {
            java.util.UUID cursor = queue.poll();
            if (!memberIds.add(cursor)) continue;
            queue.addAll(children.getOrDefault(cursor, Set.of()));
        }
        Set<String> memberAliases = new LinkedHashSet<>(base.nameAliases());
        for (User member : userRepository.findAllById(memberIds)) {
            memberAliases.addAll(aliasesOf(member));
        }
        return new ScopeKeys(DataScope.TEAM, base.userId(), base.nameAliases(),
                Set.copyOf(memberIds), Set.copyOf(memberAliases));
    }

    private DataScope scopeOf(User user) {
        if (user.getProfileId() == null) {
            return DataScope.PROJECT;
        }
        return profileRepository.findById(user.getProfileId())
                .map(Profile::dataScopeOrDefault)
                .orElse(DataScope.PROJECT);
    }

    /** Username + "First Last" — the id-else-name keys legacy free-text supervisor rows need. */
    private static Set<String> aliasesOf(User user) {
        Set<String> aliases = new LinkedHashSet<>();
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            aliases.add(user.getUsername().trim());
        }
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String full = (first + " " + last).trim();
        if (!full.isBlank()) {
            aliases.add(full);
        }
        return Set.copyOf(aliases);
    }
}
