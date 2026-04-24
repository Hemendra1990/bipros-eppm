package com.bipros.project.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.dto.CreateWbsNodeRequest;
import com.bipros.project.application.dto.UpdateEpsNodeRequest;
import com.bipros.project.application.dto.WbsNodeResponse;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.model.WbsStatus;
import com.bipros.project.domain.model.WbsType;
import com.bipros.project.domain.repository.ProjectActivityCounter;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class WbsService {

    private final WbsNodeRepository wbsNodeRepository;
    private final ProjectRepository projectRepository;
    private final ProjectActivityCounter projectActivityCounter;
    private final AuditService auditService;

    public WbsNodeResponse createNode(CreateWbsNodeRequest request) {
        log.info("Creating WBS node with code: {}", request.code());

        if (!projectRepository.existsById(request.projectId())) {
            throw new ResourceNotFoundException("Project", request.projectId());
        }

        if (wbsNodeRepository.existsByCode(request.code())) {
            throw new BusinessRuleException("WBS_CODE_DUPLICATE",
                    "WBS node with code " + request.code() + " already exists");
        }

        WbsNode node = new WbsNode();
        node.setCode(request.code());
        node.setName(request.name());
        node.setParentId(request.parentId());
        node.setProjectId(request.projectId());
        node.setObsNodeId(request.obsNodeId());
        node.setSortOrder(0);

        // Derive level and apply sensible defaults so POSTing with only {code,name,projectId}
        // doesn't leave the node half-populated (BUG-016).
        Integer level = request.wbsLevel();
        if (level == null) {
            if (request.parentId() != null) {
                Integer parentLevel = wbsNodeRepository.findById(request.parentId())
                    .map(WbsNode::getWbsLevel).orElse(0);
                level = (parentLevel == null ? 0 : parentLevel) + 1;
            } else {
                level = 1;
            }
        }
        node.setWbsLevel(level);
        node.setWbsType(request.wbsType() != null ? request.wbsType()
            : (request.parentId() == null ? WbsType.NODE : WbsType.WORK_PACKAGE));
        node.setWbsStatus(request.wbsStatus() != null ? request.wbsStatus() : WbsStatus.NOT_STARTED);

        WbsNode saved = wbsNodeRepository.save(node);
        log.info("WBS node created with ID: {}", saved.getId());
        auditService.logCreate("WbsNode", saved.getId(), request);

        return buildNodeResponse(saved);
    }

    public WbsNodeResponse updateNode(UUID id, UpdateEpsNodeRequest request) {
        log.info("Updating WBS node: {}", id);

        WbsNode node = wbsNodeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("WbsNode", id));

        String oldName = node.getName();
        Integer oldSortOrder = node.getSortOrder();
        UUID oldParentId = node.getParentId();

        node.setName(request.name());
        if (request.sortOrder() != null) {
            node.setSortOrder(request.sortOrder());
        }

        if (request.parentId() != null) {
            if (request.parentId().equals(id)) {
                throw new BusinessRuleException("INVALID_PARENT",
                    "WBS node cannot be its own parent");
            }
            // Detect descendant cycles: walk up the proposed parent's ancestry; if we hit `id`,
            // the move would create a loop.
            UUID ancestor = request.parentId();
            int guard = 0;
            while (ancestor != null && guard++ < 1000) {
                if (ancestor.equals(id)) {
                    throw new BusinessRuleException("INVALID_PARENT",
                        "Moving this WBS node under the requested parent would create a cycle");
                }
                UUID next = wbsNodeRepository.findById(ancestor)
                    .map(WbsNode::getParentId).orElse(null);
                ancestor = next;
            }
            node.setParentId(request.parentId());
        }

        WbsNode updated = wbsNodeRepository.save(node);
        log.info("WBS node updated: {}", id);
        auditService.logUpdate("WbsNode", id, "name", oldName, updated.getName());
        auditService.logUpdate("WbsNode", id, "sortOrder", oldSortOrder, updated.getSortOrder());
        if (request.parentId() != null && !request.parentId().equals(oldParentId)) {
            auditService.logUpdate("WbsNode", id, "parentId", oldParentId, updated.getParentId());
        }

        return buildNodeResponse(updated);
    }

    public void deleteNode(UUID id) {
        log.info("Deleting WBS node: {}", id);

        WbsNode node = wbsNodeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("WbsNode", id));

        // Check for child nodes
        List<WbsNode> children = wbsNodeRepository.findByParentIdOrderBySortOrder(id);
        if (!children.isEmpty()) {
            throw new BusinessRuleException("WBS_HAS_CHILDREN", "Cannot delete WBS node with child nodes");
        }

        // Check if activities reference this WBS node
        long activityCount = projectActivityCounter.countActivitiesByWbsNodeId(id);
        if (activityCount > 0) {
            throw new BusinessRuleException("WBS_HAS_ACTIVITIES", "Cannot delete WBS node with existing activities");
        }

        wbsNodeRepository.delete(node);
        log.info("WBS node deleted: {}", id);
        auditService.logDelete("WbsNode", id);
    }

    public List<WbsNodeResponse> getTree(UUID projectId) {
        log.info("Fetching WBS tree for project: {}", projectId);

        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }

        List<WbsNode> rootNodes = wbsNodeRepository.findByProjectIdAndParentIdIsNullOrderBySortOrder(projectId);
        return rootNodes.stream()
            .map(this::buildNodeResponse)
            .collect(Collectors.toList());
    }

    public WbsNodeResponse getNode(UUID id) {
        log.info("Fetching WBS node: {}", id);

        WbsNode node = wbsNodeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("WbsNode", id));

        return buildNodeResponse(node);
    }

    private WbsNodeResponse buildNodeResponse(WbsNode node) {
        List<WbsNode> children = wbsNodeRepository.findByParentIdOrderBySortOrder(node.getId());
        List<WbsNodeResponse> childResponses = children.stream()
            .map(this::buildNodeResponse)
            .collect(Collectors.toList());

        return new WbsNodeResponse(
            node.getId(),
            node.getCode(),
            node.getName(),
            node.getParentId(),
            node.getProjectId(),
            node.getObsNodeId(),
            node.getSortOrder(),
            node.getSummaryDuration(),
            node.getSummaryPercentComplete(),
            node.getWbsLevel(),
            node.getWbsType(),
            node.getPhase(),
            node.getWbsStatus(),
            node.getResponsibleOrganisationId(),
            node.getPlannedStart(),
            node.getPlannedFinish(),
            node.getBudgetCrores(),
            node.getGisPolygonId(),
            node.getChainageFromM(),
            node.getChainageToM(),
            childResponses
        );
    }
}
