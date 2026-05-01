package com.bipros.ai.activity;

import com.bipros.ai.activity.dto.*;
import com.bipros.ai.provider.LlmProvider;
import com.bipros.ai.provider.LlmProviderConfig;
import com.bipros.ai.provider.LlmProviderConfigRepository;
import com.bipros.ai.provider.OpenAiCompatibleProvider;
import com.bipros.activity.application.dto.CreateActivityRequest;
import com.bipros.activity.application.dto.CreateRelationshipRequest;
import com.bipros.activity.application.dto.ActivityResponse;
import com.bipros.activity.application.dto.RelationshipResponse;
import com.bipros.activity.application.service.ActivityService;
import com.bipros.activity.application.service.RelationshipService;
import com.bipros.activity.domain.model.RelationshipType;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.util.AuditService;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityAiGenerationService {

    private final LlmProviderConfigRepository llmProviderConfigRepository;
    private final OpenAiCompatibleProvider openAiCompatibleProvider;
    private final ProjectRepository projectRepository;
    private final WbsNodeRepository wbsNodeRepository;
    private final ActivityRepository activityRepository;
    private final ActivityService activityService;
    private final RelationshipService relationshipService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ActivityAiGenerationResponse generate(UUID projectId, ActivityAiGenerateRequest req) {
        log.info("Generating activities with AI for project: {}", projectId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessRuleException("PROJECT_NOT_FOUND", "Project not found: " + projectId));

        List<WbsNode> wbsNodes = wbsNodeRepository.findByProjectIdOrderBySortOrder(projectId);
        if (wbsNodes.isEmpty()) {
            throw new BusinessRuleException("WBS_REQUIRED",
                    "Project has no WBS. Please create a WBS before generating activities.");
        }

        LlmProviderConfig config = llmProviderConfigRepository.findByIsDefaultTrueAndIsActiveTrue()
                .or(() -> llmProviderConfigRepository.findFirstByIsActiveTrueOrderByIsDefaultDescCreatedAtAsc())
                .orElseThrow(() -> new BusinessRuleException("AI_NOT_CONFIGURED",
                        "No active AI provider configured. Please configure an LLM provider in Settings."));

        String prompt = buildPrompt(project, wbsNodes, req);
        JsonNode responseSchema = buildResponseSchema();

        List<LlmProvider.Message> messages = List.of(
                new LlmProvider.Message("system",
                        "You are a construction project planning expert. Generate activities for the project's WBS " +
                        "using structured output. Reference only the WBS codes given. Each activity must have a " +
                        "unique short code like A-001, A-002. predecessorCodes references other activity codes " +
                        "in this batch, never WBS codes. Use simple finish-to-start sequencing. " +
                        "Return ONLY valid JSON. No markdown, no explanations outside the JSON."),
                new LlmProvider.Message("user", prompt)
        );

        LlmProvider.ChatRequest chatReq = new LlmProvider.ChatRequest(
                messages, null, config.getMaxTokens(),
                config.getTemperature().doubleValue(), (long) config.getTimeoutMs(),
                responseSchema
        );

        LlmProvider.ChatResponse resp;
        try {
            resp = openAiCompatibleProvider.chat(config, chatReq);
        } catch (Exception e) {
            log.error("LLM call failed for activity generation", e);
            throw new BusinessRuleException("AI_GENERATION_FAILED", "AI generation failed: " + e.getMessage());
        }

        if (resp.content() == null || resp.content().isBlank()) {
            throw new BusinessRuleException("AI_GENERATION_FAILED", "AI returned empty response");
        }

        try {
            JsonNode root = objectMapper.readTree(resp.content());
            String rationale = root.has("rationale") ? root.get("rationale").asText() : "";
            JsonNode activitiesNode = root.get("activities");
            List<ActivityAiNode> activities = objectMapper.convertValue(activitiesNode,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ActivityAiNode.class));
            return new ActivityAiGenerationResponse(rationale, activities);
        } catch (Exception e) {
            log.error("Failed to parse AI activity response: {}", resp.content(), e);
            throw new BusinessRuleException("AI_GENERATION_FAILED", "Failed to parse AI response: " + e.getMessage());
        }
    }

    @Transactional
    public ActivityAiApplyResponse applyGenerated(UUID projectId, ActivityAiApplyRequest req) {
        log.info("Applying AI-generated activities to project: {}", projectId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessRuleException("PROJECT_NOT_FOUND", "Project not found: " + projectId));

        Map<String, UUID> wbsCodeToId = wbsNodeRepository.findByProjectIdOrderBySortOrder(projectId)
                .stream()
                .collect(Collectors.toMap(WbsNode::getCode, WbsNode::getId));

        List<String> codeCollisions = new ArrayList<>();
        List<String> wbsResolutionFailures = new ArrayList<>();
        List<String> relationshipResolutionFailures = new ArrayList<>();
        List<UUID> createdActivityIds = new ArrayList<>();
        List<UUID> createdRelationshipIds = new ArrayList<>();

        // Pre-scan code collisions and build remap
        Map<String, String> codeRemap = new HashMap<>();
        for (ActivityAiNode node : req.activities()) {
            String code = node.code();
            if (activityRepository.existsByProjectIdAndCode(projectId, code)) {
                String originalCode = code;
                for (int i = 1; i <= 5; i++) {
                    code = originalCode + "-AI" + (i > 1 ? i : "");
                    if (!activityRepository.existsByProjectIdAndCode(projectId, code)) break;
                }
                if (activityRepository.existsByProjectIdAndCode(projectId, code)) {
                    code = originalCode + "-AI-" + UUID.randomUUID().toString().substring(0, 6);
                }
                codeCollisions.add(originalCode + " -> " + code);
                codeRemap.put(originalCode, code);
            }
        }

        // First pass: create activities
        Map<String, UUID> generatedCodeToId = new LinkedHashMap<>();
        for (ActivityAiNode node : req.activities()) {
            String wbsNodeCode = node.wbsNodeCode();
            UUID wbsNodeId = wbsCodeToId.get(wbsNodeCode);
            if (wbsNodeId == null) {
                wbsResolutionFailures.add(node.code() + " -> wbsNodeCode '" + wbsNodeCode + "' not found");
                continue;
            }

            String resolvedCode = codeRemap.getOrDefault(node.code(), node.code());
            Double duration = node.originalDurationDays();

            CreateActivityRequest createReq = new CreateActivityRequest(
                    resolvedCode,
                    node.name(),
                    node.description(),
                    projectId,
                    wbsNodeId,
                    null,  // activityType — let service apply default
                    null,  // durationType
                    null,  // percentCompleteType
                    duration,
                    null,  // plannedStartDate
                    null,  // plannedFinishDate
                    null,  // calendarId
                    null,  // chainageFromM
                    null,  // chainageToM
                    null,  // workActivityId
                    null   // costAccountId
            );

            try {
                ActivityResponse saved = activityService.createActivity(createReq);
                generatedCodeToId.put(node.code(), saved.id());
                createdActivityIds.add(saved.id());
            } catch (Exception e) {
                log.warn("Failed to create activity {}: {}", resolvedCode, e.getMessage());
                wbsResolutionFailures.add(node.code() + " -> creation failed: " + e.getMessage());
            }
        }

        // Second pass: create relationships
        for (ActivityAiNode node : req.activities()) {
            if (node.predecessorCodes() == null || node.predecessorCodes().isEmpty()) continue;

            UUID successorId = generatedCodeToId.get(node.code());
            if (successorId == null) continue;

            for (String predCode : node.predecessorCodes()) {
                String resolvedPredCode = codeRemap.getOrDefault(predCode, predCode);
                UUID predecessorId = generatedCodeToId.get(predCode);
                if (predecessorId == null) {
                    // Try resolved code
                    for (Map.Entry<String, UUID> entry : generatedCodeToId.entrySet()) {
                        if (codeRemap.getOrDefault(entry.getKey(), entry.getKey()).equals(resolvedPredCode)) {
                            predecessorId = entry.getValue();
                            break;
                        }
                    }
                }
                if (predecessorId == null) {
                    relationshipResolutionFailures.add(
                            node.code() + " -> predecessor '" + predCode + "' not found in batch");
                    continue;
                }

                CreateRelationshipRequest relReq = new CreateRelationshipRequest(
                        predecessorId,
                        successorId,
                        RelationshipType.FINISH_TO_START,
                        0.0
                );

                try {
                    RelationshipResponse rel = relationshipService.createRelationship(relReq);
                    createdRelationshipIds.add(rel.id());
                } catch (BusinessRuleException e) {
                    relationshipResolutionFailures.add(
                            node.code() + " -> " + predCode + ": " + e.getMessage());
                } catch (Exception e) {
                    relationshipResolutionFailures.add(
                            node.code() + " -> " + predCode + ": " + e.getMessage());
                }
            }
        }

        auditService.logCreate("ActivityAiBatch", projectId, req);

        log.info("AI-generated activities applied to project: {}, created: {}, collisions: {}, wbsFailures: {}, relFailures: {}",
                projectId, createdActivityIds.size(), codeCollisions.size(),
                wbsResolutionFailures.size(), relationshipResolutionFailures.size());

        return new ActivityAiApplyResponse(
                codeCollisions,
                wbsResolutionFailures,
                relationshipResolutionFailures,
                createdActivityIds,
                createdRelationshipIds
        );
    }

    private String buildPrompt(Project project, List<WbsNode> wbsNodes, ActivityAiGenerateRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate activities for the following project:\n\n");
        sb.append("Project: ").append(project.getName()).append(" (").append(project.getCode()).append(")\n");

        if (project.getIndustryCode() != null) {
            sb.append("Asset Class: ").append(project.getIndustryCode()).append("\n");
        }
        if (project.getFromChainageM() != null && project.getToChainageM() != null) {
            sb.append("Chainage: ").append(project.getFromChainageM()).append("m to ")
                    .append(project.getToChainageM()).append("m\n");
        }
        if (project.getFromLocation() != null && project.getToLocation() != null) {
            sb.append("Corridor: ").append(project.getFromLocation()).append(" to ")
                    .append(project.getToLocation()).append("\n");
        }
        if (project.getTotalLengthKm() != null) {
            sb.append("Total Length: ").append(project.getTotalLengthKm()).append(" km\n");
        }
        if (project.getDescription() != null && !project.getDescription().isBlank()) {
            sb.append("Description: ").append(project.getDescription()).append("\n");
        }

        sb.append("\nExisting WBS Structure:\n");
        Map<UUID, String> parentIdMap = new HashMap<>();
        for (WbsNode node : wbsNodes) {
            parentIdMap.put(node.getId(), node.getParentId() != null ? node.getParentId().toString() : null);
        }

        Map<UUID, Integer> depthMap = new HashMap<>();
        for (WbsNode node : wbsNodes) {
            int depth = 0;
            UUID parentId = node.getParentId();
            while (parentId != null) {
                depth++;
                final UUID searchId = parentId;
                WbsNode parent = wbsNodes.stream().filter(n -> n.getId().equals(searchId)).findFirst().orElse(null);
                parentId = parent != null ? parent.getParentId() : null;
            }
            depthMap.put(node.getId(), depth);
        }

        // Determine leaf nodes (nodes that are not parents of any other node)
        Set<UUID> parentIds = wbsNodes.stream()
                .map(WbsNode::getParentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (WbsNode node : wbsNodes) {
            int depth = depthMap.getOrDefault(node.getId(), 0);
            String indent = "  ".repeat(depth);
            boolean isLeaf = !parentIds.contains(node.getId());
            sb.append(indent).append(node.getCode()).append("  ").append(node.getName());
            if (isLeaf) sb.append(" (leaf)");
            sb.append("\n");
        }

        sb.append("\nEvery activity MUST set wbsNodeCode to one of the existing codes listed above — prefer leaves.\n");

        int targetCount = req.targetActivityCount() != null ? req.targetActivityCount() : 15;
        sb.append("\nTarget activity count: ").append(targetCount).append(" (soft hint)\n");

        if (req.defaultDurationDays() != null) {
            sb.append("Default duration (days): ").append(req.defaultDurationDays()).append(" (soft hint)\n");
        }
        if (req.projectTypeHint() != null && !req.projectTypeHint().isBlank()) {
            sb.append("\nProject type details: ").append(req.projectTypeHint()).append("\n");
        }
        if (req.additionalContext() != null && !req.additionalContext().isBlank()) {
            sb.append("\nAdditional context: ").append(req.additionalContext()).append("\n");
        }

        sb.append("\nReturn activities with short codes like A-001, A-002. ");
        sb.append("predecessorCodes references other codes in this batch, never WBS codes. ");
        sb.append("Use simple finish-to-start sequencing. ");
        sb.append("Include a brief rationale for the activity breakdown.");

        return sb.toString();
    }

    private JsonNode buildResponseSchema() {
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("type", "json_schema");

        ObjectNode jsonSchema = objectMapper.createObjectNode();
        jsonSchema.put("name", "activity_generation");
        jsonSchema.put("strict", true);

        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode rationaleProp = objectMapper.createObjectNode();
        rationaleProp.put("type", "string");
        properties.set("rationale", rationaleProp);

        ObjectNode activitiesProp = objectMapper.createObjectNode();
        activitiesProp.put("type", "array");
        ObjectNode activitiesItemsRef = objectMapper.createObjectNode();
        activitiesItemsRef.put("$ref", "#/$defs/activity_node");
        activitiesProp.set("items", activitiesItemsRef);
        properties.set("activities", activitiesProp);

        schema.set("properties", properties);

        ArrayNode topRequired = objectMapper.createArrayNode();
        topRequired.add("rationale");
        topRequired.add("activities");
        schema.set("required", topRequired);

        ObjectNode defs = objectMapper.createObjectNode();
        defs.set("activity_node", buildActivityNodeDef());
        schema.set("$defs", defs);

        jsonSchema.set("schema", schema);
        wrapper.set("json_schema", jsonSchema);

        return wrapper;
    }

    private ObjectNode buildActivityNodeDef() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "object");
        node.put("additionalProperties", false);

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode codeProp = objectMapper.createObjectNode();
        codeProp.put("type", "string");
        properties.set("code", codeProp);

        ObjectNode nameProp = objectMapper.createObjectNode();
        nameProp.put("type", "string");
        properties.set("name", nameProp);

        // Nullable string for OpenAI strict mode
        ObjectNode descProp = objectMapper.createObjectNode();
        ArrayNode descTypes = objectMapper.createArrayNode();
        descTypes.add("string");
        descTypes.add("null");
        descProp.set("type", descTypes);
        properties.set("description", descProp);

        ObjectNode wbsNodeCodeProp = objectMapper.createObjectNode();
        wbsNodeCodeProp.put("type", "string");
        properties.set("wbsNodeCode", wbsNodeCodeProp);

        ObjectNode durationProp = objectMapper.createObjectNode();
        durationProp.put("type", "number");
        properties.set("originalDurationDays", durationProp);

        ObjectNode predecessorsProp = objectMapper.createObjectNode();
        predecessorsProp.put("type", "array");
        ObjectNode predItems = objectMapper.createObjectNode();
        predItems.put("type", "string");
        predecessorsProp.set("items", predItems);
        properties.set("predecessorCodes", predecessorsProp);

        node.set("properties", properties);

        ArrayNode required = objectMapper.createArrayNode();
        required.add("code");
        required.add("name");
        required.add("description");
        required.add("wbsNodeCode");
        required.add("originalDurationDays");
        required.add("predecessorCodes");
        node.set("required", required);

        return node;
    }
}
