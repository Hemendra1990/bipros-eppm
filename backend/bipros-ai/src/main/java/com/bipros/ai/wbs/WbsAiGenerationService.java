package com.bipros.ai.wbs;

import com.bipros.ai.provider.LlmProvider;
import com.bipros.ai.provider.LlmProviderConfig;
import com.bipros.ai.provider.LlmProviderConfigRepository;
import com.bipros.ai.provider.OpenAiCompatibleProvider;
import com.bipros.ai.wbs.dto.WbsAiApplyRequest;
import com.bipros.ai.wbs.dto.WbsAiGenerateRequest;
import com.bipros.ai.wbs.dto.WbsAiGenerationResponse;
import com.bipros.ai.wbs.dto.WbsAiNode;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.util.AuditService;
import com.bipros.project.domain.model.AssetClass;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WbsAiGenerationService {

    private final LlmProviderConfigRepository llmProviderConfigRepository;
    private final OpenAiCompatibleProvider openAiCompatibleProvider;
    private final WbsNodeRepository wbsNodeRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public WbsAiGenerationResponse generate(UUID projectId, WbsAiGenerateRequest req) {
        log.info("Generating WBS with AI for project: {}", projectId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessRuleException("PROJECT_NOT_FOUND", "Project not found: " + projectId));

        LlmProviderConfig config = llmProviderConfigRepository.findByIsDefaultTrueAndIsActiveTrue()
                .or(() -> llmProviderConfigRepository.findFirstByIsActiveTrueOrderByIsDefaultDescCreatedAtAsc())
                .orElseThrow(() -> new BusinessRuleException("AI_NOT_CONFIGURED",
                        "No active AI provider configured. Please configure an LLM provider in Settings."));

        AssetClass resolvedAssetClass = resolveAssetClass(project, req);
        boolean needsConfirmation = resolvedAssetClass == null && req.assetClass() == null;

        if (needsConfirmation) {
            return new WbsAiGenerationResponse(null, true,
                    "Could not determine project type. Please select an asset class.", List.of());
        }

        String prompt = buildPrompt(project, req, resolvedAssetClass);
        JsonNode responseSchema = buildResponseSchema();

        List<LlmProvider.Message> messages = List.of(
                new LlmProvider.Message("system",
                        "You are a construction WBS (Work Breakdown Structure) expert. " +
                        "Generate a hierarchical WBS tree appropriate for the given project type and context. " +
                        "Use industry-standard codes and naming conventions. " +
                        "Return ONLY valid JSON matching the provided schema. No markdown, no explanations outside the JSON."),
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
            log.error("LLM call failed for WBS generation", e);
            throw new BusinessRuleException("AI_GENERATION_FAILED", "AI generation failed: " + e.getMessage());
        }

        if (resp.content() == null || resp.content().isBlank()) {
            throw new BusinessRuleException("AI_GENERATION_FAILED", "AI returned empty response");
        }

        try {
            JsonNode root = objectMapper.readTree(resp.content());
            JsonNode nodesNode = root.has("nodes") ? root.get("nodes") : root;
            List<WbsAiNode> nodes = objectMapper.convertValue(nodesNode,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, WbsAiNode.class));
            String rationale = root.has("rationale") ? root.get("rationale").asText() : "";
            return new WbsAiGenerationResponse(resolvedAssetClass, false, rationale, nodes);
        } catch (Exception e) {
            log.error("Failed to parse AI WBS response: {}", resp.content(), e);
            throw new BusinessRuleException("AI_GENERATION_FAILED", "Failed to parse AI response: " + e.getMessage());
        }
    }

    @Transactional
    public List<String> applyGenerated(UUID projectId, WbsAiApplyRequest req) {
        log.info("Applying AI-generated WBS to project: {}, parent: {}", projectId, req.parentId());

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessRuleException("PROJECT_NOT_FOUND", "Project not found: " + projectId));

        AssetClass assetClass = project.getIndustryCode() != null ? resolveAssetClassFromCode(project.getIndustryCode()) : null;

        List<String> collisions = new ArrayList<>();
        for (WbsAiNode node : req.nodes()) {
            createNodeFromAi(projectId, node, req.parentId(), assetClass, collisions);
        }

        log.info("AI-generated WBS applied to project: {}, collisions: {}", projectId, collisions.size());
        return collisions;
    }

    private void createNodeFromAi(UUID projectId, WbsAiNode aiNode, UUID parentId,
                                   AssetClass assetClass, List<String> collisions) {
        String code = aiNode.code();
        if (wbsNodeRepository.existsByProjectIdAndCode(projectId, code)) {
            String originalCode = code;
            for (int i = 1; i <= 5; i++) {
                code = originalCode + "-AI" + (i > 1 ? i : "");
                if (!wbsNodeRepository.existsByProjectIdAndCode(projectId, code)) break;
            }
            if (wbsNodeRepository.existsByProjectIdAndCode(projectId, code)) {
                code = originalCode + "-AI-" + UUID.randomUUID().toString().substring(0, 6);
            }
            collisions.add(originalCode + " -> " + code);
        }

        WbsNode wbsNode = new WbsNode();
        wbsNode.setCode(code);
        wbsNode.setName(aiNode.name());
        wbsNode.setParentId(parentId);
        wbsNode.setProjectId(projectId);
        wbsNode.setAssetClass(assetClass);
        wbsNode.setSortOrder(0);

        WbsNode savedNode = wbsNodeRepository.save(wbsNode);
        auditService.logCreate("WbsNode", savedNode.getId(), aiNode);

        if (aiNode.children() != null) {
            for (WbsAiNode child : aiNode.children()) {
                createNodeFromAi(projectId, child, savedNode.getId(), assetClass, collisions);
            }
        }
    }

    private AssetClass resolveAssetClass(Project project, WbsAiGenerateRequest req) {
        if (req.assetClass() != null) {
            return req.assetClass();
        }
        if (project.getIndustryCode() != null) {
            return resolveAssetClassFromCode(project.getIndustryCode());
        }
        return null;
    }

    private AssetClass resolveAssetClassFromCode(String industryCode) {
        try {
            return AssetClass.valueOf(industryCode.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String buildPrompt(Project project, WbsAiGenerateRequest req, AssetClass assetClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate a Work Breakdown Structure (WBS) for the following project:\n\n");
        sb.append("Project: ").append(project.getName()).append(" (").append(project.getCode()).append(")\n");
        sb.append("Asset Class: ").append(assetClass.name()).append("\n");

        if (project.getTotalLengthKm() != null) {
            sb.append("Total Length: ").append(project.getTotalLengthKm()).append(" km\n");
        }
        if (project.getFromChainageM() != null && project.getToChainageM() != null) {
            sb.append("Chainage: ").append(project.getFromChainageM()).append("m to ")
                    .append(project.getToChainageM()).append("m\n");
        }
        if (project.getFromLocation() != null && project.getToLocation() != null) {
            sb.append("Corridor: ").append(project.getFromLocation()).append(" to ")
                    .append(project.getToLocation()).append("\n");
        }
        if (project.getDescription() != null && !project.getDescription().isBlank()) {
            sb.append("Description: ").append(project.getDescription()).append("\n");
        }

        int depth = req.targetDepth() != null ? req.targetDepth() : 3;
        sb.append("\nTarget depth: ").append(depth).append(" levels\n");

        if (req.projectTypeHint() != null && !req.projectTypeHint().isBlank()) {
            sb.append("\nProject type details: ").append(req.projectTypeHint()).append("\n");
        }
        if (req.additionalContext() != null && !req.additionalContext().isBlank()) {
            sb.append("\nAdditional context: ").append(req.additionalContext()).append("\n");
        }

        sb.append("\nGenerate a comprehensive WBS with appropriate phases, sub-phases, and work packages. ");
        sb.append("Use hierarchical codes (e.g., PRJ.1, PRJ.1.1, PRJ.1.1.1). ");
        sb.append("Include a brief rationale for the structure.");

        return sb.toString();
    }

    private JsonNode buildResponseSchema() {
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("type", "json_schema");

        ObjectNode jsonSchema = objectMapper.createObjectNode();
        jsonSchema.put("name", "wbs_generation");
        jsonSchema.put("strict", true);

        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        ObjectNode properties = objectMapper.createObjectNode();
        ObjectNode rationaleProp = objectMapper.createObjectNode();
        rationaleProp.put("type", "string");
        properties.set("rationale", rationaleProp);

        ObjectNode nodesProp = objectMapper.createObjectNode();
        nodesProp.put("type", "array");
        ObjectNode nodesItemsRef = objectMapper.createObjectNode();
        nodesItemsRef.put("$ref", "#/$defs/wbs_node");
        nodesProp.set("items", nodesItemsRef);
        properties.set("nodes", nodesProp);

        schema.set("properties", properties);

        ArrayNode topRequired = objectMapper.createArrayNode();
        topRequired.add("rationale");
        topRequired.add("nodes");
        schema.set("required", topRequired);

        ObjectNode defs = objectMapper.createObjectNode();
        defs.set("wbs_node", buildWbsNodeDef());
        schema.set("$defs", defs);

        jsonSchema.set("schema", schema);
        wrapper.set("json_schema", jsonSchema);

        return wrapper;
    }

    private ObjectNode buildWbsNodeDef() {
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

        // Nullable string for OpenAI strict mode (all properties must be required;
        // optional values are expressed via union with null).
        ObjectNode descProp = objectMapper.createObjectNode();
        ArrayNode descTypes = objectMapper.createArrayNode();
        descTypes.add("string");
        descTypes.add("null");
        descProp.set("type", descTypes);
        properties.set("description", descProp);

        ObjectNode childrenProp = objectMapper.createObjectNode();
        childrenProp.put("type", "array");
        ObjectNode childRef = objectMapper.createObjectNode();
        childRef.put("$ref", "#/$defs/wbs_node");
        childrenProp.set("items", childRef);
        properties.set("children", childrenProp);

        node.set("properties", properties);

        ArrayNode required = objectMapper.createArrayNode();
        required.add("code");
        required.add("name");
        required.add("description");
        required.add("children");
        node.set("required", required);

        return node;
    }
}
