package com.bipros.security.application.service;

import com.bipros.project.domain.model.ObsNode;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ObsNodeRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.security.domain.model.AccessLevel;
import com.bipros.security.domain.model.UserObsAssignment;
import com.bipros.security.domain.repository.UserObsAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ObsSecurityService {

    private final UserObsAssignmentRepository assignmentRepository;
    private final ObsNodeRepository obsNodeRepository;
    private final ProjectRepository projectRepository;

    /**
     * Check if user has the required access level to a project's OBS node.
     * Walks up the OBS hierarchy to check parent nodes if direct assignment not found.
     *
     * @param userId the user ID
     * @param projectId the project ID
     * @param required the minimum required access level
     * @return true if user has access, false otherwise
     */
    public boolean hasAccess(UUID userId, UUID projectId, AccessLevel required) {
        if (userId == null || projectId == null) {
            return false;
        }

        Project project = getProjectById(projectId);
        if (project == null || project.getObsNodeId() == null) {
            log.warn("Project not found or has no OBS node assigned: projectId={}", projectId);
            return false;
        }

        return hasAccessToObs(userId, project.getObsNodeId(), required);
    }

    /**
     * Check if user has access to an OBS node (walks up hierarchy).
     */
    private boolean hasAccessToObs(UUID userId, UUID obsNodeId, AccessLevel required) {
        UUID currentNodeId = obsNodeId;
        int maxDepth = 100;
        int depth = 0;

        while (currentNodeId != null && depth < maxDepth) {
            var assignment = assignmentRepository.findByUserIdAndObsNodeId(userId, currentNodeId);
            if (assignment.isPresent()) {
                return meetsAccessLevel(assignment.get().getAccessLevel(), required);
            }

            ObsNode obsNode = getObsNodeById(currentNodeId);
            if (obsNode == null) {
                break;
            }
            currentNodeId = obsNode.getParentId();
            depth++;
        }

        return false;
    }

    /**
     * Get all project IDs accessible to a user.
     * Returns projects whose OBS nodes (or parent nodes) are assigned to the user.
     *
     * @param userId the user ID
     * @return list of accessible project IDs
     */
    @Transactional(readOnly = true)
    public List<UUID> getAccessibleProjectIds(UUID userId) {
        if (userId == null) {
            return List.of();
        }

        List<UserObsAssignment> assignments = assignmentRepository.findByUserId(userId);
        if (assignments.isEmpty()) {
            return List.of();
        }

        Set<UUID> accessibleNodeIds = new HashSet<>();
        for (UserObsAssignment assignment : assignments) {
            accessibleNodeIds.add(assignment.getObsNodeId());
            addDescendantNodes(assignment.getObsNodeId(), accessibleNodeIds);
        }

        return getProjectsByObsNodeIds(accessibleNodeIds);
    }

    /**
     * Assign a user to an OBS node with a specific access level.
     * Creates or updates the assignment.
     *
     * @param userId the user ID
     * @param obsNodeId the OBS node ID
     * @param level the access level
     */
    public void assignUserToObs(UUID userId, UUID obsNodeId, AccessLevel level) {
        if (userId == null || obsNodeId == null || level == null) {
            throw new IllegalArgumentException("userId, obsNodeId, and level must not be null");
        }

        var existing = assignmentRepository.findByUserIdAndObsNodeId(userId, obsNodeId);
        if (existing.isPresent()) {
            UserObsAssignment assignment = existing.get();
            assignment.setAccessLevel(level);
            assignmentRepository.save(assignment);
            log.info("Updated OBS assignment: userId={}, obsNodeId={}, level={}", userId, obsNodeId, level);
        } else {
            UserObsAssignment assignment = new UserObsAssignment(userId, obsNodeId, level);
            assignmentRepository.save(assignment);
            log.info("Created OBS assignment: userId={}, obsNodeId={}, level={}", userId, obsNodeId, level);
        }
    }

    /**
     * Check if userLevel meets or exceeds the required level.
     * Access hierarchy: FULL > EDIT > SCHEDULE > VIEW
     */
    private boolean meetsAccessLevel(AccessLevel userLevel, AccessLevel required) {
        if (userLevel == null || required == null) {
            return false;
        }

        int userRank = getAccessRank(userLevel);
        int requiredRank = getAccessRank(required);
        return userRank >= requiredRank;
    }

    /**
     * Get numeric rank for access level comparison.
     * Higher rank = more access.
     */
    private int getAccessRank(AccessLevel level) {
        return switch (level) {
            case FULL -> 4;
            case EDIT -> 3;
            case SCHEDULE -> 2;
            case VIEW -> 1;
        };
    }

    /**
     * Get OBS node by ID.
     */
    private ObsNode getObsNodeById(UUID id) {
        return obsNodeRepository.findById(id).orElse(null);
    }

    /**
     * Get project by ID.
     */
    private Project getProjectById(UUID id) {
        return projectRepository.findById(id).orElse(null);
    }

    /**
     * Add all descendant OBS nodes to the set recursively.
     */
    private void addDescendantNodes(UUID parentId, Set<UUID> nodeIds) {
        List<ObsNode> children = obsNodeRepository.findByParentIdOrderBySortOrder(parentId);
        for (ObsNode child : children) {
            nodeIds.add(child.getId());
            addDescendantNodes(child.getId(), nodeIds);
        }
    }

    /**
     * Get project IDs for a set of OBS node IDs.
     */
    private List<UUID> getProjectsByObsNodeIds(Set<UUID> obsNodeIds) {
        List<UUID> projectIds = new ArrayList<>();
        List<Project> projects = projectRepository.findAll();
        for (Project project : projects) {
            if (project.getObsNodeId() != null && obsNodeIds.contains(project.getObsNodeId())) {
                projectIds.add(project.getId());
            }
        }
        return projectIds;
    }
}
