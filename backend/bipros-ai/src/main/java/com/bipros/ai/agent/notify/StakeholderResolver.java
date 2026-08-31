package com.bipros.ai.agent.notify;

import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.ai.agent.memory.AgentMemoryService;
import com.bipros.project.application.service.ProjectTeamService;
import com.bipros.project.domain.model.ProjectRole;
import com.bipros.project.domain.model.ProjectTeamMember;
import com.bipros.project.domain.repository.ProjectTeamRepository;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves the recipient users for a finding. A finding's {@code stakeholders} map holds role-key
 * strings (e.g. {@code PROJECT_MANAGER}, {@code SITE_MANAGER}) — usually with empty id lists, so the
 * roles are translated to {@link ProjectRole}s and resolved against the project's reporting line
 * ({@code project_team}). Any explicit user ids in the map are included directly. When nothing
 * resolves, the project PM is the fallback (mirrors {@code DprNotificationRecipientResolver}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StakeholderResolver {

    private final ProjectTeamRepository projectTeamRepository;
    private final ProjectTeamService projectTeamService;
    private final UserRepository userRepository;
    private final AgentMemoryService memoryService;

    /** A resolved recipient with the contact points needed by each channel. */
    public record Recipient(UUID userId, String name, String email, String phone) {
    }

    public List<Recipient> resolve(AgentFinding finding) {
        if (finding == null) {
            return List.of();
        }
        UUID projectId = finding.getProjectId();
        Set<UUID> userIds = new LinkedHashSet<>();

        Map<String, List<UUID>> stakeholders = memoryService.readStakeholders(finding);
        for (Map.Entry<String, List<UUID>> e : stakeholders.entrySet()) {
            if (e.getValue() != null) {
                e.getValue().stream().filter(Objects::nonNull).forEach(userIds::add);
            }
            ProjectRole role = mapRole(e.getKey());
            if (role != null && projectId != null) {
                for (ProjectTeamMember m : projectTeamRepository.findByProjectIdAndRole(projectId, role)) {
                    if (m.getUserId() != null) {
                        userIds.add(m.getUserId());
                    }
                }
            }
        }

        if (userIds.isEmpty() && projectId != null) {
            projectTeamService.resolvePmFor(projectId).ifPresent(userIds::add);
        }

        List<Recipient> out = new ArrayList<>(userIds.size());
        for (UUID uid : userIds) {
            User u = userRepository.findById(uid).orElse(null);
            if (u != null) {
                out.add(new Recipient(uid, displayName(u), u.getEmail(), u.getMobile()));
            } else {
                // Keep the user id — the in-app channel can still deliver even without contact details.
                out.add(new Recipient(uid, null, null, null));
            }
        }
        return out;
    }

    /**
     * The user's direct manager on this project's reporting line (one {@code reportsTo} edge in
     * {@code project_team}) — for responsible-person routing. Owner decision 2026-08-05: the
     * subject of a finding is never notified; their manager is (put the returned id under the
     * unmapped {@code "RESPONSIBLE"} stakeholder key so no role fan-out is triggered).
     */
    public java.util.Optional<UUID> managerOf(UUID projectId, UUID userId) {
        if (projectId == null || userId == null) {
            return java.util.Optional.empty();
        }
        return projectTeamService.getImmediateReporter(projectId, userId)
                .filter(mgr -> !mgr.equals(userId));
    }

    /**
     * Stakeholder map for responsible-person routing: PM role key always; the given users as
     * explicit recipients under the unmapped {@code RESPONSIBLE} key (no role fan-out); when
     * none resolve, the SITE_MANAGER seats as the fallback audience.
     */
    public Map<String, List<UUID>> pmPlusResponsible(UUID projectId, java.util.Collection<UUID> responsibleUserIds) {
        Map<String, List<UUID>> out = new java.util.LinkedHashMap<>();
        out.put("PROJECT_MANAGER", List.of());
        List<UUID> ids = responsibleUserIds == null ? List.of() : responsibleUserIds.stream()
                .filter(Objects::nonNull).distinct().toList();
        if (!ids.isEmpty()) {
            out.put("RESPONSIBLE", ids);
        } else {
            out.put("SITE_MANAGER", List.of());
        }
        return out;
    }

    /**
     * Same as {@link #pmPlusResponsible} but the responsible recipients are the subjects'
     * direct MANAGERS — owner decision 2026-08-05: the person a finding criticises is never
     * notified themselves; their manager (+ PM) is.
     */
    public Map<String, List<UUID>> pmPlusManagersOf(UUID projectId, java.util.Collection<UUID> subjectUserIds) {
        List<UUID> managers = subjectUserIds == null ? List.of() : subjectUserIds.stream()
                .filter(Objects::nonNull).distinct()
                .map(id -> managerOf(projectId, id).orElse(null))
                .filter(Objects::nonNull)
                .toList();
        return pmPlusResponsible(projectId, managers);
    }

    /** Map a finding stakeholder role-key onto a {@link ProjectRole}; unknown keys resolve to null. */
    static ProjectRole mapRole(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return switch (key.trim().toUpperCase()) {
            case "PM", "PROJECT_MANAGER" -> ProjectRole.PM;
            case "CM", "CONSTRUCTION_MANAGER" -> ProjectRole.CONSTRUCTION_MANAGER;
            case "SM", "SITE_MANAGER" -> ProjectRole.SITE_MANAGER;
            case "ENGINEER" -> ProjectRole.ENGINEER;
            case "SUPERVISOR" -> ProjectRole.SUPERVISOR;
            case "QS" -> ProjectRole.QS;
            case "SAFETY" -> ProjectRole.SAFETY;

            // The seven keys the agents actually emit that previously resolved to nobody.
            // Counted across backend/bipros-ai/.../agent/impl: PLANNING_ENGINEER x17,
            // COST_CONTROLLER x5, QUALITY_MANAGER x2, HSE_MANAGER x2, DOCUMENT_CONTROLLER x2,
            // STORE_KEEPER x1, PORTFOLIO_MANAGER x1. Unmapped keys fell through to the PM
            // fallback, so every one of these findings was addressed to the PM alone.
            case "PLANNING_ENGINEER" -> ProjectRole.ENGINEER;
            case "COST_CONTROLLER", "PROJECT_CONTROL", "PROJECT_CONTROL_ENGINEER" -> ProjectRole.PROJECT_CONTROL;
            case "QUALITY_MANAGER", "QUALITY_ENGINEER", "QA_QC" -> ProjectRole.QUALITY_ENGINEER;
            case "HSE_MANAGER", "HSE" -> ProjectRole.SAFETY;
            case "DOCUMENT_CONTROLLER", "DESIGN_COORDINATOR" -> ProjectRole.DESIGN_COORDINATOR;
            case "STORE_KEEPER", "STOREKEEPER" -> ProjectRole.STORE_KEEPER;
            case "PORTFOLIO_MANAGER" -> ProjectRole.PM;
            default -> null;
        };
    }

    private static String displayName(User u) {
        String fn = u.getFirstName() == null ? "" : u.getFirstName();
        String ln = u.getLastName() == null ? "" : u.getLastName();
        String full = (fn + " " + ln).trim();
        return full.isBlank() ? u.getUsername() : full;
    }
}
