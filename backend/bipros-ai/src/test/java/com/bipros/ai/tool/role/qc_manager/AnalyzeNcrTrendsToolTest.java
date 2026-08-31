package com.bipros.ai.tool.role.qc_manager;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.testsupport.AiContextFixtures;
import com.bipros.ai.tool.ToolResult;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskCategoryMaster;
import com.bipros.risk.domain.model.RiskCategoryType;
import com.bipros.risk.domain.repository.RiskCategoryTypeRepository;
import com.bipros.risk.domain.repository.RiskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyzeNcrTrendsToolTest {

    private final RiskRepository riskRepo = mock(RiskRepository.class);
    private final RiskCategoryTypeRepository categoryTypeRepo = mock(RiskCategoryTypeRepository.class);
    private final ObjectMapper om = new ObjectMapper();
    private final AnalyzeNcrTrendsTool tool = new AnalyzeNcrTrendsTool(riskRepo, categoryTypeRepo, om);

    @Test
    void nameAndRoles() {
        assertEquals("analyze_ncr_trends", tool.name());
        assertTrue(tool.allowedRoles().contains("QC_MANAGER"));
        assertTrue(tool.allowedRoles().contains("PROJECT_MANAGER"));
    }

    @Test
    void fallsBackToRiskRegisterProxy() {
        UUID pid = UUID.randomUUID();

        // Set up a CONSTRUCTION_QUALITY category type
        RiskCategoryType qualityType = new RiskCategoryType();
        qualityType.setCode("CONSTRUCTION_QUALITY");
        qualityType.setName("Construction Quality");

        // Set up a quality-category risk master
        RiskCategoryMaster qualityCat = new RiskCategoryMaster();
        qualityCat.setCode("CQ-CONCRETE-CUBE-FAIL");
        qualityCat.setName("Concrete cube test failure");
        qualityCat.setType(qualityType);

        // Build two risk entries in quality category
        Risk risk1 = new Risk();
        risk1.setProjectId(pid);
        risk1.setCode("RISK-001");
        risk1.setTitle("Concrete cube test failure on pier 3");
        risk1.setCategory(qualityCat);
        risk1.setIdentifiedDate(LocalDate.of(2026, 4, 10));

        Risk risk2 = new Risk();
        risk2.setProjectId(pid);
        risk2.setCode("RISK-002");
        risk2.setTitle("BC riding-quality miss on chainage 14+200");
        risk2.setCategory(qualityCat);
        risk2.setIdentifiedDate(LocalDate.of(2026, 4, 22));

        when(categoryTypeRepo.findByCode(eq("CONSTRUCTION_QUALITY")))
                .thenReturn(Optional.of(qualityType));
        when(riskRepo.findByProjectId(any(UUID.class)))
                .thenReturn(List.of(risk1, risk2));

        AiContext ctx = AiContextFixtures.forProfile("QC_MANAGER", pid);
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success());
        assertNotNull(r.data());
        assertTrue(r.data().path("rows").isArray(), "Expected rows array");
        assertTrue(r.data().path("rows").size() > 0, "Expected at least one grouped row");
        // summary should mention proxy
        assertNotNull(r.summary());
        assertTrue(r.summary().contains("Risk Register"),
                "Summary should mention Risk Register proxy");
    }

    @Test
    void dataUnavailableWhenNoQualityRisks() {
        UUID pid = UUID.randomUUID();

        RiskCategoryType qualityType = new RiskCategoryType();
        qualityType.setCode("CONSTRUCTION_QUALITY");
        qualityType.setName("Construction Quality");

        when(categoryTypeRepo.findByCode(eq("CONSTRUCTION_QUALITY")))
                .thenReturn(Optional.of(qualityType));
        when(riskRepo.findByProjectId(any(UUID.class)))
                .thenReturn(List.of()); // no risks at all

        AiContext ctx = AiContextFixtures.forProfile("QC_MANAGER", pid);
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success(), "data_unavailable is still success=true with payload");
        assertEquals("data_unavailable", r.data().path("status").asText());
        assertFalse(r.data().path("reason").asText().isBlank());
        assertEquals("analyze_risk", r.data().path("closest_available").asText());
    }
}
