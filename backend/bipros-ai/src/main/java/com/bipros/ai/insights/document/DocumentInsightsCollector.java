package com.bipros.ai.insights.document;

import com.bipros.ai.insights.InsightDataCollector;
import com.bipros.ai.insights.charts.EChartsOptions;
import com.bipros.ai.insights.dto.ChartSpec;
import com.bipros.document.application.dto.DocumentResponse;
import com.bipros.document.application.service.DocumentService;
import com.bipros.document.domain.model.DocumentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DocumentInsightsCollector implements InsightDataCollector {

    private static final long STALE_DRAFT_DAYS = 14;
    private static final long REVIEW_PENDING_DAYS = 7;

    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    @Override
    public JsonNode collect(UUID projectId) {
        List<DocumentResponse> documents = documentService.listDocuments(projectId);
        Instant now = Instant.now();

        long totalCount = documents.size();

        Map<String, Long> statusBreakdown = documents.stream()
                .filter(d -> d.status() != null)
                .collect(Collectors.groupingBy(d -> d.status().name(), Collectors.counting()));

        Map<String, Long> typeBreakdown = documents.stream()
                .filter(d -> d.documentType() != null)
                .collect(Collectors.groupingBy(d -> d.documentType().name(), Collectors.counting()));

        Map<String, Long> disciplineBreakdown = documents.stream()
                .filter(d -> d.discipline() != null)
                .collect(Collectors.groupingBy(d -> d.discipline().name(), Collectors.counting()));

        List<DocumentResponse> staleDrafts = documents.stream()
                .filter(d -> d.status() == DocumentStatus.DRAFT)
                .filter(d -> d.updatedAt() != null
                        && Duration.between(d.updatedAt(), now).toDays() >= STALE_DRAFT_DAYS)
                .sorted(Comparator.comparing(DocumentResponse::updatedAt))
                .limit(10)
                .toList();

        List<DocumentResponse> reviewPending = documents.stream()
                .filter(d -> d.status() == DocumentStatus.UNDER_REVIEW)
                .filter(d -> d.updatedAt() != null
                        && Duration.between(d.updatedAt(), now).toDays() >= REVIEW_PENDING_DAYS)
                .sorted(Comparator.comparing(DocumentResponse::updatedAt))
                .limit(10)
                .toList();

        long approvedCount = documents.stream()
                .filter(d -> d.status() == DocumentStatus.APPROVED)
                .count();
        long supersededCount = documents.stream()
                .filter(d -> d.status() == DocumentStatus.SUPERSEDED)
                .count();
        long missingFileCount = documents.stream()
                .filter(d -> d.filePath() == null || d.filePath().isBlank())
                .count();

        ObjectNode root = objectMapper.createObjectNode();
        root.put("totalDocumentCount", totalCount);
        root.put("approvedCount", approvedCount);
        root.put("supersededCount", supersededCount);
        root.put("missingFileCount", missingFileCount);

        ObjectNode statusNode = root.putObject("statusBreakdown");
        statusBreakdown.forEach(statusNode::put);

        ObjectNode typeNode = root.putObject("documentTypeBreakdown");
        typeBreakdown.forEach(typeNode::put);

        ObjectNode disciplineNode = root.putObject("disciplineBreakdown");
        disciplineBreakdown.forEach(disciplineNode::put);

        ArrayNode staleArray = root.putArray("staleDrafts");
        for (DocumentResponse d : staleDrafts) {
            ObjectNode node = staleArray.addObject();
            node.put("documentNumber", d.documentNumber());
            node.put("title", d.title());
            node.put("daysSinceUpdate", d.updatedAt() != null
                    ? Duration.between(d.updatedAt(), now).toDays() : null);
            node.put("currentVersion", d.currentVersion());
        }

        ArrayNode reviewArray = root.putArray("reviewPending");
        for (DocumentResponse d : reviewPending) {
            ObjectNode node = reviewArray.addObject();
            node.put("documentNumber", d.documentNumber());
            node.put("title", d.title());
            node.put("daysInReview", d.updatedAt() != null
                    ? Duration.between(d.updatedAt(), now).toDays() : null);
        }

        return root;
    }

    @Override
    public List<ChartSpec> charts(UUID projectId) {
        if (projectId == null) {
            return List.of(
                    new ChartSpec("doc-status", "Document Status", "donut", null, null),
                    new ChartSpec("doc-discipline", "Documents by Discipline", "bar", null, null),
                    new ChartSpec("doc-type", "Documents by Type", "donut", null, null)
            );
        }

        List<DocumentResponse> documents = documentService.listDocuments(projectId);
        List<ChartSpec> charts = new ArrayList<>();

        Map<String, Long> statusBreakdown = documents.stream()
                .filter(d -> d.status() != null)
                .collect(Collectors.groupingBy(d -> d.status().name(), Collectors.counting()));
        charts.add(new ChartSpec("doc-status", "Document Status", "donut",
                EChartsOptions.donut(objectMapper, new LinkedHashMap<>(statusBreakdown)),
                "Document status distribution"));

        Map<String, Long> disciplineBreakdown = documents.stream()
                .filter(d -> d.discipline() != null)
                .collect(Collectors.groupingBy(d -> d.discipline().name(), Collectors.counting()));
        List<Map.Entry<String, Long>> disciplineEntries = disciplineBreakdown.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .toList();
        charts.add(new ChartSpec("doc-discipline", "Documents by Discipline", "bar",
                EChartsOptions.bar(objectMapper,
                        disciplineEntries.stream().map(Map.Entry::getKey).toList(),
                        "Documents",
                        disciplineEntries.stream().map(Map.Entry::getValue).toList()),
                "Top disciplines by document count"));

        Map<String, Long> typeBreakdown = documents.stream()
                .filter(d -> d.documentType() != null)
                .collect(Collectors.groupingBy(d -> d.documentType().name(), Collectors.counting()));
        charts.add(new ChartSpec("doc-type", "Documents by Type", "donut",
                EChartsOptions.donut(objectMapper, new LinkedHashMap<>(typeBreakdown)),
                "Distribution by document type"));

        return charts;
    }

    @Override
    public String tabKey() {
        return "documents";
    }

    @Override
    public String promptInstructions() {
        return "Produce insights to improve document control discipline. "
                + "Focus on stale drafts, documents stuck in review, status distribution, "
                + "missing file attachments, and discipline coverage. Use the findings field "
                + "for non-numeric observations (e.g., 'X drawings still in DRAFT after Y days', "
                + "'Z documents lack file attachments'). Recommendations should target "
                + "actionable document-control improvements rather than abstract advice.";
    }
}
