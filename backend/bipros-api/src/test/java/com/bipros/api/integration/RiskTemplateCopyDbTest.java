package com.bipros.api.integration;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.risk.application.dto.CreateRiskTemplateRequest;
import com.bipros.risk.application.dto.RiskAnalysisQuality;
import com.bipros.risk.application.dto.RiskSummary;
import com.bipros.risk.application.dto.UpdateRiskTemplateRequest;
import com.bipros.risk.application.service.RiskService;
import com.bipros.risk.application.service.RiskTemplateService;
import com.bipros.risk.domain.model.Industry;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskCategory;
import com.bipros.risk.domain.model.RiskResponse;
import com.bipros.risk.domain.model.RiskResponseType;
import com.bipros.risk.domain.model.RiskStatus;
import com.bipros.risk.domain.model.RiskTemplate;
import com.bipros.risk.domain.repository.RiskRepository;
import com.bipros.risk.domain.repository.RiskResponseRepository;
import com.bipros.risk.domain.repository.RiskTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DB-backed integration tests for the Risk Library: bulk-copy from templates into a project's
 * risk register and the analysis-quality computation across the four states. Mirrors the
 * pattern of {@link ActivityPredecessorValidationDbTest}; runs against the local dev
 * PostgreSQL container ({@code docker compose up -d}).
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@Rollback
@DisplayName("Risk Library copy + analysis quality — real DB")
class RiskTemplateCopyDbTest {

    @Autowired private RiskTemplateService riskTemplateService;
    @Autowired private RiskTemplateRepository templateRepository;
    @Autowired private RiskService riskService;
    @Autowired private RiskRepository riskRepository;
    @Autowired private RiskResponseRepository responseRepository;

