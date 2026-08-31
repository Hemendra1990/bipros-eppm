package com.bipros.ai.tool.resource;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.resolver.EffectiveRate;
import com.bipros.ai.resolver.EffectiveRateResolver;
import com.bipros.ai.tool.ToolResult;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.model.ResourceRole;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class CompareResourcesAcrossProjectsToolTest {

  private ProjectRepository projectRepo;
  private ResourceRepository resourceRepo;
  private ResourceAssignmentRepository assignmentRepo;
  private EffectiveRateResolver rateResolver;
  private CompareResourcesAcrossProjectsTool tool;
  private ObjectMapper objectMapper;

  private UUID projectAId;
  private UUID projectBId;
  private UUID projectCId;
  private UUID masonId;

  @BeforeEach
  void setUp() {
    projectRepo = Mockito.mock(ProjectRepository.class);
    resourceRepo = Mockito.mock(ResourceRepository.class);
    assignmentRepo = Mockito.mock(ResourceAssignmentRepository.class);
    rateResolver = Mockito.mock(EffectiveRateResolver.class);
    objectMapper = new ObjectMapper();
    tool =
        new CompareResourcesAcrossProjectsTool(
            projectRepo, resourceRepo, assignmentRepo, rateResolver, objectMapper);

    projectAId = UUID.randomUUID();
    projectBId = UUID.randomUUID();
    projectCId = UUID.randomUUID();
    masonId = UUID.randomUUID();
  }

  @Test
  void aggregatesMasonAcrossThreeProjectsWithOverrideOnOne() {
    Project a = project(projectAId, "PROJ-A", "Project A");
    Project b = project(projectBId, "PROJ-B", "Project B");
    Project c = project(projectCId, "PROJ-C", "Project C");
    when(projectRepo.findAllById(any())).thenReturn(List.of(a, b, c));

    Resource mason = resource(masonId, "RES-MASON", "Mason", "LABOR", "80");
    when(resourceRepo.findAllById(any())).thenReturn(List.of(mason));

    ResourceAssignment aa = assignment(projectAId, masonId, 10.0, 0.0, "800", "0");
    ResourceAssignment ab = assignment(projectBId, masonId, 5.0, 0.0, "475", "0");
    ResourceAssignment ac = assignment(projectCId, masonId, 8.0, 0.0, "640", "0");
    when(assignmentRepo.findByProjectId(projectAId)).thenReturn(List.of(aa));
    when(assignmentRepo.findByProjectId(projectBId)).thenReturn(List.of(ab));
    when(assignmentRepo.findByProjectId(projectCId)).thenReturn(List.of(ac));

    when(rateResolver.resolve(projectAId, masonId))
        .thenReturn(
            new EffectiveRate(
                new BigDecimal("80"),
                EffectiveRate.Source.RESOURCE,
                "Day",
                "DAY",
                false,
                null,
                null));
    when(rateResolver.resolve(projectBId, masonId))
        .thenReturn(
            new EffectiveRate(
                new BigDecimal("95"),
                EffectiveRate.Source.OVERRIDE,
                "Day",
                "DAY",
                true,
                UUID.randomUUID(),
                null));
    when(rateResolver.resolve(projectCId, masonId))
        .thenReturn(
            new EffectiveRate(
                new BigDecimal("80"),
                EffectiveRate.Source.RESOURCE,
                "Day",
                "DAY",
                false,
                null,
                null));

    AiContext ctx =
        new AiContext(
            UUID.randomUUID(),
            null,
            "general",
            "PROJECT_MANAGER",
            null,
            List.of(projectAId, projectBId, projectCId));

    ToolResult result = tool.execute(input("mason", null), ctx);

    assertThat(result.success()).isTrue();
    ObjectNode data = (ObjectNode) result.data();
    ArrayNode rows = (ArrayNode) data.get("rows");
    assertThat(rows).hasSize(3);
    assertThat(data.get("override_row_count").asInt()).isEqualTo(1);
    assertThat(data.get("project_count_scanned").asInt()).isEqualTo(3);

    ObjectNode bRow = findRowByProjectCode(rows, "PROJ-B");
    assertThat(bRow.get("effective_rate").asDouble()).isEqualTo(95.0);
    assertThat(bRow.get("override_applied").asBoolean()).isTrue();
    ArrayNode bNotes = (ArrayNode) bRow.get("formula_overrides");
    assertThat(bNotes.size()).isEqualTo(1);
    assertThat(bNotes.get(0).asText()).isEqualTo("rate_overridden_per_project");

    ObjectNode aRow = findRowByProjectCode(rows, "PROJ-A");
    assertThat(aRow.get("effective_rate").asDouble()).isEqualTo(80.0);
    assertThat(aRow.get("override_applied").asBoolean()).isFalse();
    ArrayNode aNotes = (ArrayNode) aRow.get("formula_overrides");
    assertThat(aNotes.size()).isZero();

    ArrayNode topNotes = (ArrayNode) data.get("formula_overrides");
    assertThat(topNotes.size()).isEqualTo(2);
    assertThat(topNotes.get(0).asText()).isEqualTo("cross_project_rollup");
    assertThat(topNotes.get(1).asText()).isEqualTo("rate_overridden_per_project");
  }

  @Test
  void rejectsBlankKeyword() {
    AiContext ctx =
        new AiContext(
            UUID.randomUUID(), null, "general", "PROJECT_MANAGER", null, List.of(projectAId));
    ToolResult result = tool.execute(input("   ", null), ctx);
    assertThat(result.success()).isFalse();
    assertThat(result.error()).contains("keyword");
  }

  @Test
  void filtersByResourceTypeWhenSpecified() {
    Project a = project(projectAId, "PROJ-A", "Project A");
    when(projectRepo.findAllById(any())).thenReturn(List.of(a));

    Resource manpowerMason = resource(masonId, "RES-MASON", "Mason", "LABOR", "80");
    UUID materialId = UUID.randomUUID();
    Resource matMason = resource(materialId, "MAT-MASONRY", "Masonry sand", "MATERIAL", "50");
    when(resourceRepo.findAllById(any())).thenReturn(List.of(manpowerMason, matMason));

    when(assignmentRepo.findByProjectId(projectAId))
        .thenReturn(
            List.of(
                assignment(projectAId, masonId, 10.0, 0.0, "800", "0"),
                assignment(projectAId, materialId, 100.0, 0.0, "5000", "0")));

    when(rateResolver.resolve(projectAId, masonId))
        .thenReturn(
            new EffectiveRate(
                new BigDecimal("80"),
                EffectiveRate.Source.RESOURCE,
                "Day",
                "DAY",
                false,
                null,
                null));
    when(rateResolver.resolve(projectAId, materialId))
        .thenReturn(
            new EffectiveRate(
                new BigDecimal("50"),
                EffectiveRate.Source.RESOURCE,
                "Cum",
                "EACH",
                false,
                null,
                null));

    AiContext ctx =
        new AiContext(
            UUID.randomUUID(), null, "general", "PROJECT_MANAGER", null, List.of(projectAId));

    ToolResult labourOnly = tool.execute(input("mason", "LABOR"), ctx);
    ArrayNode labourRows = (ArrayNode) labourOnly.data().get("rows");
    assertThat(labourRows).hasSize(1);
    assertThat(labourRows.get(0).get("resource_code").asText()).isEqualTo("RES-MASON");

    ToolResult unrestricted = tool.execute(input("mason", null), ctx);
    ArrayNode allRows = (ArrayNode) unrestricted.data().get("rows");
    assertThat(allRows).hasSize(2);
  }

  // --- helpers --------------------------------------------------------------

  private ObjectNode input(String keyword, String resourceType) {
    ObjectNode in = objectMapper.createObjectNode();
    if (keyword != null) in.put("keyword", keyword);
    if (resourceType != null) in.put("resource_type", resourceType);
    return in;
  }

  private Project project(UUID id, String code, String name) {
    Project p = new Project();
    p.setId(id);
    p.setCode(code);
    p.setName(name);
    return p;
  }

  private Resource resource(UUID id, String code, String name, String typeCode, String rate) {
    Resource r = new Resource();
    r.setId(id);
    r.setCode(code);
    r.setName(name);
    r.setUnit("Day");
    r.setCostPerUnit(new BigDecimal(rate));
    com.bipros.resource.domain.model.ResourceType rt =
        new com.bipros.resource.domain.model.ResourceType();
    rt.setCode(typeCode);
    rt.setName(typeCode);
    r.setResourceType(rt);
    ResourceRole role = new ResourceRole();
    role.setCode(code);
    role.setName(name);
    r.setRole(role);
    return r;
  }

  private ResourceAssignment assignment(
      UUID projectId,
      UUID resourceId,
      double plannedUnits,
      double actualUnits,
      String plannedCost,
      String actualCost) {
    ResourceAssignment a = new ResourceAssignment();
    a.setProjectId(projectId);
    a.setResourceId(resourceId);
    a.setActivityId(UUID.randomUUID());
    a.setPlannedUnits(plannedUnits);
    a.setActualUnits(actualUnits);
    a.setPlannedCost(new BigDecimal(plannedCost));
    a.setActualCost(new BigDecimal(actualCost));
    return a;
  }

  private ObjectNode findRowByProjectCode(ArrayNode rows, String code) {
    for (com.fasterxml.jackson.databind.JsonNode n : rows) {
      if (code.equals(n.get("project_code").asText())) return (ObjectNode) n;
    }
    throw new AssertionError("No row for project_code=" + code);
  }
}
