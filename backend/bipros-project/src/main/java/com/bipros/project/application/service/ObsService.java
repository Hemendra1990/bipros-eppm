package com.bipros.project.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.project.application.dto.CreateEpsNodeRequest;
import com.bipros.project.application.dto.EpsNodeResponse;
import com.bipros.project.application.dto.UpdateEpsNodeRequest;
import com.bipros.project.domain.model.ObsNode;
import com.bipros.project.domain.repository.ObsNodeRepository;
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
public class ObsService {

    private final ObsNodeRepository obsNodeRepository;

    public EpsNodeResponse createNode(CreateEpsNodeRequest request) {
        log.info("Creating OBS node with code: {}", request.code());

        if (obsNodeRepository.existsByCode(request.code())) {
            throw new BusinessRuleException("OBS_CODE_DUPLICATE", "OBS node with code '" + request.code() + "' already exists");
        }

        ObsNode node = new ObsNode();
        node.setCode(request.code());
        node.setName(request.name());
        node.setParentId(request.parentId());
        node.setSortOrder(0);

        ObsNode saved = obsNodeRepository.save(node);
        log.info("OBS node created with ID: {}", saved.getId());

        return buildNodeResponse(saved, new HashMap<>());
    }

    public EpsNodeResponse updateNode(UUID id, UpdateEpsNodeRequest request) {
        log.info("Updating OBS node: {}", id);

        ObsNode node = obsNodeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ObsNode", id));

        node.setName(request.name());
        if (request.sortOrder() != null) {
            node.setSortOrder(request.sortOrder());
        }

        ObsNode updated = obsNodeRepository.save(node);
        log.info("OBS node updated: {}", id);

        return buildNodeResponse(updated, new HashMap<>());
    }

    public void deleteNode(UUID id) {
        log.info("Deleting OBS node: {}", id);

        ObsNode node = obsNodeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ObsNode", id));

        // Check for child nodes
        List<ObsNode> children = obsNodeRepository.findByParentIdOrderBySortOrder(id);
        if (!children.isEmpty()) {
            throw new BusinessRuleException("OBS_HAS_CHILDREN", "Cannot delete OBS node with child nodes");
        }

        obsNodeRepository.delete(node);
        log.info("OBS node deleted: {}", id);
    }

    public List<EpsNodeResponse> getTree() {
        log.info("Fetching OBS tree");

        List<ObsNode> allNodes = obsNodeRepository.findAll();
        Map<UUID, ObsNode> nodeMap = allNodes.stream()
            .collect(Collectors.toMap(ObsNode::getId, n -> n));

        List<ObsNode> rootNodes = obsNodeRepository.findByParentIdIsNullOrderBySortOrder();
        return rootNodes.stream()
            .map(node -> buildNodeResponse(node, nodeMap))
            .collect(Collectors.toList());
    }

    public EpsNodeResponse getNode(UUID id) {
        log.info("Fetching OBS node: {}", id);

        ObsNode node = obsNodeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ObsNode", id));

        List<ObsNode> allNodes = obsNodeRepository.findAll();
        Map<UUID, ObsNode> nodeMap = allNodes.stream()
            .collect(Collectors.toMap(ObsNode::getId, n -> n));

        return buildNodeResponse(node, nodeMap);
    }

    private EpsNodeResponse buildNodeResponse(ObsNode node, Map<UUID, ObsNode> nodeMap) {
        List<ObsNode> children = obsNodeRepository.findByParentIdOrderBySortOrder(node.getId());
        List<EpsNodeResponse> childResponses = children.stream()
            .map(child -> buildNodeResponse(child, nodeMap))
            .collect(Collectors.toList());

        return new EpsNodeResponse(
            node.getId(),
            node.getCode(),
            node.getName(),
            node.getParentId(),
            null,
            node.getSortOrder(),
            childResponses
        );
    }
}