    private UUID projectId;
    private RiskTemplate seededTemplate;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        // Use one of the seeded ROAD templates so we don't fight unique-code constraints.
        seededTemplate = templateRepository.findByCode("ROAD-LAND-001")
            .orElseThrow(() -> new IllegalStateException(
                "RiskTemplateSeeder didn't run — start the dev backend at least once first"));
    }

    @Test
    @DisplayName("copyToProject creates one Risk per template, IDENTIFIED with no owner, RAG/score populated")
    void copyCreatesRisksWithDefaults() {
        RiskTemplate other = templateRepository.findByCode("ROAD-MONSOON-004").orElseThrow();

        List<RiskSummary> created = riskTemplateService.copyToProject(
            projectId, List.of(seededTemplate.getId(), other.getId()));

        assertThat(created).hasSize(2);
        for (RiskSummary s : created) {
            assertThat(s.getStatus()).isEqualTo(RiskStatus.IDENTIFIED);
            assertThat(s.getOwnerId()).isNull();
            assertThat(s.getRiskScore()).isNotNull().isGreaterThan(0.0);
            assertThat(s.getRag()).isNotNull();
        }

        // Codes are sequential
        assertThat(created.get(0).getCode()).isEqualTo("RISK-0001");
        assertThat(created.get(1).getCode()).isEqualTo("RISK-0002");

        // Risks landed in the project's register
        List<Risk> persisted = riskRepository.findByProjectId(projectId);
        assertThat(persisted).hasSize(2);
    }

    @Test
    @DisplayName("system-default templates reject code/industry mutations")
    void systemDefaultProtection() {
        UpdateRiskTemplateRequest changeCode = new UpdateRiskTemplateRequest(
            "RENAMED", seededTemplate.getTitle(), seededTemplate.getDescription(),
            seededTemplate.getIndustry(), Set.of(), seededTemplate.getCategory(),
            seededTemplate.getDefaultProbability(),
            seededTemplate.getDefaultImpactCost(),
            seededTemplate.getDefaultImpactSchedule(),
            seededTemplate.getMitigationGuidance(),
            seededTemplate.getIsOpportunity(),
            seededTemplate.getSortOrder(),
            seededTemplate.getActive());

        BusinessRuleException codeEx = assertThrows(BusinessRuleException.class,
            () -> riskTemplateService.update(seededTemplate.getId(), changeCode));
        assertThat(codeEx.getRuleCode()).isEqualTo("SYSTEM_DEFAULT_IMMUTABLE");

        BusinessRuleException deleteEx = assertThrows(BusinessRuleException.class,
            () -> riskTemplateService.delete(seededTemplate.getId()));
        assertThat(deleteEx.getRuleCode()).isEqualTo("SYSTEM_DEFAULT_PROTECTED");
    }

    @Test
    @DisplayName("custom templates can be created, edited, deleted")
    void customTemplateCrud() {
        UUID id = riskTemplateService.create(new CreateRiskTemplateRequest(
            "TEST-CUSTOM-" + UUID.randomUUID().toString().substring(0, 8),
            "Custom integration test risk",
            "An integration test created this template",
            Industry.GENERIC,
            Set.of(),
            RiskCategory.PROJECT_MANAGEMENT,
            2, 2, 2,
            null, false, 999, true)).id();

        // Editable
        riskTemplateService.update(id, new UpdateRiskTemplateRequest(
            null, "Custom integration test risk (edited)", null,
            null, null, null, 3, 3, 3, null, null, 999, true));

        // Deletable
        riskTemplateService.delete(id);
        assertThat(templateRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("analysis-quality progresses through assignment of owner and response")
    void analysisQualityProgresses() {
        // Step 1: copy from a seeded template — rating + description (seed text is ≥50 chars) met,
        // owner + response missing → 2 of 4 → PARTIALLY_ANALYSED.
        List<RiskSummary> copied = riskTemplateService.copyToProject(
            projectId, List.of(seededTemplate.getId()));
        UUID riskId = copied.get(0).getId();

        RiskAnalysisQuality q1 = riskService.assessQuality(projectId, riskId);
        assertThat(q1.score()).isEqualTo(2);
        assertThat(q1.level()).isEqualTo(RiskAnalysisQuality.QualityLevel.PARTIALLY_ANALYSED);
        assertThat(q1.criteria())
            .containsEntry("hasRating", true)
            .containsEntry("hasDescription", true)
            .containsEntry("hasOwner", false)
            .containsEntry("hasResponse", false);

        // Step 2: assign owner → 3 of 4 → still PARTIALLY_ANALYSED
        Risk r = riskRepository.findById(riskId).orElseThrow();
        r.setOwnerId(UUID.randomUUID());
        riskRepository.save(r);

        RiskAnalysisQuality q2 = riskService.assessQuality(projectId, riskId);
        assertThat(q2.score()).isEqualTo(3);
        assertThat(q2.level()).isEqualTo(RiskAnalysisQuality.QualityLevel.PARTIALLY_ANALYSED);

        // Step 3: add a complete response → 4 of 4 → WELL_ANALYSED
        RiskResponse resp = new RiskResponse();
        resp.setRiskId(riskId);
        resp.setResponseType(RiskResponseType.MITIGATE);
        resp.setResponsibleId(UUID.randomUUID());
        resp.setDescription("Mitigation plan");
        responseRepository.save(resp);

        RiskAnalysisQuality q3 = riskService.assessQuality(projectId, riskId);
        assertThat(q3.score()).isEqualTo(4);
        assertThat(q3.level()).isEqualTo(RiskAnalysisQuality.QualityLevel.WELL_ANALYSED);
    }

    @Test
    @DisplayName("blank-description copy stays NOT_ANALYSED (only rating met)")
    void blankDescriptionStaysNotAnalysed() {
        // Custom template with no description — only the rating criterion will be met after copy.
        UUID id = riskTemplateService.create(new CreateRiskTemplateRequest(
            "TEST-BLANK-" + UUID.randomUUID().toString().substring(0, 8),
            "Blank-description template",
            null, // <-- no description
            Industry.GENERIC, Set.of(),
            RiskCategory.PROJECT_MANAGEMENT,
            3, 3, 3, null, false, 999, true)).id();

        List<RiskSummary> copied = riskTemplateService.copyToProject(projectId, List.of(id));
        UUID riskId = copied.get(0).getId();

        RiskAnalysisQuality q = riskService.assessQuality(projectId, riskId);
        assertThat(q.score()).isEqualTo(1);
        assertThat(q.level()).isEqualTo(RiskAnalysisQuality.QualityLevel.NOT_ANALYSED);
        assertThat(q.criteria())
            .containsEntry("hasRating", true)
            .containsEntry("hasDescription", false)
            .containsEntry("hasOwner", false)
            .containsEntry("hasResponse", false);
    }

    @Test
    @DisplayName("listRisks attaches analysisQuality to every summary")
    void listRisksAttachesQuality() {
        riskTemplateService.copyToProject(projectId,
            List.of(seededTemplate.getId(),
                templateRepository.findByCode("ROAD-MONSOON-004").orElseThrow().getId()));

        List<RiskSummary> rows = riskService.listRisks(projectId, null);

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(s -> {
            // Every row must carry an analysisQuality assessment after the list path.
            assertThat(s.getAnalysisQuality()).isNotNull();
            // Freshly-copied rows have no owner and no response, so they cannot be WELL_ANALYSED.
            assertThat(s.getAnalysisQuality().level())
                .isNotEqualTo(RiskAnalysisQuality.QualityLevel.WELL_ANALYSED);
            assertThat(s.getAnalysisQuality().criteria())
                .containsEntry("hasOwner", false)
                .containsEntry("hasResponse", false);
        });
    }

    @Test
    @DisplayName("library list pre-filters by industry + project category")
    void listFiltersByIndustryAndCategory() {
        // ROAD industry + HIGHWAY category should match the seeded ROAD-* set
        var matching = riskTemplateService.list(Industry.ROAD, "HIGHWAY", true);
        assertThat(matching).extracting("code")
            .contains("ROAD-LAND-001", "ROAD-MONSOON-004");

        // OIL_GAS industry should NOT include ROAD templates
        var oilGas = riskTemplateService.list(Industry.OIL_GAS, null, true);
        assertThat(oilGas).extracting("code").noneSatisfy(c ->
            assertThat((String) c).startsWith("ROAD-"));
    }

    @Test
    @DisplayName("seeder ran (smoke check that key codes exist)")
    void seederRan() {
        assertThat(templateRepository.findByCode("ROAD-LAND-001")).isPresent();
        assertThat(templateRepository.findByCode("OG-HOTWORK-007")).isPresent();
        assertThat(templateRepository.findByCode("GEN-FORCE-004")).isPresent();
        // Confirm system-default flag set on seeded rows
        Optional<RiskTemplate> seed = templateRepository.findByCode("ROAD-LAND-001");
        assertThat(seed).isPresent();
        assertThat(seed.get().getSystemDefault()).isTrue();
    }
}
