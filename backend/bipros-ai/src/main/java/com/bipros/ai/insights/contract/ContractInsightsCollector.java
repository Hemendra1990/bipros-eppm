package com.bipros.ai.insights.contract;

import com.bipros.ai.insights.InsightDataCollector;
import com.bipros.common.dto.PagedResponse;
import com.bipros.contract.application.dto.ContractResponse;
import com.bipros.contract.application.service.ContractService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ContractInsightsCollector implements InsightDataCollector {

    private final ContractService contractService;
    private final ObjectMapper objectMapper;

    @Override
    public JsonNode collect(UUID projectId) {
        Pageable pageable = PageRequest.of(0, 1000);
        PagedResponse<ContractResponse> page = contractService.listByProject(projectId, pageable);
        List<ContractResponse> contracts = page.content();

        long totalCount = contracts.size();
        BigDecimal totalValue = contracts.stream()
                .map(ContractResponse::contractValue)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRevisedValue = contracts.stream()
                .map(ContractResponse::revisedValue)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> statusBreakdown = contracts.stream()
                .filter(c -> c.status() != null)
                .collect(Collectors.groupingBy(c -> c.status().name(), Collectors.counting()));

        List<ContractResponse> valueVarianceContracts = contracts.stream()
                .filter(c -> c.revisedValue() != null && c.contractValue() != null)
                .filter(c -> c.revisedValue().compareTo(c.contractValue()) > 0)
                .sorted((a, b) -> b.revisedValue().compareTo(a.revisedValue()))
                .limit(5)
                .toList();

        LocalDate today = LocalDate.now();
        List<ContractResponse> nearDlpOrExpiredBg = contracts.stream()
                .filter(c -> isNearDlp(c, today) || isExpiredOrNearBg(c, today))
                .toList();

        ObjectNode root = objectMapper.createObjectNode();
        root.put("totalContractCount", totalCount);
        root.put("totalContractValue", totalValue.doubleValue());
        root.put("totalRevisedValue", totalRevisedValue.doubleValue());

        ObjectNode statusNode = root.putObject("statusBreakdown");
        statusBreakdown.forEach(statusNode::put);

        ArrayNode varianceArray = root.putArray("contractsWithRevisedValueIncrease");
        for (ContractResponse c : valueVarianceContracts) {
            ObjectNode node = varianceArray.addObject();
            node.put("contractNumber", c.contractNumber());
            node.put("contractorName", c.contractorName());
            node.put("contractValue", c.contractValue() != null ? c.contractValue().doubleValue() : null);
            node.put("revisedValue", c.revisedValue() != null ? c.revisedValue().doubleValue() : null);
            node.put("status", c.status() != null ? c.status().name() : null);
            node.put("spi", c.spi() != null ? c.spi().doubleValue() : null);
            node.put("cpi", c.cpi() != null ? c.cpi().doubleValue() : null);
            node.put("performanceScore", c.performanceScore() != null ? c.performanceScore().doubleValue() : null);
        }

        ArrayNode dlpBgArray = root.putArray("contractsNearDlpOrExpiredBg");
        for (ContractResponse c : nearDlpOrExpiredBg) {
            ObjectNode node = dlpBgArray.addObject();
            node.put("contractNumber", c.contractNumber());
            node.put("contractorName", c.contractorName());
            node.put("status", c.status() != null ? c.status().name() : null);
            node.put("bgExpiry", c.bgExpiry() != null ? c.bgExpiry().toString() : null);
            LocalDate dlpEnd = computeDlpEnd(c);
            node.put("dlpEndDate", dlpEnd != null ? dlpEnd.toString() : null);
        }

        return root;
    }

    @Override
    public String tabKey() {
        return "contracts";
    }

    @Override
    public String promptInstructions() {
        return "Produce insights to improve contract performance and commercial oversight. "
                + "Focus on delayed contracts, value variance, status distribution, DLP expiry risks, and performance score trends.";
    }

    private boolean isNearDlp(ContractResponse c, LocalDate today) {
        LocalDate dlpEnd = computeDlpEnd(c);
        if (dlpEnd == null) {
            return false;
        }
        return dlpEnd.isBefore(today.plusMonths(3)) || dlpEnd.isBefore(today);
    }

    private boolean isExpiredOrNearBg(ContractResponse c, LocalDate today) {
        if (c.bgExpiry() == null) {
            return false;
        }
        return c.bgExpiry().isBefore(today.plusMonths(3));
    }

    private LocalDate computeDlpEnd(ContractResponse c) {
        LocalDate baseDate = c.actualCompletionDate();
        if (baseDate == null) {
            baseDate = c.revisedCompletionDate();
        }
        if (baseDate == null) {
            baseDate = c.completionDate();
        }
        if (baseDate == null || c.dlpMonths() == null) {
            return null;
        }
        return baseDate.plusMonths(c.dlpMonths());
    }
}
