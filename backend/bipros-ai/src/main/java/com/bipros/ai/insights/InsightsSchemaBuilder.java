package com.bipros.ai.insights;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InsightsSchemaBuilder {

    private final ObjectMapper objectMapper;

    public JsonNode buildSchema() {
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("type", "json_schema");

        ObjectNode jsonSchema = objectMapper.createObjectNode();
        jsonSchema.put("name", "insights_response");
        jsonSchema.put("strict", true);

        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode summaryProp = objectMapper.createObjectNode();
        summaryProp.put("type", "string");
        properties.set("summary", summaryProp);

        ObjectNode highlightsProp = objectMapper.createObjectNode();
        highlightsProp.put("type", "array");
        ObjectNode highlightsItemsRef = objectMapper.createObjectNode();
        highlightsItemsRef.put("$ref", "#/$defs/highlight");
        highlightsProp.set("items", highlightsItemsRef);
        properties.set("highlights", highlightsProp);

        ObjectNode variancesProp = objectMapper.createObjectNode();
        variancesProp.put("type", "array");
        ObjectNode variancesItemsRef = objectMapper.createObjectNode();
        variancesItemsRef.put("$ref", "#/$defs/variance");
        variancesProp.set("items", variancesItemsRef);
        properties.set("variances", variancesProp);

        ObjectNode recommendationsProp = objectMapper.createObjectNode();
        recommendationsProp.put("type", "array");
        ObjectNode recommendationsItemsRef = objectMapper.createObjectNode();
        recommendationsItemsRef.put("$ref", "#/$defs/recommendation");
        recommendationsProp.set("items", recommendationsItemsRef);
        properties.set("recommendations", recommendationsProp);

        ObjectNode findingsProp = objectMapper.createObjectNode();
        findingsProp.put("type", "array");
        ObjectNode findingsItemsRef = objectMapper.createObjectNode();
        findingsItemsRef.put("$ref", "#/$defs/finding");
        findingsProp.set("items", findingsItemsRef);
        properties.set("findings", findingsProp);

        ObjectNode rationaleProp = objectMapper.createObjectNode();
        rationaleProp.put("type", "string");
        properties.set("rationale", rationaleProp);

        schema.set("properties", properties);

        ArrayNode topRequired = objectMapper.createArrayNode();
        topRequired.add("summary");
        topRequired.add("highlights");
        topRequired.add("variances");
        topRequired.add("recommendations");
        topRequired.add("findings");
        topRequired.add("rationale");
        schema.set("required", topRequired);

        ObjectNode defs = objectMapper.createObjectNode();
        defs.set("highlight", buildHighlightDef());
        defs.set("variance", buildVarianceDef());
        defs.set("recommendation", buildRecommendationDef());
        defs.set("finding", buildFindingDef());
        schema.set("$defs", defs);

        jsonSchema.set("schema", schema);
        wrapper.set("json_schema", jsonSchema);

        return wrapper;
    }

    private ObjectNode buildHighlightDef() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "object");
        node.put("additionalProperties", false);

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode labelProp = objectMapper.createObjectNode();
        labelProp.put("type", "string");
        properties.set("label", labelProp);

        ObjectNode valueProp = objectMapper.createObjectNode();
        valueProp.put("type", "string");
        properties.set("value", valueProp);

        ObjectNode severityProp = objectMapper.createObjectNode();
        severityProp.put("type", "string");
        properties.set("severity", severityProp);

        ObjectNode trendProp = objectMapper.createObjectNode();
        ArrayNode trendTypes = objectMapper.createArrayNode();
        trendTypes.add("string");
        trendTypes.add("null");
        trendProp.set("type", trendTypes);
        properties.set("trend", trendProp);

        node.set("properties", properties);

        ArrayNode required = objectMapper.createArrayNode();
        required.add("label");
        required.add("value");
        required.add("severity");
        required.add("trend");
        node.set("required", required);

        return node;
    }

    private ObjectNode buildVarianceDef() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "object");
        node.put("additionalProperties", false);

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode nameProp = objectMapper.createObjectNode();
        nameProp.put("type", "string");
        properties.set("name", nameProp);

        ObjectNode deltaProp = objectMapper.createObjectNode();
        deltaProp.put("type", "string");
        properties.set("delta", deltaProp);

        ObjectNode explanationProp = objectMapper.createObjectNode();
        explanationProp.put("type", "string");
        properties.set("explanation", explanationProp);

        node.set("properties", properties);

        ArrayNode required = objectMapper.createArrayNode();
        required.add("name");
        required.add("delta");
        required.add("explanation");
        node.set("required", required);

        return node;
    }

    private ObjectNode buildRecommendationDef() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "object");
        node.put("additionalProperties", false);

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode titleProp = objectMapper.createObjectNode();
        titleProp.put("type", "string");
        properties.set("title", titleProp);

        ObjectNode priorityProp = objectMapper.createObjectNode();
        priorityProp.put("type", "string");
        properties.set("priority", priorityProp);

        ObjectNode actionProp = objectMapper.createObjectNode();
        actionProp.put("type", "string");
        properties.set("action", actionProp);

        ObjectNode rationaleProp = objectMapper.createObjectNode();
        rationaleProp.put("type", "string");
        properties.set("rationale", rationaleProp);

        node.set("properties", properties);

        ArrayNode required = objectMapper.createArrayNode();
        required.add("title");
        required.add("priority");
        required.add("action");
        required.add("rationale");
        node.set("required", required);

        return node;
    }

    private ObjectNode buildFindingDef() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "object");
        node.put("additionalProperties", false);

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode labelProp = objectMapper.createObjectNode();
        labelProp.put("type", "string");
        properties.set("label", labelProp);

        ObjectNode detailProp = objectMapper.createObjectNode();
        detailProp.put("type", "string");
        properties.set("detail", detailProp);

        ObjectNode severityProp = objectMapper.createObjectNode();
        severityProp.put("type", "string");
        properties.set("severity", severityProp);

        node.set("properties", properties);

        ArrayNode required = objectMapper.createArrayNode();
        required.add("label");
        required.add("detail");
        required.add("severity");
        node.set("required", required);

        return node;
    }
}
