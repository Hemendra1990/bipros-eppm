package com.bipros.project.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class WbsBudgetService {

    private final WbsNodeRepository wbsNodeRepository;
    private final ProjectRepository projectRepository;

    public WbsBudgetSummary getBudgetSummary(UUID projectId) {
        log.info("Computing WBS budget summary for project: {}", projectId);

        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }

        List<WbsNode> allNodes = wbsNodeRepository.findByProjectIdOrderBySortOrder(projectId);

        if (allNodes.isEmpty()) {
            return new WbsBudgetSummary(BigDecimal.ZERO, Collections.emptyList());
        }

        // Build parent -> children map
        Map<UUID, List<WbsNode>> childrenMap = allNodes.stream()
            .filter(n -> n.getParentId() != null)
            .collect(Collectors.groupingBy(WbsNode::getParentId));

        // Compute children budget rollup for each node
        Map<UUID, BigDecimal> childrenBudgetMap = new HashMap<>();
        for (WbsNode node : allNodes) {
            BigDecimal childrenBudget = sumChildrenBudget(node.getId(), childrenMap, allNodes);
            childrenBudgetMap.put(node.getId(), childrenBudget);
        }

        BigDecimal totalBudget = allNodes.stream()
            .filter(n -> n.getParentId() == null)
            .map(n -> n.getBudgetCrores() != null ? n.getBudgetCrores() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<WbsBudgetNode> nodes = allNodes.stream()
            .map(node -> {
                BigDecimal nodeBudget = node.getBudgetCrores() != null ? node.getBudgetCrores() : BigDecimal.ZERO;
                BigDecimal childrenBudget = childrenBudgetMap.getOrDefault(node.getId(), BigDecimal.ZERO);
                BigDecimal unallocated = nodeBudget.subtract(childrenBudget);
                boolean warning = childrenBudget.compareTo(nodeBudget) > 0;

                return new WbsBudgetNode(
                    node.getId(),
                    node.getCode(),
                    node.getName(),
                    node.getWbsLevel(),
                    nodeBudget,
                    childrenBudget,
                    unallocated,
                    warning
                );
            })
            .collect(Collectors.toList());

        return new WbsBudgetSummary(totalBudget, nodes);
    }

    private BigDecimal sumChildrenBudget(UUID nodeId, Map<UUID, List<WbsNode>> childrenMap, List<WbsNode> allNodes) {
        List<WbsNode> directChildren = childrenMap.getOrDefault(nodeId, Collections.emptyList());
        BigDecimal sum = BigDecimal.ZERO;
        for (WbsNode child : directChildren) {
            BigDecimal childBudget = child.getBudgetCrores() != null ? child.getBudgetCrores() : BigDecimal.ZERO;
            sum = sum.add(childBudget).add(sumChildrenBudget(child.getId(), childrenMap, allNodes));
        }
        return sum;
    }

    public record WbsBudgetSummary(
        BigDecimal totalBudgetCrores,
        List<WbsBudgetNode> nodes
    ) {
    }

    public record WbsBudgetNode(
        UUID wbsNodeId,
        String code,
        String name,
        Integer wbsLevel,
        BigDecimal budgetCrores,
        BigDecimal childrenBudgetCrores,
        BigDecimal unallocatedCrores,
        boolean warning
    ) {
    }
}
