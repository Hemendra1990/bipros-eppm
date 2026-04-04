package com.bipros.project.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.project.application.dto.CreateEpsNodeRequest;
import com.bipros.project.application.dto.EpsNodeResponse;
import com.bipros.project.application.dto.UpdateEpsNodeRequest;
import com.bipros.project.domain.model.EpsNode;
import com.bipros.project.domain.repository.EpsNodeRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class EpsService {

    private final EpsNodeRepository epsNodeRepository;
    private final ProjectRepository projectRepository;

    public EpsNodeResponse createNode(CreateEpsNodeRequest request) {
        log.info("Creating EPS node with code: {}", request.code());

        if (epsNodeRepository.existsByCode(request.code())) {
            throw new BusinessRuleException("EPS_CODE_DUPLICATE", "EPS node with code '" + request.code() + "' already exists");
        }

        EpsNode node = new EpsNode();
        node.setCode(request.code());
        node.setName(request.name());
        node.setParentId(request.parentId());
        node.setObsId(request.obsId());
        node.setSortOrder(0);

        EpsNode saved = epsNodeRepository.save(node);
        log.info("EPS node created with ID: {}", saved.getId());

        return buildNodeResponse(saved, new HashMap<>());
    }

    public EpsNodeResponse updateNode(UUID id, UpdateEpsNodeRequest request) {
        log.info("Updating EPS node: {}", id);

        EpsNode node = epsNodeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("EpsNode", id));

        node.setName(request.name());
        node.setObsId(request.obsId());
        if (request.sortOrder() != null) {
            node.setSortOrder(request.sortOrder());
        }

        EpsNode updated = epsNodeRepository.save(node);
        log.info("EPS node updated: {}", id);

        return buildNodeResponse(updated, new HashMap<>());
    }

    public void deleteNode(UUID id) {
        log.info("Deleting EPS node: {}", id);

        EpsNode node = epsNodeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("EpsNode", id));

        // Check for child nodes
        List<EpsNode> children = epsNodeRepository.findByParentIdOrderBySortOrder(id);
        if (!children.isEmpty()) {
            throw new BusinessRuleException("EPS_HAS_CHILDREN", "Cannot delete EPS node with child nodes");
        }

        // Check for projects under this EPS node
        long projectCount = projectRepository.findByEpsNodeId(id).size();
        if (projectCount > 0) {
            throw new BusinessRuleException("EPS_HAS_PROJECTS", "Cannot delete EPS node with existing projects");
        }

        epsNodeRepository.delete(node);
        log.info("EPS node deleted: {}", id);
    }

    public List<EpsNodeResponse> getTree() {
        log.info("Fetching EPS tree");

        List<EpsNode> allNodes = epsNodeRepository.findAll();
        Map<UUID, EpsNode> nodeMap = allNodes.stream()
            .collect(Collectors.toMap(EpsNode::getId, n -> n));

        List<EpsNode> rootNodes = epsNodeRepository.findByParentIdIsNullOrderBySortOrder();
        return rootNodes.stream()
            .map(node -> buildNodeResponse(node, nodeMap))
            .collect(Collectors.toList());
    }

    public EpsNodeResponse getNode(UUID id) {
        log.info("Fetching EPS node: {}", id);

        EpsNode node = epsNodeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("EpsNode", id));

        List<EpsNode> allNodes = epsNodeRepository.findAll();
        Map<UUID, EpsNode> nodeMap = allNodes.stream()
            .collect(Collectors.toMap(EpsNode::getId, n -> n));

        return buildNodeResponse(node, nodeMap);
    }

    private EpsNodeResponse buildNodeResponse(EpsNode node, Map<UUID, EpsNode> nodeMap) {
        List<EpsNode> children = epsNodeRepository.findByParentIdOrderBySortOrder(node.getId());
        List<EpsNodeResponse> childResponses = children.stream()
            .map(child -> buildNodeResponse(child, nodeMap))
            .collect(Collectors.toList());

        return new EpsNodeResponse(
            node.getId(),
            node.getCode(),
            node.getName(),
            node.getParentId(),
            node.getObsId(),
            node.getSortOrder(),
            childResponses
        );
    }
}
