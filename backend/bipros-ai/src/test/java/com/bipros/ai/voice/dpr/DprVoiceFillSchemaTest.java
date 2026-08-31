package com.bipros.ai.voice.dpr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DprVoiceFillSchemaTest {

    private final DprVoiceFillSchema schemaBuilder = new DprVoiceFillSchema(new ObjectMapper());

    @Test
    void manpowerRowSchemaIncludesRoleFkFields() {
        JsonNode schema = schemaBuilder.buildSchema();
        JsonNode manpowerItem = schema
            .path("json_schema").path("schema")
            .path("properties").path("patch")
            .path("properties").path("manpower")
            .path("items");

        JsonNode props = manpowerItem.path("properties");
        assertThat(props.has("roleId")).as("manpower row must include roleId").isTrue();
        assertThat(props.has("manpowerRoleRateId")).as("manpower row must include manpowerRoleRateId").isTrue();
        assertThat(props.has("shift")).as("manpower row must include shift").isTrue();

        // OpenAI strict mode requires nullable fields expressed as type: ["string","null"].
        assertThat(props.path("roleId").path("type").isArray()).as("roleId type must be an array").isTrue();
        assertThat(props.path("manpowerRoleRateId").path("type").isArray()).as("manpowerRoleRateId type must be an array").isTrue();
        assertThat(props.path("shift").path("type").isArray()).as("shift type must be an array").isTrue();

        JsonNode required = manpowerItem.path("required");
        assertThat(required.isArray()).as("manpower row must have a required array").isTrue();
        assertThat(required).extracting(JsonNode::asText)
            .contains("roleId", "manpowerRoleRateId", "shift");
    }

    @Test
    void equipmentRowSchemaIncludesRoleFkFields() {
        JsonNode schema = schemaBuilder.buildSchema();
        JsonNode equipmentItem = schema
            .path("json_schema").path("schema")
            .path("properties").path("patch")
            .path("properties").path("equipment")
            .path("items");

        JsonNode props = equipmentItem.path("properties");
        assertThat(props.has("roleId")).as("equipment row must include roleId").isTrue();
        assertThat(props.has("equipmentRoleVariantId")).as("equipment row must include equipmentRoleVariantId").isTrue();
        assertThat(props.has("shift")).as("equipment row must include shift").isTrue();

        assertThat(props.path("roleId").path("type").isArray()).as("roleId type must be an array").isTrue();
        assertThat(props.path("equipmentRoleVariantId").path("type").isArray()).as("equipmentRoleVariantId type must be an array").isTrue();
        assertThat(props.path("shift").path("type").isArray()).as("shift type must be an array").isTrue();

        JsonNode required = equipmentItem.path("required");
        assertThat(required.isArray()).as("equipment row must have a required array").isTrue();
        assertThat(required).extracting(JsonNode::asText)
            .contains("roleId", "equipmentRoleVariantId", "shift");
    }

    @Test
    void materialRowSchemaIncludesRoleFkFields() {
        JsonNode schema = schemaBuilder.buildSchema();
        JsonNode materialItem = schema
            .path("json_schema").path("schema")
            .path("properties").path("patch")
            .path("properties").path("materials")
            .path("items");

        JsonNode props = materialItem.path("properties");
        assertThat(props.has("roleId")).as("material row must include roleId").isTrue();
        assertThat(props.has("materialRoleVariantId")).as("material row must include materialRoleVariantId").isTrue();

        assertThat(props.path("roleId").path("type").isArray()).as("roleId type must be an array").isTrue();
        assertThat(props.path("materialRoleVariantId").path("type").isArray()).as("materialRoleVariantId type must be an array").isTrue();

        JsonNode required = materialItem.path("required");
        assertThat(required.isArray()).as("material row must have a required array").isTrue();
        assertThat(required).extracting(JsonNode::asText)
            .contains("roleId", "materialRoleVariantId");
    }

    @Test
    void patchIncludesBoqItemId() {
        JsonNode patch = schemaBuilder.buildSchema()
            .path("json_schema").path("schema").path("properties").path("patch");
        assertThat(patch.path("properties").has("boqItemId")).as("patch must expose boqItemId").isTrue();
        assertThat(patch.path("required")).extracting(JsonNode::asText).contains("boqItemId");
    }

    @Test
    void unitIsConstrainedToStandardCodes() {
        JsonNode unit = schemaBuilder.buildSchema()
            .path("json_schema").path("schema").path("properties").path("patch")
            .path("properties").path("unit");
        assertThat(unit.path("type").isArray()).as("nullable enum type is an array").isTrue();
        java.util.List<String> enumVals = new java.util.ArrayList<>();
        unit.path("enum").forEach(n -> { if (!n.isNull()) enumVals.add(n.asText()); });
        assertThat(enumVals).contains("Cum", "Sqm", "MT", "Nr", "Hrs", "LS");
    }
}
